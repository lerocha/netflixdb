package com.github.lerocha.netflixdb.strategy

import com.github.lerocha.netflixdb.entity.AbstractEntity
import com.github.lerocha.netflixdb.entity.Movie
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Component
class MySqlStrategy() : DatabaseStrategy {
    private val instantFormatter =
        DateTimeFormatter.ofPattern("YYYY-MM-dd HH:mm:ss.SSSSSS +00:00")
            .withZone(ZoneOffset.UTC)

    override fun getInsertStatement(movie: Movie): String {
        val values =
            getSqlValues(
                movie.id,
                movie.title,
                movie.originalTitle,
                movie.availableGlobally,
                movie.releaseDate,
                movie.runtime,
                movie.createdDate,
                movie.modifiedDate,
            )
        return "INSERT INTO movie (id, title, original_title, available_globally, " +
            "release_date, runtime, created_date, modified_date) VALUES ($values);"
    }

    override fun getInsertStatement(movies: List<Movie>): String {
        TODO("Not yet implemented")
    }

    private fun <T> getSqlValues(vararg properties: T): String {
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
