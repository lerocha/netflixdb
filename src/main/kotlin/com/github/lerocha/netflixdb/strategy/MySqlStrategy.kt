package com.github.lerocha.netflixdb.strategy

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.github.lerocha.netflixdb.entity.AbstractEntity
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class MySqlStrategy : DatabaseStrategy {
    private val instantFormatter =
        DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSSSSS +00:00")
            .withZone(ZoneOffset.UTC)
    private val namingStrategy: PropertyNamingStrategies.NamingBase = PropertyNamingStrategies.SnakeCaseStrategy()

    override fun getName(name: String): String = namingStrategy.translate(name)

    override fun <T> getSqlValues(vararg properties: T): String {
        return listOf(*properties).map { property ->
            when (property) {
                is String -> "'${property.replace("'", "''")}'"
                is Instant -> "'${instantFormatter.format(property)}'"
                is LocalDate -> "'$property'"
                is UUID -> "0x00${property.toString().uppercase().replace("-", "")}"
                is AbstractEntity -> "0x00${property.id.toString().uppercase().replace("-", "")}"
                else -> property ?: "null"
            }
        }.joinToString(", ")
    }
}
