package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.entity.WeeklySummary
import com.github.lerocha.netflixdb.repository.WeeklySummaryRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.configuration.annotation.StepScope
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.launch.support.RunIdIncrementer
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.extensions.excel.RowMapper
import org.springframework.batch.extensions.excel.mapping.PassThroughRowMapper
import org.springframework.batch.extensions.excel.poi.PoiItemReader
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.RepositoryItemWriter
import org.springframework.batch.item.data.builder.RepositoryItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class ImportWeeklySummaryJobConfig {
    private val logger: Logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun importWeeklySummaryJob(
        jobRepository: JobRepository,
        importWeeklySummaryStep: Step,
    ): Job =
        JobBuilder("importWeeklySummaryJob", jobRepository)
            .incrementer(RunIdIncrementer())
            .start(importWeeklySummaryStep)
            .build()

    @Bean
    fun importWeeklySummaryStep(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        importWeeklySummaryReader: PoiItemReader<Array<String>>,
        importWeeklySummaryProcessor: ItemProcessor<Array<String>, WeeklySummary>,
        importWeeklySummaryWriter: RepositoryItemWriter<WeeklySummary>,
    ): Step =
        StepBuilder("importWeeklySummaryStep", jobRepository)
            .chunk<Array<String>, WeeklySummary>(10, transactionManager)
            .allowStartIfComplete(true)
            .reader(importWeeklySummaryReader)
            .processor(importWeeklySummaryProcessor)
            .writer(importWeeklySummaryWriter)
            .faultTolerant()
            .build()

    // https://github.com/spring-projects/spring-batch-extensions/tree/main/spring-batch-excel
    @Bean
    @StepScope
    fun importWeeklySummaryReader(rowMapper: RowMapper<Array<String>>): PoiItemReader<Array<String>> =
        PoiItemReader<Array<String>>().apply {
            setLinesToSkip(1)
            setResource(ClassPathResource("all-weeks-global.xlsx"))
            setRowMapper(PassThroughRowMapper())
        }

    @Bean
    fun rowMapper(): RowMapper<Array<String>> {
        return PassThroughRowMapper()
    }

    @Bean
    fun importWeeklySummaryProcessor() =
        ItemProcessor<Array<String>, WeeklySummary?> { row ->
            logger.info(row.joinToString(", "))
            null
        }

    @Bean
    fun importWeeklySummaryWriter(repository: WeeklySummaryRepository): RepositoryItemWriter<WeeklySummary> =
        RepositoryItemWriterBuilder<WeeklySummary>()
            .repository(repository)
            .methodName("save")
            .build()
}
