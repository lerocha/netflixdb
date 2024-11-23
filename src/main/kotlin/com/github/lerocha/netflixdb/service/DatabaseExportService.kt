package com.github.lerocha.netflixdb.service

import com.github.lerocha.netflixdb.entity.AbstractEntity
import com.github.lerocha.netflixdb.repository.MovieRepository
import com.github.lerocha.netflixdb.repository.SeasonRepository
import com.github.lerocha.netflixdb.repository.TvShowRepository
import com.github.lerocha.netflixdb.repository.ViewSummaryRepository
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.tool.hbm2ddl.SchemaExport
import org.hibernate.tool.schema.TargetType
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties
import org.springframework.stereotype.Service
import java.io.File
import java.time.OffsetDateTime
import java.util.EnumSet

@Service
class DatabaseExportService(
    private val dataSourceProperties: DataSourceProperties,
    private val databaseStrategyFactory: DatabaseStrategyFactory,
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val seasonRepository: SeasonRepository,
    private val viewSummaryRepository: ViewSummaryRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        const val CHUNK_SIZE = 1
        const val ARTIFACTS_DIRECTORY = "build/artifacts"
    }

    fun exportSchema(
        title: String,
        databaseName: String,
        filename: String,
        entityClasses: List<Class<out AbstractEntity>>,
    ) {
        val path = "$ARTIFACTS_DIRECTORY/$filename"
        File(path).parentFile.mkdirs()
        val settings =
            mutableMapOf<String, Any>(
                "connection.driver_class" to dataSourceProperties.driverClassName,
                "hibernate.connection.url" to dataSourceProperties.url,
                "hibernate.connection.username" to dataSourceProperties.username,
                "hibernate.connection.password" to dataSourceProperties.password,
                "hibernate.hbm2ddl.auto" to "create",
                "show_sql" to "true",
                "hibernate.implicit_naming_strategy" to "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy",
                "hibernate.physical_naming_strategy" to "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            )

        val metadata =
            MetadataSources(
                StandardServiceRegistryBuilder()
                    .applySettings(settings)
                    .build(),
            ).addAnnotatedClasses(*entityClasses.toTypedArray()).buildMetadata()

        File(path).writeText(
            """
            /**********************************************************************************
              $title for ${databaseName.uppercase()}
              Description: Creates and populates the $title.
              Created on: ${OffsetDateTime.now()}
              Author: Luis Rocha
               
              WARNING: This file was generated by a tool and changes to this file will be lost
                       when this file is regenerated.
            ***********************************************************************************/
            ${databaseStrategyFactory.getInstance(databaseName).getInitDatabase()}
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
        val path = "$ARTIFACTS_DIRECTORY/$filename"
        File(path).appendText(
            StringBuilder()
                .append(getInsertStatement(databaseName, "Movies data", movieRepository.findAll()))
                .append(getInsertStatement(databaseName, "TV Show data", tvShowRepository.findAll()))
                .append(getInsertStatement(databaseName, "Season data", seasonRepository.findAll()))
                .append(getInsertStatement(databaseName, "ViewSummary data", viewSummaryRepository.findAll())).toString(),
        )
        logger.info("Exported data for $databaseName to $path")
    }

    fun getInsertStatement(
        databaseName: String,
        caption: String,
        data: List<AbstractEntity>,
        chunkSize: Int = CHUNK_SIZE,
    ): String {
        val databaseStrategy = databaseStrategyFactory.getInstance(databaseName)
        val stringBuilder = StringBuilder()
        stringBuilder.appendLine().appendLine(printHeader(caption))
        data.chunked(chunkSize).forEach { chunk ->
            stringBuilder.appendLine(databaseStrategy.getInsertStatement(chunk))
        }
        return stringBuilder.toString()
    }

    private fun printHeader(text: String) =
        """
        /**********************************************************************************
          $text
        ***********************************************************************************/
        """.trimIndent()
}
