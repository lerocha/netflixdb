package com.github.lerocha.netflixdb.strategy

import com.github.lerocha.netflixdb.entity.AbstractEntity
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.PhysicalNamingStrategy
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

    private val physicalNamingStrategy = CamelCaseToUnderscoresNamingStrategy()

    override fun getPhysicalNamingStrategy(): PhysicalNamingStrategy = physicalNamingStrategy

    override fun <T> getSqlValues(vararg properties: T): String {
        return listOf(*properties).map { property ->
            when (property) {
                is Enum<*> -> "'$property'"
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
