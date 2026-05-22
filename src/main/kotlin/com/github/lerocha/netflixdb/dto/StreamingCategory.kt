package com.github.lerocha.netflixdb.dto

import java.util.Locale

enum class StreamingCategory {
    MOVIE,
    TV_SHOW,
}

/**
 * Maps Excel sheet names and category labels to [StreamingCategory].
 * Falls back to [StreamingCategory.valueOf] when the label is already an enum constant name.
 */
fun String.toCategory(): StreamingCategory? {
    val normalized = lowercase(Locale.getDefault())
    return when {
        normalized.contains("film") -> StreamingCategory.MOVIE
        normalized.contains("tv") -> StreamingCategory.TV_SHOW
        else -> StreamingCategory.valueOf(this)
    }
}
