package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.dto.ReportSheetRow
import com.github.lerocha.netflixdb.dto.StreamingCategory
import com.github.lerocha.netflixdb.dto.toSeason
import com.github.lerocha.netflixdb.dto.toShow
import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.repository.MovieRepository
import com.github.lerocha.netflixdb.repository.SeasonRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.extensions.excel.poi.PoiItemReader
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.RepositoryItemWriter
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.PlatformTransactionManager
import java.time.LocalDate
import kotlin.time.Duration

@Configuration
class ImportNetflixDataJobConfig {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun importNetflixDataJob(
        jobRepository: JobRepository,
        importMoviesFromEngagementReportStep: Step,
        importSeasonsFromEngagementReportStep: Step,
        importMoviesFromTop10ListStep: Step,
        importSeasonsFromTop10ListStep: Step,
    ): Job =
        JobBuilder("importNetflixDataJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(importMoviesFromEngagementReportStep)
            .next(importMoviesFromTop10ListStep)
            .next(importSeasonsFromEngagementReportStep)
            .next(importSeasonsFromTop10ListStep)
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
    fun engagementReportReader() =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/What_We_Watched_A_Netflix_Engagement_Report_2024Jan-Jun.xlsx"))
            setRowMapper { rowSet ->
                val titles = rowSet.properties["Title"]?.toString()?.split("//") ?: emptyList()
                ReportSheetRow().apply {
                    runtime = rowSet.properties["Runtime"]?.toString()?.let { runtime ->
                        Duration.parseOrNull(runtime.replace(":", "h") + "m")?.inWholeMinutes
                    } ?: 0
                    title = titles.firstOrNull()?.trim()
                    originalTitle = titles.lastOrNull()?.trim()
                    category =
                        when (rowSet.metaData.sheetName) {
                            "Film" -> StreamingCategory.MOVIE
                            "TV" -> StreamingCategory.TV_SHOW
                            else -> null
                        }
                    availableGlobally =
                        rowSet.properties["Available Globally?"]?.toString() == "Yes"
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
        }

    @Bean
    fun top10ListReader() =
        PoiItemReader<ReportSheetRow>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("reports/all-weeks-global.xlsx"))
            setRowMapper { rowSet ->
                ReportSheetRow().apply {
                    runtime =
                        rowSet.properties["runtime"]?.toString()?.let {
                            if (it > "") {
                                it.toBigDecimal().multiply(60.toBigDecimal())
                                    .toLong()
                            } else {
                                null
                            }
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
        }

    @Bean
    fun movieProcessor(movieRepository: MovieRepository): ItemProcessor<ReportSheetRow, Movie> =
        ItemProcessor<ReportSheetRow, Movie> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.MOVIE) return@ItemProcessor null
            if (movieRepository.findByTitle(reportSheetRow.title!!) != null) return@ItemProcessor null
            logger.info("showProcessor: ${reportSheetRow.title}")
            reportSheetRow.toShow()
        }

    @Bean
    fun seasonProcessor(seasonRepository: SeasonRepository): ItemProcessor<ReportSheetRow, Season> =
        ItemProcessor<ReportSheetRow, Season> { reportSheetRow ->
            if (reportSheetRow.category != StreamingCategory.TV_SHOW) return@ItemProcessor null
            if (seasonRepository.findByTitle(reportSheetRow.title!!) != null) return@ItemProcessor null
            logger.info("seasonProcessor: ${reportSheetRow.title}")
            reportSheetRow.toSeason()
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
}
