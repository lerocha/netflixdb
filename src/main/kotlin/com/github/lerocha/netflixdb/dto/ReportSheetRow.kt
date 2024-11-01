package com.github.lerocha.netflixdb.dto

import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.TvShow
import java.time.Instant
import java.time.LocalDate

data class ReportSheetRow(
    var title: String? = null,
    var originalTitle: String? = null,
    var category: StreamingCategory? = null,
    var runtime: Long? = null,
    var releaseDate: LocalDate? = null,
    var availableGlobally: Boolean? = false,
    var hoursViewed: Int? = null,
)

fun ReportSheetRow.toShow() =
    Movie().apply {
        this.createdDate = Instant.now()
        this.modifiedDate = Instant.now()
        this.title = this@toShow.title
        this.originalTitle = this@toShow.originalTitle
        this.runtime = this@toShow.runtime
        this.releaseDate = this@toShow.releaseDate
        this.availableGlobally = this@toShow.availableGlobally
    }

fun ReportSheetRow.toTvShow() =
    TvShow().apply {
        this.createdDate = Instant.now()
        this.modifiedDate = Instant.now()
        this.title = this@toTvShow.title
        this.originalTitle = this@toTvShow.originalTitle
        this.availableGlobally = this@toTvShow.availableGlobally
    }

fun ReportSheetRow.toSeason() =
    Season().apply {
        this.createdDate = Instant.now()
        this.modifiedDate = Instant.now()
        this.number =
            this@toSeason.title?.split(":")?.lastOrNull()?.filter { it.isDigit() }?.let {
                if (it.isNotBlank()) it.toInt() else null
            }
        this.title = this@toSeason.title
        this.originalTitle = this@toSeason.originalTitle
        this.runtime = this@toSeason.runtime
        this.releaseDate = this@toSeason.releaseDate
    }
