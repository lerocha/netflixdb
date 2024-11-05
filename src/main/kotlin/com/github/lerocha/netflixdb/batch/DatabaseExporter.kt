package com.github.lerocha.netflixdb.batch

import com.github.lerocha.netflixdb.entity.Episode
import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.TvShow
import com.github.lerocha.netflixdb.entity.ViewSummary
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import org.slf4j.LoggerFactory
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.stereotype.Component
import java.io.File
import java.time.OffsetDateTime
import java.util.EnumSet

@Component
class DatabaseExporter(
    private val dataSourceProperties: DataSourceProperties,
) : Tasklet {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext,
    ): RepeatStatus? {
        val databaseName = dataSourceProperties.url.replace("jdbc:", "").split(":").first()
        val filename = "netflixdb-$databaseName.sql"
        exportSchema(databaseName, filename)
        exportData(databaseName, filename)
        logger.info("${chunkContext.stepContext.jobName}.${contribution.stepExecution.stepName}: database has been exported")
        return RepeatStatus.FINISHED
    }

    fun exportSchema(
        databaseName: String,
        filename: String,
    ) {
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

        logger.info("Exported schema for $databaseName to $path")
    }

    fun exportData(
        databaseName: String,
        filename: String,
    ) {
        val path = "build/$filename"
        logger.info("Exported data for $databaseName to $path")
    }
}
