package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.EngagementReport
import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toCategory
import com.github.lerocha.netflixdb.dto.toMovie
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toTvShow
import com.github.lerocha.netflixdb.dto.toTvShowTitle
import com.github.lerocha.netflixdb.dto.updateViewSummary
import com.github.lerocha.netflixdb.entity.AbstractEntity
import com.github.lerocha.netflixdb.entity.Episode
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
import org.springframework.batch.core.Job
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
import java.util.LinkedList
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Duration

@Configuration
class CreateNetflixDatabaseJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSourceProperties: DataSourceProperties,
    private val databaseExportService: DatabaseExportService,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    private val entityClasses =
        listOf(
            Movie::class.java,
            TvShow::class.java,
            Season::class.java,
            Episode::class.java,
            ViewSummary::class.java,
        )

    private val movies = LinkedList<ReportSheetRow>()
    private val seasons = LinkedList<ReportSheetRow>()

    companion object {
        const val ARTIFACTS_DIRECTORY = "build/artifacts"
    }

    @Bean
    fun createNetflixDatabaseJob(
        hibernateProperties: HibernateProperties,
        importEngagementReport20240101Step: Step,
        importEngagementReport20230701Step: Step,
        importTop10ListStep: Step,
        importMoviesStep: Step,
        importSeasonsStep: Step,
        exportDatabaseSchemaStep: Step,
        fileCompressionStep: Step,
        movieRepository: MovieRepository,
        tvShowRepository: TvShowRepository,
        seasonRepository: SeasonRepository,
        viewSummaryRepository: ViewSummaryRepository,
    ): Job =
        if (hibernateProperties.ddlAuto == "create") {
            JobBuilder(getFunctionName(), jobRepository)
                .incrementer(RunIdIncrementer())
                .start(importEngagementReport20240101Step)
                .next(importEngagementReport20230701Step)
                .next(importTop10ListStep)
                .next(importMoviesStep)
                .next(importSeasonsStep)
                .next(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .next(fileCompressionStep)
                .build()
        } else {
            JobBuilder(getFunctionName(), jobRepository)
                .incrementer(RunIdIncrementer())
                .start(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .next(fileCompressionStep)
                .build()
        }

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
    fun importMoviesStep(
        movieProcessor: ItemProcessor<ReportSheetRow, Movie>,
        movieRepository: MovieRepository,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<ReportSheetRow, Movie>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader { if (movies.isNotEmpty()) movies.removeFirst() else null }
            .processor(movieProcessor)
            .writer { chunk -> movieRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    @Bean
    fun importSeasonsStep(
        seasonProcessor: ItemProcessor<ReportSheetRow, Season>,
        seasonRepository: SeasonRepository,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .chunk<ReportSheetRow, Season>(5, transactionManager)
            .allowStartIfComplete(true)
            .reader { if (seasons.isNotEmpty()) seasons.removeFirst() else null }
            .processor(seasonProcessor)
            .writer { chunk -> seasonRepository.saveAll(chunk.items) }
            .faultTolerant()
            .build()

    @Bean
    fun exportDatabaseSchemaStep(
        dataSourceProperties: DataSourceProperties,
        databaseExportService: DatabaseExportService,
    ): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ contribution, chunkContext ->
                databaseExportService.exportSchema(
                    title = "Netflix database",
                    databaseName = dataSourceProperties.name,
                    filename = "$ARTIFACTS_DIRECTORY/netflixdb-${dataSourceProperties.name}.sql",
                    entityClasses,
                )
                logger.info("${chunkContext.stepContext.jobName}.${contribution.stepExecution.stepName}: database has been exported")
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
                File("$ARTIFACTS_DIRECTORY/netflixdb-${dataSourceProperties.name}.sql")
                    .appendText(databaseExportService.getInsertStatement(dataSourceProperties.name, chunk.items))
            }
            .faultTolerant()
            .build()

    @Bean
    fun fileCompressionStep(dataSourceProperties: DataSourceProperties): Step =
        StepBuilder(getFunctionName(), jobRepository)
            .tasklet({ contribution, chunkContext ->
                val sqlFilename = "$ARTIFACTS_DIRECTORY/netflixdb-${dataSourceProperties.name}.sql"
                val zipFilename = "$ARTIFACTS_DIRECTORY/netflixdb-${dataSourceProperties.name}.zip"
                FileOutputStream(zipFilename).use { fileOutputStream ->
                    ZipOutputStream(fileOutputStream).use { zipOutputStream ->
                        val sqlFile = File(sqlFilename)
                        val fileInputStream = FileInputStream(sqlFile)
                        val zipEntry = ZipEntry(sqlFile.name)
                        zipOutputStream.putNextEntry(zipEntry)
                        zipOutputStream.write(fileInputStream.readAllBytes())
                    }
                }
                logger.info(
                    "${chunkContext.stepContext.jobName}.${contribution.stepExecution.stepName}: $sqlFilename -> $zipFilename",
                )
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
                    language = if (rowSet.getString("category")?.contains("(English)") == true) Locale.ENGLISH else null
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
    fun movieProcessor(movieRepository: MovieRepository): ItemProcessor<ReportSheetRow, Movie> =
        ItemProcessor<ReportSheetRow, Movie> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.MOVIE || reportSheetRow.runtime == null) return@ItemProcessor null

            movieRepository.findByTitleAndRuntime(reportSheetRow.title!!, reportSheetRow.runtime!!)?.let { movie ->
                reportSheetRow.originalTitle?.let { movie.originalTitle = it }
                reportSheetRow.runtime?.let { movie.runtime = it }
                reportSheetRow.releaseDate?.let { movie.releaseDate = it }
                reportSheetRow.availableGlobally?.let { movie.availableGlobally = it }
                reportSheetRow.language?.let { movie.language = it }
                movie.updateViewSummary(reportSheetRow)
                movie
            } ?: reportSheetRow.toMovie()
        }

    @Bean
    fun seasonProcessor(
        seasonRepository: SeasonRepository,
        tvShowRepository: TvShowRepository,
    ): ItemProcessor<ReportSheetRow, Season> =
        ItemProcessor<ReportSheetRow, Season> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.TV_SHOW || reportSheetRow.runtime == null) return@ItemProcessor null

            val season =
                seasonRepository.findByTitleAndRuntime(reportSheetRow.title!!, reportSheetRow.runtime!!)?.let { season ->
                    val updatedSeason = reportSheetRow.toSeason()
                    updatedSeason.seasonNumber?.let { season.seasonNumber = it }
                    updatedSeason.originalTitle?.let { season.originalTitle = it }
                    updatedSeason.runtime?.let { season.runtime = it }
                    updatedSeason.releaseDate?.let { season.releaseDate = it }
                    season.updateViewSummary(reportSheetRow)
                    season
                } ?: reportSheetRow.toSeason()

            val tvShowTitle = reportSheetRow.title.toTvShowTitle()!!
            val tvShow =
                (season.tvShow ?: tvShowRepository.findByTitle(tvShowTitle))?.let { tvShow ->
                    val updatedTvShow = reportSheetRow.toTvShow()
                    updatedTvShow.originalTitle?.let { tvShow.originalTitle = it }
                    updatedTvShow.releaseDate?.let { tvShow.releaseDate = it }
                    updatedTvShow.availableGlobally?.let { tvShow.availableGlobally = it }
                    updatedTvShow.language?.let { tvShow.language = it }
                    tvShow
                } ?: reportSheetRow.toTvShow()

            season.tvShow = tvShowRepository.save(tvShow)
            season
        }

    private fun writeReportSheetRow(chunk: Chunk<out ReportSheetRow>) {
        chunk.items.forEach { item ->
            when (item.category) {
                StreamingCategory.MOVIE -> movies.add(item)
                StreamingCategory.TV_SHOW -> seasons.add(item)
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
}
