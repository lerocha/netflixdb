package com.github.lerocha.netflixdb.service

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
class OracleStrategy : DatabaseStrategy {
    private val instantFormatter =
        DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneOffset.UTC)

    private val physicalNamingStrategy = CamelCaseToUnderscoresNamingStrategy()

    override fun getPhysicalNamingStrategy(): PhysicalNamingStrategy = physicalNamingStrategy

    override fun getInitDatabase(): String {
        return """
            ALTER SESSION SET CONTAINER=FREEPDB1;
            ALTER SESSION SET CURRENT_SCHEMA=netflixdb;
            """
    }

    override fun <T> getSqlValues(vararg properties: T): String {
        return listOf(*properties).map { property ->
            when (property) {
                is Enum<*> -> "'$property'"
                is Boolean -> if (property) "1" else "0"
                is String -> "'${property.replace("'", "''")}'"
                is Instant -> "timestamp '${instantFormatter.format(property)}'"
                is LocalDate -> "date '$property'"
                is UUID -> "'${property.toString().uppercase().replace("-", "")}'"
                is AbstractEntity -> "'${property.id.toString().uppercase().replace("-", "")}'"
                else -> property ?: "null"
            }
        }.joinToString(", ")
    }

    override fun <T : AbstractEntity> getInsertStatement(entities: List<T>): String {
        val stringBuilder = StringBuilder()
        entities.map { entity -> getInsertStatement(entity) }.forEach { stringBuilder.appendLine(it) }
        return stringBuilder.toString()
    }
}
