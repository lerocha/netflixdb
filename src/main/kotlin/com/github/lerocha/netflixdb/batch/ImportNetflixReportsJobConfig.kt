package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toShow
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.Show
import com.github.lerocha.netflixdb.repository.SeasonRepository
import com.github.lerocha.netflixdb.repository.ShowRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.extensions.excel.RowMapper
import org.springframework.batch.extensions.excel.poi.PoiItemReader
import org.springframework.batch.item.data.RepositoryItemWriter
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.time.LocalDate
import kotlin.time.Duration

@Configuration
class ImportNetflixReportsJobConfig {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun importNetflixReportsJob(
        jobRepository: JobRepository,
        importMoviesFromEngagementReportStep: Step,
        importSeasonsFromEngagementReportStep: Step,
        weeklySummaryStep: Step,
    ): Job =
        JobBuilder("importNetflixShowsJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(importMoviesFromEngagementReportStep)
            .next(importSeasonsFromEngagementReportStep)
            .next(weeklySummaryStep)
            .build()

    @Bean
    fun importMoviesFromEngagementReportStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        engagementReportReader: PoiItemReader<ReportSheetRow>,
        showWriter: RepositoryItemWriter<Show>,
        showRepository: ShowRepository,
    ): Step =
        StepBuilder("importMoviesFromEngagementReportStep", jobRepository)
            .chunk<ReportSheetRow, Show>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader)
            .processor({ reportSheetRow ->
                if (reportSheetRow.category == StreamingCategory.TV_SHOW) return@processor null
                if (showRepository.findByTitle(reportSheetRow.title!!) != null) return@processor null
                logger.info("importMoviesFromEngagementReportStep: ${reportSheetRow.title}")
                reportSheetRow.toShow()
            })
            .writer(showWriter)
            .faultTolerant()
            .build()

    @Bean
    fun importSeasonsFromEngagementReportStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        engagementReportReader: PoiItemReader<ReportSheetRow>,
        seasonWriter: RepositoryItemWriter<Season>,
        seasonRepository: SeasonRepository,
    ): Step =
        StepBuilder("importSeasonsFromEngagementReportStep", jobRepository)
            .chunk<ReportSheetRow, Season>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(engagementReportReader)
            .processor({ reportSheetRow ->
                if (reportSheetRow.category == StreamingCategory.MOVIE) return@processor null
                if (seasonRepository.findByTitle(reportSheetRow.title!!) != null) return@processor null
                logger.info("importSeasonsFromEngagementReportStep: ${reportSheetRow.title}")
                reportSheetRow.toSeason()
            })
            .writer(seasonWriter)
            .faultTolerant()
            .build()

    @Bean
    fun engagementReportReader(engagementReportRowMapper: RowMapper<ReportSheetRow>) =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/What_We_Watched_A_Netflix_Engagement_Report_2024Jan-Jun.xlsx"))
            setRowMapper(engagementReportRowMapper)
        }

    @Bean
    fun engagementReportRowMapper(): RowMapper<ReportSheetRow> =
        RowMapper<ReportSheetRow> { rowSet ->
            val titles = rowSet.properties["Title"]?.toString()?.split("//") ?: emptyList()
            ReportSheetRow().apply {
                runtime = rowSet.properties["Runtime"]?.toString()?.let { runtime ->
                    Duration.parseOrNull(runtime.replace(":", "h") + "m")?.inWholeMinutes
                } ?: 0
                title = titles.firstOrNull()?.trim()
                originalTitle = titles.lastOrNull()?.trim()
                category = when (rowSet.metaData.sheetName) {
                    "Film" -> StreamingCategory.MOVIE
                    "TV" -> StreamingCategory.TV_SHOW
                    else -> null
                }
                availableGlobally = rowSet.properties["Available Globally?"]?.toString() == "Yes"
                releaseDate =
                    rowSet.properties["Release Date"]?.toString()?.let {
                        if (it.isNotBlank()) LocalDate.parse(it) else null
                    }
                hoursViewed =
                    rowSet.properties["Hours Viewed"]?.toString()?.let {
                        it.replace(",", "").toInt()
                    }
            }
        }

    @Bean
    fun weeklySummaryStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        showWriter: RepositoryItemWriter<Show>,
        weeklySummaryRowMapper: RowMapper<Show>,
        showRepository: ShowRepository,
    ): Step =
        StepBuilder("weeklySummaryStep", jobRepository)
            .chunk<Show, Show>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(
                PoiItemReader<Show>().apply {
                    setLinesToSkip(1)
                    setResource(ClassPathResource("reports/all-weeks-global.xlsx"))
                    setRowMapper(weeklySummaryRowMapper)
                },
            )
            .processor { show ->
                logger.info("weeklySummaryStep: ${show.title}")
                if (showRepository.findByTitle(show.title!!) == null) show else null
            }
            .writer(showWriter)
            .faultTolerant()
            .build()

    @Bean
    fun weeklySummaryRowMapper(): RowMapper<Show> =
        RowMapper<Show> { rowSet ->
            Show().apply {
                createdDate = Instant.now()
                modifiedDate = Instant.now()
                runtime =
                    rowSet.properties["runtime"]?.toString()?.let {
                        if (it > "") it.toBigDecimal().multiply(60.toBigDecimal()).toLong() else null
                    } ?: 0
                title = rowSet.properties["show_title"]?.toString()?.trim()
                originalTitle = rowSet.properties["show_title"]?.toString()?.trim()
                category =
                    rowSet.properties["category"]?.toString()?.trim()?.let {
                        when {
                            it.contains("TV") -> StreamingCategory.TV_SHOW
                            it.contains("Film") -> StreamingCategory.MOVIE
                            else -> null
                        }
                    }
                releaseDate = null
            }
        }

    @Bean
    fun showWriter(repository: ShowRepository): RepositoryItemWriter<Show> =
        RepositoryItemWriterBuilder<Show>()
            .repository(repository)
            .methodName("save")
            .build()

    @Bean
    fun seasonWriter(repository: SeasonRepository): RepositoryItemWriter<Season> =
        RepositoryItemWriterBuilder<Season>()
            .repository(repository)
            .methodName("save")
            .build()
}
