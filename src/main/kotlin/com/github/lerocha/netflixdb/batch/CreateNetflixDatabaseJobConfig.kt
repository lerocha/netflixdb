package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.EngagementReport
import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toCategory
import com.github.lerocha.netflixdb.dto.toMovie
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toTvShow
import com.github.lerocha.netflixdb.dto.updateViewSummary
import com.github.lerocha.netflixdb.entity.AbstractEntity
import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.SummaryDuration
import com.github.lerocha.netflixdb.entity.TvShow
import com.github.lerocha.netflixdb.entity.ViewSummary
import com.github.lerocha.netflixdb.repository.MovieRepository
import com.github.lerocha.netflixdb.repository.SeasonRepository
import com.github.lerocha.netflixdb.repository.TvShowRepository
import com.github.lerocha.netflixdb.repository.ViewSummaryRepository
import com.github.lerocha.netflixdb.service.DatabaseExportService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.JobExecutionListener
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.extensions.excel.poi.PoiItemReader
import org.springframework.batch.extensions.excel.support.rowset.RowSet
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.PlatformTransactionManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess
import kotlin.time.Duration

/**
 * Spring Batch pipeline that ingests Netflix Excel reports, materializes JPA entities,
 * verifies weekly view data, and optionally exports portable SQL artifacts per database profile.
 */
@Configuration
class CreateNetflixDatabaseJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSourceProperties: DataSourceProperties,
    private val databaseExportService: DatabaseExportService,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    /** Rows keyed by (title, runtime) until populate steps drain these maps. */
    private val movieRowsByTitleAndRuntime = mutableMapOf<Pair<String, Long>, MutableSet<ReportSheetRow>>()
    private val seasonRowsByTitleAndRuntime = mutableMapOf<Pair<String, Long>, MutableSet<ReportSheetRow>>()

    private val exportEntityClasses =
        listOf(
            Movie::class.java,
            TvShow::class.java,
            Season::class.java,
            ViewSummary::class.java,
        )

    @Bean
    fun createNetflixDatabaseJob(
        hibernateProperties: HibernateProperties,
        setupStep: Step,
        importEngagementReport20240101Step: Step,
        importEngagementReport20230701Step: Step,
        importTop10ListStep: Step,
        populateMovieTableStep: Step,
        populateSeasonTableStep: Step,
        verifyContentStep: Step,
        exportDatabaseSchemaStep: Step,
        fileCompressionStep: Step,
        movieRepository: MovieRepository,
        tvShowRepository: TvShowRepository,
        seasonRepository: SeasonRepository,
        viewSummaryRepository: ViewSummaryRepository,
    ): Job {
        val jobBuilder =
            JobBuilder(callerBeanMethodName(), jobRepository)
                .incrementer(RunIdIncrementer())
                .start(setupStep)

        if (hibernateProperties.ddlAuto == "create") {
            jobBuilder
                .next(importEngagementReport20240101Step)
                .next(importEngagementReport20230701Step)
                .next(importTop10ListStep)
                .next(populateMovieTableStep)
                .next(populateSeasonTableStep)
                .next(verifyContentStep)
        }

        if (dataSourceProperties.name != "sqlite") {
            jobBuilder
                .next(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .next(fileCompressionStep)
        }

        return jobBuilder
            .listener(failJobOnStepFailureListener())
            .build()
    }

    @Bean
    fun setupStep(): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .tasklet({ _, _ ->
                File(dataSourceProperties.artifactFilename()).parentFile.mkdirs()
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    @Bean
    fun importEngagementReport20240101Step(): Step =
        buildEngagementReportImportStep(
            stepName = "importEngagementReport20240101Step",
            engagementReport = EngagementReport.ENGAGEMENT_REPORT_2024_01_01,
        )

    @Bean
    fun importEngagementReport20230701Step(): Step =
        buildEngagementReportImportStep(
            stepName = "importEngagementReport20230701Step",
            engagementReport = EngagementReport.ENGAGEMENT_REPORT_2023_07_01,
        )

    /** Shared chunk step for semi-annual engagement workbooks (one header row skipped). */
    private fun buildEngagementReportImportStep(
        stepName: String,
        engagementReport: EngagementReport,
    ): Step =
        StepBuilder(stepName, jobRepository)
            .chunk<ReportSheetRow, ReportSheetRow>(ENGAGEMENT_IMPORT_CHUNK_SIZE, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader(engagementReport))
            .processor { item -> item }
            .writer { chunk -> accumulateReportRows(chunk) }
            .faultTolerant()
            .build()

    @Bean
    fun importTop10ListStep(top10ListReader: PoiItemReader<ReportSheetRow>): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .chunk<ReportSheetRow, ReportSheetRow>(ENGAGEMENT_IMPORT_CHUNK_SIZE, transactionManager)
            .allowStartIfComplete(true)
            .reader(top10ListReader)
            .processor { item -> item }
            .writer { chunk -> accumulateReportRows(chunk) }
            .faultTolerant()
            .build()

    @Bean
    fun populateMovieTableStep(
        movieProcessor: ItemProcessor<Set<ReportSheetRow>, Movie>,
        movieRepository: MovieRepository,
    ): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .chunk<Set<ReportSheetRow>, Movie>(ENTITY_POPULATE_CHUNK_SIZE, transactionManager)
            .allowStartIfComplete(true)
            .reader { pollNextRowSet(movieRowsByTitleAndRuntime) }
            .processor(movieProcessor)
            .writer { chunk -> movieRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    @Bean
    fun populateSeasonTableStep(
        seasonProcessor: ItemProcessor<Set<ReportSheetRow>, Season>,
        seasonRepository: SeasonRepository,
    ): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .chunk<Set<ReportSheetRow>, Season>(ENTITY_POPULATE_CHUNK_SIZE, transactionManager)
            .allowStartIfComplete(true)
            .reader { pollNextRowSet(seasonRowsByTitleAndRuntime) }
            .processor(seasonProcessor)
            .writer { chunk -> seasonRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    /**
     * Fails the job when no [ViewSummary] exists for the most recent Sunday end date,
     * proving that weekly top-10 data was loaded.
     */
    @Bean
    fun verifyContentStep(viewSummaryRepository: ViewSummaryRepository): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .tasklet({ contribution, _ ->
                val viewSummaryEndDate = LocalDate.now().with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
                val results = viewSummaryRepository.findAllByEndDate(viewSummaryEndDate)
                if (results.isEmpty()) {
                    contribution.exitStatus = ExitStatus.FAILED
                    throw IllegalStateException("No view summary found for end date $viewSummaryEndDate")
                }
                logger.info("${contribution.stepExecution.stepName}: database has been verified - endDate=$viewSummaryEndDate")
                RepeatStatus.FINISHED
            }, transactionManager)
            .allowStartIfComplete(false)
            .startLimit(1)
            .build()

    @Bean
    fun exportDatabaseSchemaStep(
        dataSourceProperties: DataSourceProperties,
        databaseExportService: DatabaseExportService,
    ): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .tasklet({ contribution, _ ->
                databaseExportService.exportSchema(
                    title = "Netflix database",
                    databaseName = dataSourceProperties.name,
                    filename = dataSourceProperties.artifactFilename(),
                    exportEntityClasses,
                )
                logger.info("${contribution.stepExecution.stepName}: database has been exported")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    /** Paginated repository read; appends INSERT statements to the artifact SQL file. */
    fun <T : AbstractEntity> exportDataStep(
        name: String,
        repository: JpaRepository<T, Long>,
    ): Step =
        StepBuilder("${name}ExportStep", jobRepository)
            .chunk<T, T>(DATA_EXPORT_CHUNK_SIZE, transactionManager)
            .allowStartIfComplete(true)
            .reader(
                RepositoryItemReaderBuilder<T>()
                    .name("${name}ExportReader")
                    .repository(repository)
                    .methodName("findAll")
                    .sorts(mapOf("id" to Sort.Direction.ASC))
                    .pageSize(DATA_EXPORT_CHUNK_SIZE)
                    .build(),
            )
            .processor { entity -> entity }
            .writer { chunk ->
                File(dataSourceProperties.artifactFilename())
                    .appendText(databaseExportService.getInsertStatement(dataSourceProperties.name, chunk.items))
            }
            .faultTolerant()
            .build()

    @Bean
    fun fileCompressionStep(dataSourceProperties: DataSourceProperties): Step =
        StepBuilder(callerBeanMethodName(), jobRepository)
            .tasklet({ contribution, _ ->
                val sqlFilename = dataSourceProperties.artifactFilename()
                val zipFilename = dataSourceProperties.artifactFilename("zip")
                FileOutputStream(zipFilename).use { fileOutputStream ->
                    ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                        val sqlFile = File(sqlFilename)
                        FileInputStream(sqlFile).use { fileInputStream ->
                            zipOutputStream.putNextEntry(ZipEntry(sqlFile.name))
                            zipOutputStream.write(fileInputStream.readAllBytes())
                        }
                    }
                }
                logger.info("${contribution.stepExecution.stepName}: $sqlFilename -> $zipFilename")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    private fun engagementReportReader(engagementReport: EngagementReport) =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource(engagementReport.path))
            setRowMapper { rowSet -> mapEngagementReportRow(rowSet, engagementReport) }
        }

    /** Engagement sheets encode localized titles as "English // Original". */
    private fun mapEngagementReportRow(rowSet: RowSet, engagementReport: EngagementReport): ReportSheetRow {
        val (title, originalTitle) = rowSet.parseEngagementTitles()
        return ReportSheetRow().apply {
            startDate = engagementReport.startDate
            endDate = engagementReport.endDate
            duration = engagementReport.duration
            runtime = rowSet.runtimeInMinutes("Runtime")
            this.title = title
            this.originalTitle = originalTitle
            category = rowSet.metaData.sheetName?.toCategory()
            availableGlobally = rowSet.getString("Available Globally?") == "Yes"
            releaseDate = rowSet.parseLocalDate("Release Date")
            hoursViewed = rowSet.getInt("Hours Viewed")
            views = rowSet.getInt("Views")
        }
    }

    @Bean
    fun top10ListReader() =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/all-weeks-global.xlsx"))
            setRowMapper { rowSet -> mapTop10ListRow(rowSet) }
        }

    /** Weekly global top-10: `week` column is the Sunday that ends the reporting period. */
    private fun mapTop10ListRow(rowSet: RowSet): ReportSheetRow {
        val weekEndingSunday = rowSet.getString("week")?.let { LocalDate.parse(it) }
        val categoryLabel = rowSet.getString("category")
        return ReportSheetRow().apply {
            startDate = weekEndingSunday?.minusDays(6)
            endDate = weekEndingSunday
            duration = SummaryDuration.WEEKLY
            runtime = rowSet.runtimeInMinutes("runtime")
            title = rowSet.getString("show_title")?.trim()
            category = categoryLabel?.toCategory()
            locale = if (categoryLabel?.contains("(English)") == true) Locale.ENGLISH else null
            availableGlobally = null
            releaseDate = null
            hoursViewed = rowSet.getInt("weekly_hours_viewed")
            views = rowSet.getInt("weekly_views")
            viewRank = rowSet.getInt("weekly_rank")
            cumulativeWeeksInTop10 = rowSet.getInt("cumulative_weeks_in_top_10")
        }
    }

    /** Merges all rows for the same title/runtime into one [Movie], accumulating [ViewSummary] records. */
    @Bean
    fun movieProcessor(movieRepository: MovieRepository): ItemProcessor<Set<ReportSheetRow>, Movie> =
        ItemProcessor { reportSheetRows ->
            reportSheetRows.fold(reportSheetRows.first().toMovie()) { movie, row ->
                mergeMovieFields(movie, row)
                movie.updateViewSummary(row)
                movie
            }
        }

    @Bean
    fun seasonProcessor(
        seasonRepository: SeasonRepository,
        tvShowRepository: TvShowRepository,
    ): ItemProcessor<Set<ReportSheetRow>, Season> =
        ItemProcessor { reportSheetRows ->
            val season =
                reportSheetRows.fold(reportSheetRows.first().toSeason()) { season, row ->
                    mergeSeasonFields(season, row)
                    season.updateViewSummary(row)
                    season.tvShow = resolveOrCreateTvShow(season.tvShow, row, tvShowRepository)
                    season
                }
            season.tvShow = tvShowRepository.save(season.tvShow!!)
            season
        }

    /** Routes parsed rows into in-memory maps; only rows with title and runtime are kept. */
    private fun accumulateReportRows(chunk: Chunk<out ReportSheetRow>) {
        chunk.items
            .filter { it.title != null && it.runtime != null }
            .forEach { row ->
                val key = Pair(row.title!!, row.runtime!!)
                when (row.category) {
                    StreamingCategory.MOVIE -> movieRowsByTitleAndRuntime.getOrPut(key) { mutableSetOf() }.add(row)
                    StreamingCategory.TV_SHOW -> seasonRowsByTitleAndRuntime.getOrPut(key) { mutableSetOf() }.add(row)
                    else -> Unit
                }
            }
    }

    private fun mergeMovieFields(movie: Movie, row: ReportSheetRow) {
        row.originalTitle?.let { movie.originalTitle = it }
        row.runtime?.let { movie.runtime = it }
        row.releaseDate?.let { movie.releaseDate = it }
        row.availableGlobally?.let { movie.availableGlobally = it }
        row.locale?.let { movie.locale = it }
    }

    private fun mergeSeasonFields(season: Season, row: ReportSheetRow) {
        val parsedSeason = row.toSeason()
        parsedSeason.seasonNumber?.let { season.seasonNumber = it }
        parsedSeason.originalTitle?.let { season.originalTitle = it }
        parsedSeason.runtime?.let { season.runtime = it }
        parsedSeason.releaseDate?.let { season.releaseDate = it }
    }

    private fun resolveOrCreateTvShow(
        existingTvShow: TvShow?,
        row: ReportSheetRow,
        tvShowRepository: TvShowRepository,
    ): TvShow {
        val parsedTvShow = row.toTvShow()
        val tvShow = existingTvShow ?: tvShowRepository.findByTitle(parsedTvShow.title!!)
        return if (tvShow != null) {
            parsedTvShow.originalTitle?.let { tvShow.originalTitle = it }
            parsedTvShow.releaseDate?.let { tvShow.releaseDate = it }
            parsedTvShow.availableGlobally?.let { tvShow.availableGlobally = it }
            parsedTvShow.locale?.let { tvShow.locale = it }
            tvShow
        } else {
            parsedTvShow
        }
    }

    private fun pollNextRowSet(map: MutableMap<Pair<String, Long>, MutableSet<ReportSheetRow>>): Set<ReportSheetRow>? =
        if (map.isEmpty()) null else map.remove(map.keys.first())

    private fun failJobOnStepFailureListener(): JobExecutionListener =
        object : JobExecutionListener {
            override fun afterJob(jobExecution: JobExecution) {
                if (jobExecution.stepExecutions.any { it.exitStatus.exitCode == ExitStatus.FAILED.exitCode }) {
                    jobExecution.status = BatchStatus.FAILED
                    exitProcess(1)
                }
            }
        }

    private fun RowSet.parseEngagementTitles(): Pair<String?, String?> {
        val rawTitle = getString("Title") ?: return null to null
        val parts = rawTitle.split("//")
        val primary = parts.firstOrNull()?.trim()
        val alternate = if (rawTitle.contains("//")) parts.lastOrNull()?.trim() else null
        return primary to alternate
    }

    private fun RowSet.getString(key: String): String? = properties.getProperty(key)

    private fun RowSet.getInt(key: String): Int? =
        getString(key)?.replace(",", "")?.let { if (it.isNotBlank()) it.toInt() else null }

    private fun RowSet.parseLocalDate(key: String): LocalDate? =
        getString(key)?.let { if (it.isNotBlank()) LocalDate.parse(it) else null }

    /** Accepts `H:MM` duration strings or decimal hours (converted to minutes). */
    private fun RowSet.runtimeInMinutes(key: String): Long? =
        getString(key)?.let { raw ->
            when {
                raw.contains(":") -> Duration.parseOrNull(raw.replace(":", "h") + "m")?.inWholeMinutes
                else -> raw.toBigDecimalOrNull()?.multiply(60.toBigDecimal())?.toLong()
            }
        }

    /**
     * Step and job names must match the enclosing @Bean method name.
     * Stack frame [1] is the @Bean factory method that invoked this helper.
     */
    private fun callerBeanMethodName(): String = Exception().stackTrace[1].methodName

    private fun DataSourceProperties.artifactFilename(extension: String = "sql"): String =
        "build/artifacts/netflixdb-${name}.$extension"

    private companion object {
        const val ENGAGEMENT_IMPORT_CHUNK_SIZE = 100
        const val ENTITY_POPULATE_CHUNK_SIZE = 50
        const val DATA_EXPORT_CHUNK_SIZE = 100
    }
}
