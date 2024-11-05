package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toCategory
import com.github.lerocha.netflixdb.dto.toMovie
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toTvShow
import com.github.lerocha.netflixdb.entity.Episode
import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.SummaryDuration
import com.github.lerocha.netflixdb.entity.TvShow
import com.github.lerocha.netflixdb.entity.ViewSummary
import com.github.lerocha.netflixdb.repository.MovieRepository
import com.github.lerocha.netflixdb.repository.SeasonRepository
import com.github.lerocha.netflixdb.repository.TvShowRepository
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.extensions.excel.poi.PoiItemReader
import org.springframework.batch.extensions.excel.support.rowset.RowSet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.RepositoryItemWriter
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.PlatformTransactionManager
import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.EnumSet
import kotlin.time.Duration

@Configuration
class ImportNetflixDataJobConfig(
    private val dataSourceProperties: DataSourceProperties,
) {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun importNetflixDataJob(
        jobRepository: JobRepository,
        importMoviesFromEngagementReportStep: Step,
        importSeasonsFromEngagementReportStep: Step,
        importMoviesFromTop10ListStep: Step,
        importSeasonsFromTop10ListStep: Step,
        exportDatabaseStep: Step,
    ): Job =
        JobBuilder("importNetflixDataJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(importMoviesFromEngagementReportStep)
            .next(importMoviesFromTop10ListStep)
            .next(importSeasonsFromEngagementReportStep)
            .next(importSeasonsFromTop10ListStep)
            .next(exportDatabaseStep)
            .build()

    @Bean
    fun importMoviesFromEngagementReportStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
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
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
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
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
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
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
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
    fun exportDatabaseStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
    ): Step =
        StepBuilder("exportDatabaseStep", jobRepository)
            .tasklet({ stepContribution, chunkContext -> exportDatabase(stepContribution, chunkContext) }, transactionManager)
            .build()

    private fun exportDatabase(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus {
        val databaseName = dataSourceProperties.url.replace("jdbc:", "").split(":").first()
        val filename = "netflixdb-$databaseName.sql"
        val path = "build/$filename"
        val settings =
            mutableMapOf<String, Any>(
                "connection.driver_class" to dataSourceProperties.driverClassName,
                "hibernate.connection.url" to dataSourceProperties.url,
                "hibernate.connection.username" to dataSourceProperties.username,
                "hibernate.connection.password" to dataSourceProperties.password,
                "hibernate.hbm2ddl.auto" to "create",
                "show_sql" to "true",
            )

        val metadata =
            MetadataSources(
                StandardServiceRegistryBuilder()
                    .applySettings(settings)
                    .build(),
            ).addAnnotatedClasses(Movie::class.java)
                .addAnnotatedClasses(TvShow::class.java)
                .addAnnotatedClasses(Season::class.java)
                .addAnnotatedClasses(Episode::class.java)
                .addAnnotatedClasses(ViewSummary::class.java)
                .buildMetadata()

        File(path).writeText(
            """
            /**********************************************************************************
              NetflixDB for ${databaseName.uppercase()}
              Description: Creates and populates the Netflix database.
              Created on: ${OffsetDateTime.now()}
              Author: Luis Rocha
               
              WARNING: This file was generated by a tool and changes to this file will be lost
                       when this file is regenerated.
            ***********************************************************************************/

            """.trimIndent(),
        )

        SchemaExport()
            .setHaltOnError(true)
            .setFormat(true)
            .setDelimiter(";")
            .setOutputFile(path)
            .execute(EnumSet.of(TargetType.SCRIPT), SchemaExport.Action.CREATE, metadata)

        logger.info("${chunkContext.stepContext.jobName}.${contribution.stepExecution.stepName}: database has been exported")
        return RepeatStatus.FINISHED
    }

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
                    startDate = rowSet.getString("week")?.let { LocalDate.parse(it) }
                    endDate = rowSet.getString("week")?.let { LocalDate.parse(it).plusDays(6) }
                    duration = SummaryDuration.WEEKLY
                    runtime = rowSet.getRuntimeInMinutes("runtime")
                    title = rowSet.getString("show_title")?.trim()
                    originalTitle = rowSet.getString("show_title")?.trim()
                    category = rowSet.getString("category")?.toCategory()
                    availableGlobally = null
                    releaseDate = null
                    hoursViewed = rowSet.getInt("weekly_hours_viewed")
                    views = rowSet.getInt("weekly_views")
                }
            }
        }

    @Bean
    fun movieProcessor(movieRepository: MovieRepository): ItemProcessor<ReportSheetRow, Movie> =
        ItemProcessor<ReportSheetRow, Movie> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.MOVIE) return@ItemProcessor null
            if (movieRepository.findByTitle(reportSheetRow.title!!) != null) return@ItemProcessor null
            logger.info("movieProcessor: ${reportSheetRow.title}")
            reportSheetRow.toMovie()
        }

    @Bean
    fun seasonProcessor(
        seasonRepository: SeasonRepository,
        tvShowRepository: TvShowRepository,
    ): ItemProcessor<ReportSheetRow, Season> =
        ItemProcessor<ReportSheetRow, Season> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.TV_SHOW) return@ItemProcessor null
            if (seasonRepository.findByTitle(reportSheetRow.title!!) != null) return@ItemProcessor null
            logger.info("seasonProcessor: ${reportSheetRow.title}")
            reportSheetRow.toSeason().apply {
                if (this.seasonNumber is Int) {
                    val tvShow = reportSheetRow.toTvShow()
                    this.tvShow = tvShowRepository.findByTitle(tvShow.title!!) ?: tvShowRepository.save(tvShow)
                }
            }
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
