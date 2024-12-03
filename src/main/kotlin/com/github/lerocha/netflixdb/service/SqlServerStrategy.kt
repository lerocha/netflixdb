package com.github.lerocha.netflixdb.service

import com.github.lerocha.netflixdb.entity.AbstractEntity
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

@Component
class SqlServerStrategy : DatabaseStrategy {
    private val instantFormatter =
        DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC)

    private val physicalNamingStrategy = CamelCaseToUnderscoresNamingStrategy()

    override fun getPhysicalNamingStrategy(): PhysicalNamingStrategy = physicalNamingStrategy

    override fun <T> getSqlValues(vararg properties: T): String {
        return listOf(*properties).map { property ->
            when (property) {
                is Enum<*> -> "N'$property'"
                is String -> "N'${property.replace("'", "''")}'"
                is Instant -> "N'${instantFormatter.format(property)}'"
                is LocalDate -> "N'$property'"
                is UUID -> "'$property'"
                is Locale -> "N'${property.language}'"
                is AbstractEntity -> property.id.toString()
                else -> property ?: "null"
            }
        }.joinToString(", ")
    }
}
