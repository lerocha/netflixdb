package com.github.lerocha.netflixdb.dto

import java.util.Locale

enum class StreamingCategory {
    MOVIE,
    TV_SHOW,
}

fun String.toCategory(): StreamingCategory? {
    val value = this.lowercase(Locale.getDefault())
    return when {
        value.contains("film") -> StreamingCategory.MOVIE
        value.contains("tv") -> StreamingCategory.TV_SHOW
        else -> StreamingCategory.valueOf(this)
    }
}
