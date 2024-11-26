package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toCategory
import com.github.lerocha.netflixdb.dto.toMovie
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toTvShow
import com.github.lerocha.netflixdb.dto.toTvShowTitle
import com.github.lerocha.netflixdb.dto.toViewSummary
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
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.RepositoryItemWriter
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder
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
import java.time.LocalDate
import java.util.Locale
import kotlin.time.Duration

@Configuration
class CreateNetflixDatabaseJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val dataSourceProperties: DataSourceProperties,
    private val databaseExportService: DatabaseExportService,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
    private val movieTitles: MutableSet<String> = mutableSetOf()
    private val seasonTitles: MutableSet<String> = mutableSetOf()
    private val entityClasses =
        listOf(
            Movie::class.java,
            TvShow::class.java,
            Season::class.java,
            Episode::class.java,
            ViewSummary::class.java,
        )

    companion object {
        const val ARTIFACTS_DIRECTORY = "build/artifacts"
    }

    @Bean
    fun createNetflixDatabaseJob(
        hibernateProperties: HibernateProperties,
        importMoviesFromEngagementReportStep: Step,
        importSeasonsFromEngagementReportStep: Step,
        importMoviesFromTop10ListStep: Step,
        importSeasonsFromTop10ListStep: Step,
        exportDatabaseSchemaStep: Step,
        movieRepository: MovieRepository,
        tvShowRepository: TvShowRepository,
        seasonRepository: SeasonRepository,
        viewSummaryRepository: ViewSummaryRepository,
    ): Job =
        if (hibernateProperties.ddlAuto == "create") {
            JobBuilder("createNetflixDatabaseJob", jobRepository)
                .incrementer(RunIdIncrementer())
                .start(importMoviesFromEngagementReportStep)
                .next(importMoviesFromTop10ListStep)
                .next(importSeasonsFromEngagementReportStep)
                .next(importSeasonsFromTop10ListStep)
                .next(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .build()
        } else {
            JobBuilder("createNetflixDatabaseJob", jobRepository)
                .incrementer(RunIdIncrementer())
                .start(exportDatabaseSchemaStep)
                .next(exportDataStep("movie", movieRepository))
                .next(exportDataStep("tvShow", tvShowRepository))
                .next(exportDataStep("season", seasonRepository))
                .next(exportDataStep("viewSummary", viewSummaryRepository))
                .build()
        }

    @Bean
    fun importMoviesFromEngagementReportStep(
        engagementReportReader: PoiItemReader<ReportSheetRow>,
        movieProcessor: ItemProcessor<ReportSheetRow, Movie>,
        movieWriter: RepositoryItemWriter<Movie>,
    ): Step =
        StepBuilder("importMoviesFromEngagementReportStep", jobRepository)
            .chunk<ReportSheetRow, Movie>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader)
            .processor(movieProcessor)
            .writer(movieWriter)
            .faultTolerant()
            .build()

    @Bean
    fun importMoviesFromTop10ListStep(
        top10ListReader: PoiItemReader<ReportSheetRow>,
        movieProcessor: ItemProcessor<ReportSheetRow, Movie>,
        movieWriter: RepositoryItemWriter<Movie>,
    ): Step =
        StepBuilder("importMoviesFromTop10ListStep", jobRepository)
            .chunk<ReportSheetRow, Movie>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(top10ListReader)
            .processor(movieProcessor)
            .writer(movieWriter)
            .faultTolerant()
            .build()

    @Bean
    fun importSeasonsFromEngagementReportStep(
        engagementReportReader: PoiItemReader<ReportSheetRow>,
        seasonProcessor: ItemProcessor<ReportSheetRow, Season>,
        seasonWriter: RepositoryItemWriter<Season>,
    ): Step =
        StepBuilder("importSeasonsFromEngagementReportStep", jobRepository)
            .chunk<ReportSheetRow, Season>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader)
            .processor(seasonProcessor)
            .writer(seasonWriter)
            .faultTolerant()
            .build()

    @Bean
    fun importSeasonsFromTop10ListStep(
        top10ListReader: PoiItemReader<ReportSheetRow>,
        seasonProcessor: ItemProcessor<ReportSheetRow, Season>,
        seasonWriter: RepositoryItemWriter<Season>,
    ): Step =
        StepBuilder("importSeasonsFromTop10ListStep", jobRepository)
            .chunk<ReportSheetRow, Season>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(top10ListReader)
            .processor(seasonProcessor)
            .writer(seasonWriter)
            .faultTolerant()
            .build()

    @Bean
    fun exportDatabaseSchemaStep(
        dataSourceProperties: DataSourceProperties,
        databaseExportService: DatabaseExportService,
    ): Step =
        StepBuilder("exportDatabaseSchemaStep", jobRepository)
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
    fun engagementReportReader() =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/What_We_Watched_A_Netflix_Engagement_Report_2024Jan-Jun.xlsx"))
            setRowMapper { rowSet ->
                val titles = rowSet.getString("Title")?.split("//") ?: emptyList()
                ReportSheetRow().apply {
                    startDate = LocalDate.parse("2024-01-01")
                    endDate = LocalDate.parse("2024-06-30")
                    duration = SummaryDuration.SEMI_ANNUALLY
                    runtime = rowSet.getRuntimeInMinutes("Runtime")
                    title = titles.firstOrNull()?.trim()
                    originalTitle = titles.lastOrNull()?.trim()
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
                    originalTitle = rowSet.getString("show_title")?.trim()
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
            if (reportSheetRow.category != StreamingCategory.MOVIE) return@ItemProcessor null
            movieRepository.findByTitle(reportSheetRow.title!!)?.let { movie ->
                movie.apply {
                    reportSheetRow.originalTitle?.let { originalTitle = it }
                    reportSheetRow.runtime?.let { runtime = it }
                    reportSheetRow.releaseDate?.let { releaseDate = it }
                    reportSheetRow.availableGlobally?.let { availableGlobally = it }
                    val viewSummary = reportSheetRow.toViewSummary().apply { this.movie = movie }
                    val existingViewSummary =
                        this.viewSummaries.firstOrNull {
                            it.movie == movie && it.duration == viewSummary.duration && it.startDate == viewSummary.startDate
                        }
                    if (existingViewSummary != null) {
                        viewSummary.viewRank?.let { existingViewSummary.viewRank = it }
                        viewSummary.hoursViewed?.let { existingViewSummary.hoursViewed = it }
                        viewSummary.views?.let { existingViewSummary.views = it }
                        viewSummary.cumulativeWeeksInTop10?.let { existingViewSummary.cumulativeWeeksInTop10 = it }
                    } else {
                        this.viewSummaries.add(viewSummary)
                    }
                }
                return@ItemProcessor movie
            }
            if (movieTitles.contains(reportSheetRow.title)) return@ItemProcessor null
            movieTitles.add(reportSheetRow.title!!)
            reportSheetRow.toMovie()
        }

    @Bean
    fun seasonProcessor(
        seasonRepository: SeasonRepository,
        tvShowRepository: TvShowRepository,
    ): ItemProcessor<ReportSheetRow, Season> =
        ItemProcessor<ReportSheetRow, Season> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.TV_SHOW) return@ItemProcessor null

            val season =
                seasonRepository.findByTitle(reportSheetRow.title!!)?.let { season ->
                    val updatedSeason = reportSheetRow.toSeason()
                    updatedSeason.seasonNumber?.let { season.seasonNumber = it }
                    updatedSeason.originalTitle?.let { season.originalTitle = it }
                    updatedSeason.runtime?.let { season.runtime = it }
                    updatedSeason.releaseDate?.let { season.releaseDate = it }
                    val viewSummary = reportSheetRow.toViewSummary().apply { this.season = season }
                    val existingViewSummary =
                        season.viewSummaries.firstOrNull {
                            it.season == season && it.duration == viewSummary.duration && it.startDate == viewSummary.startDate
                        }
                    if (existingViewSummary != null) {
                        viewSummary.viewRank?.let { existingViewSummary.viewRank = it }
                        viewSummary.hoursViewed?.let { existingViewSummary.hoursViewed = it }
                        viewSummary.views?.let { existingViewSummary.views = it }
                        viewSummary.cumulativeWeeksInTop10?.let { existingViewSummary.cumulativeWeeksInTop10 = it }
                    } else {
                        season.viewSummaries.add(viewSummary)
                    }
                    season
                } ?: reportSheetRow.toSeason()

            if (season.id !is Long && seasonTitles.contains(reportSheetRow.title)) return@ItemProcessor null
            seasonTitles.add(reportSheetRow.title!!)

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

    @Bean
    fun movieWriter(movieRepository: MovieRepository): RepositoryItemWriter<Movie> =
        RepositoryItemWriterBuilder<Movie>()
            .repository(movieRepository)
            .methodName("save")
            .build()

    @Bean
    fun seasonWriter(seasonRepository: SeasonRepository): RepositoryItemWriter<Season> =
        RepositoryItemWriterBuilder<Season>()
            .repository(seasonRepository)
            .methodName("save")
            .build()

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
}
