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
import java.time.LocalDate
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess
import kotlin.time.Duration

@Configuration
class CreateNetflixDatabaseJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSourceProperties: DataSourceProperties,
    private val databaseExportService: DatabaseExportService,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val moviesMap = mutableMapOf<Pair<String, Long>, MutableSet<ReportSheetRow>>()
    private val seasonsMap = mutableMapOf<Pair<String, Long>, MutableSet<ReportSheetRow>>()
    private val entityClasses =
        listOf(
            Movie::class.java,
            TvShow::class.java,
            Season::class.java,
            ViewSummary::class.java,
        )

    // Set end date as the previous Sunday.
    private val viewSummaryEndDate = LocalDate.now().let { it.minusDays(it.dayOfWeek.value.toLong()) }

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
            JobBuilder(getFunctionName(), jobRepository)
                .incrementer(RunIdIncrementer())
                .start(setupStep)

        if (hibernateProperties.ddlAuto == "create") {
            jobBuilder.next(importEngagementReport20240101Step)
                .next(importEngagementReport20230701Step)
                .next(importTop10ListStep)
                .next(populateMovieTableStep)
                .next(populateSeasonTableStep)
                .next(verifyContentStep)
        }

        if (dataSourceProperties.name != "sqlite") {
            jobBuilder.next(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .next(fileCompressionStep)
        }
        return jobBuilder
            .listener(
                object : JobExecutionListener {
                    override fun afterJob(jobExecution: JobExecution) {
                        if (jobExecution.stepExecutions.any { it.exitStatus.exitCode == ExitStatus.FAILED.exitCode }) {
                            jobExecution.status = BatchStatus.FAILED
                            exitProcess(1)
                        }
                    }
                },
            )
            .build()
    }

    @Bean
    fun setupStep(): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ _, _ ->
                File(dataSourceProperties.getFilename()).parentFile.mkdirs()
                RepeatStatus.FINISHED
            }, transactionManager).build()

    @Bean
    fun importEngagementReport20240101Step(): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<ReportSheetRow, ReportSheetRow>(100, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader(EngagementReport.ENGAGEMENT_REPORT_2024_01_01))
            .processor { item -> item }
            .writer { chunk -> writeReportSheetRow(chunk) }
            .faultTolerant()
            .build()

    @Bean
    fun importEngagementReport20230701Step(): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<ReportSheetRow, ReportSheetRow>(100, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader(EngagementReport.ENGAGEMENT_REPORT_2023_07_01))
            .processor { item -> item }
            .writer { chunk -> writeReportSheetRow(chunk) }
            .faultTolerant()
            .build()

    @Bean
    fun importTop10ListStep(
        top10ListReader: PoiItemReader<ReportSheetRow>,
        movieRepository: MovieRepository,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<ReportSheetRow, ReportSheetRow>(100, transactionManager)
            .allowStartIfComplete(true)
            .reader(top10ListReader)
            .processor { item -> item }
            .writer { chunk -> writeReportSheetRow(chunk) }
            .faultTolerant()
            .build()

    @Bean
    fun populateMovieTableStep(
        movieProcessor: ItemProcessor<Set<ReportSheetRow>, Movie>,
        movieRepository: MovieRepository,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<Set<ReportSheetRow>, Movie>(50, transactionManager)
            .allowStartIfComplete(true)
            .reader { if (moviesMap.isNotEmpty()) moviesMap.remove(moviesMap.keys.first()) else null }
            .processor(movieProcessor)
            .writer { chunk -> movieRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    @Bean
    fun populateSeasonTableStep(
        seasonProcessor: ItemProcessor<Set<ReportSheetRow>, Season>,
        seasonRepository: SeasonRepository,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<Set<ReportSheetRow>, Season>(50, transactionManager)
            .allowStartIfComplete(true)
            .reader { if (seasonsMap.isNotEmpty()) seasonsMap.remove(seasonsMap.keys.first()) else null }
            .processor(seasonProcessor)
            .writer { chunk -> seasonRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    @Bean
    fun verifyContentStep(viewSummaryRepository: ViewSummaryRepository): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ contribution, _ ->
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
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ contribution, _ ->
                databaseExportService.exportSchema(
                    title = "Netflix database",
                    databaseName = dataSourceProperties.name,
                    filename = dataSourceProperties.getFilename(),
                    entityClasses,
                )
                logger.info("${contribution.stepExecution.stepName}: database has been exported")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    fun <T : AbstractEntity> exportDataStep(
        name: String,
        repository: JpaRepository<T, Long>,
    ): Step =
        StepBuilder("${name}ExportStep", jobRepository)
            .chunk<T, T>(100, transactionManager)
            .allowStartIfComplete(true)
            .reader(
                RepositoryItemReaderBuilder<T>()
                    .name("${name}ExportReader")
                    .repository(repository)
                    .methodName("findAll")
                    .sorts(mapOf("id" to Sort.Direction.ASC))
                    .pageSize(100)
                    .build(),
            )
            .processor { entity -> entity }
            .writer { chunk ->
                File(dataSourceProperties.getFilename())
                    .appendText(databaseExportService.getInsertStatement(dataSourceProperties.name, chunk.items))
            }
            .faultTolerant()
            .build()

    @Bean
    fun fileCompressionStep(dataSourceProperties: DataSourceProperties): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ contribution, _ ->
                val sqlFilename = dataSourceProperties.getFilename()
                val zipFilename = dataSourceProperties.getFilename("zip")
                FileOutputStream(zipFilename).use { fileOutputStream ->
                    ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                        val sqlFile = File(sqlFilename)
                        val fileInputStream = FileInputStream(sqlFile)
                        val zipEntry = ZipEntry(sqlFile.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        zipOutputStream.write(fileInputStream.readAllBytes())
                    }
                }
                logger.info("${contribution.stepExecution.stepName}: $sqlFilename -> $zipFilename")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()

    fun engagementReportReader(engagementReport: EngagementReport) =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource(engagementReport.path))
            setRowMapper { rowSet ->
                val titles = rowSet.getString("Title")?.split("//") ?: emptyList()
                ReportSheetRow().apply {
                    startDate = engagementReport.startDate
                    endDate = engagementReport.endDate
                    duration = engagementReport.duration
                    runtime = rowSet.getRuntimeInMinutes("Runtime")
                    title = titles.firstOrNull()?.trim()
                    originalTitle = if (rowSet.getString("Title")?.contains("//") == true) titles.lastOrNull()?.trim() else null
                    category = rowSet.metaData.sheetName?.toCategory()
                    availableGlobally = rowSet.getString("Available Globally?") == "Yes"
                    releaseDate = rowSet.getString("Release Date")?.let { if (it.isNotBlank()) LocalDate.parse(it) else null }
                    hoursViewed = rowSet.getInt("Hours Viewed")
                    views = rowSet.getInt("Views")
                }
            }
        }

    @Bean
    fun top10ListReader() =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/all-weeks-global.xlsx"))
            setRowMapper { rowSet ->
                ReportSheetRow().apply {
                    startDate = rowSet.getString("week")?.let { LocalDate.parse(it).minusDays(6) }
                    endDate = rowSet.getString("week")?.let { LocalDate.parse(it) }
                    duration = SummaryDuration.WEEKLY
                    runtime = rowSet.getRuntimeInMinutes("runtime")
                    title = rowSet.getString("show_title")?.trim()
                    category = rowSet.getString("category")?.toCategory()
                    locale = if (rowSet.getString("category")?.contains("(English)") == true) Locale.ENGLISH else null
                    availableGlobally = null
                    releaseDate = null
                    hoursViewed = rowSet.getInt("weekly_hours_viewed")
                    views = rowSet.getInt("weekly_views")
                    viewRank = rowSet.getInt("weekly_rank")
                    cumulativeWeeksInTop10 = rowSet.getInt("cumulative_weeks_in_top_10")
                }
            }
        }

    @Bean
    fun movieProcessor(movieRepository: MovieRepository): ItemProcessor<Set<ReportSheetRow>, Movie> =
        ItemProcessor<Set<ReportSheetRow>, Movie> { reportSheetRows ->
            reportSheetRows.fold(reportSheetRows.first().toMovie()) { movie, reportSheetRow ->
                reportSheetRow.originalTitle?.let { movie.originalTitle = it }
                reportSheetRow.runtime?.let { movie.runtime = it }
                reportSheetRow.releaseDate?.let { movie.releaseDate = it }
                reportSheetRow.availableGlobally?.let { movie.availableGlobally = it }
                reportSheetRow.locale?.let { movie.locale = it }
                movie.updateViewSummary(reportSheetRow)
                movie
            }
        }

    @Bean
    fun seasonProcessor(
        seasonRepository: SeasonRepository,
        tvShowRepository: TvShowRepository,
    ): ItemProcessor<Set<ReportSheetRow>, Season> =
        ItemProcessor<Set<ReportSheetRow>, Season> { reportSheetRows ->
            val season =
                reportSheetRows.fold(reportSheetRows.first().toSeason()) { season, reportSheetRow ->
                    val updatedSeason = reportSheetRow.toSeason()
                    updatedSeason.seasonNumber?.let { season.seasonNumber = it }
                    updatedSeason.originalTitle?.let { season.originalTitle = it }
                    updatedSeason.runtime?.let { season.runtime = it }
                    updatedSeason.releaseDate?.let { season.releaseDate = it }
                    season.updateViewSummary(reportSheetRow)

                    val updatedTvShow = reportSheetRow.toTvShow()
                    season.tvShow = (season.tvShow ?: tvShowRepository.findByTitle(updatedTvShow.title!!))?.let { tvShow ->
                        updatedTvShow.originalTitle?.let { tvShow.originalTitle = it }
                        updatedTvShow.releaseDate?.let { tvShow.releaseDate = it }
                        updatedTvShow.availableGlobally?.let { tvShow.availableGlobally = it }
                        updatedTvShow.locale?.let { tvShow.locale = it }
                        tvShow
                    } ?: updatedTvShow
                    season
                }
            season.tvShow = tvShowRepository.save(season.tvShow!!)
            season
        }

    private fun writeReportSheetRow(chunk: Chunk<out ReportSheetRow>) {
        chunk.items.filter { it.title is String && it.runtime is Long }.forEach { item ->
            val key = Pair(item.title!!, item.runtime!!)
            when (item.category) {
                StreamingCategory.MOVIE -> moviesMap.getOrPut(key) { mutableSetOf() }.add(item)
                StreamingCategory.TV_SHOW -> seasonsMap.getOrPut(key) { mutableSetOf() }.add(item)
                else -> {}
            }
        }
    }

    private fun RowSet.getString(key: String): String? = this.properties.getProperty(key)

    private fun RowSet.getInt(key: String): Int? =
        this.getString(key)?.replace(",", "")?.let {
            if (it.isNotBlank()) it.toInt() else null
        }

    private fun RowSet.getRuntimeInMinutes(key: String): Long? =
        this.getString(key)?.let {
            return when {
                it.contains(":") -> Duration.parseOrNull(it.replace(":", "h") + "m")?.inWholeMinutes
                else -> it.toBigDecimalOrNull()?.multiply(60.toBigDecimal())?.toLong()
            }
        }

    private fun getFunctionName(): String {
        return Exception().stackTrace[1].methodName
    }

    private fun DataSourceProperties.getFilename(extension: String = "sql"): String = "build/artifacts/netflixdb-${this.name}.$extension"
}
