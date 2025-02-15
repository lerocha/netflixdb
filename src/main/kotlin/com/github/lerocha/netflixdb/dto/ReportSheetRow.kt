package com.github.lerocha.netflixdb.dto

import com.github.lerocha.netflixdb.entity.AbstractEntity
import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.SummaryDuration
import com.github.lerocha.netflixdb.entity.TvShow
import com.github.lerocha.netflixdb.entity.ViewSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

data class ReportSheetRow(
    var title: String? = null,
    var originalTitle: String? = null,
    var category: StreamingCategory? = null,
    var locale: Locale? = null,
    var runtime: Long? = null,
    var releaseDate: LocalDate? = null,
    var availableGlobally: Boolean? = false,
    var hoursViewed: Int? = null,
    var views: Int? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,
    var duration: SummaryDuration? = null,
    var viewRank: Int? = null,
    var cumulativeWeeksInTop10: Int? = null,
)

fun ReportSheetRow.toMovie() =
    Movie().apply {
        this.createdDate = now()
        this.modifiedDate = now()
        this.title = this@toMovie.title
        this.originalTitle = this@toMovie.originalTitle
        this.runtime = this@toMovie.runtime
        this.releaseDate = this@toMovie.releaseDate
        this.availableGlobally = this@toMovie.availableGlobally
        this.locale = this@toMovie.locale
        this.viewSummaries.add(this@toMovie.toViewSummary())
    }.apply { this.viewSummaries.forEach { it.movie = this } }

fun ReportSheetRow.toTvShow() =
    TvShow().apply {
        this.createdDate = now()
        this.modifiedDate = now()
        this.title = this@toTvShow.title?.toTvShowTitle()
        this.originalTitle = this@toTvShow.originalTitle?.toTvShowTitle()
        this.availableGlobally = this@toTvShow.availableGlobally
        this.locale = this@toTvShow.locale
    }

fun String?.toTvShowTitle() =
    this?.split(":")?.lastOrNull()?.let { last ->
        this.replace(":$last", "").trim()
    }

fun ReportSheetRow.toSeason() =
    Season().apply {
        this.createdDate = now()
        this.modifiedDate = now()
        this.seasonNumber =
            this@toSeason.title?.split(":")?.lastOrNull()?.filter { it.isDigit() }?.let {
                if (it.isNotBlank()) it.toInt() else null
            }
        this.title = this@toSeason.title
        this.originalTitle = this@toSeason.originalTitle
        this.runtime = this@toSeason.runtime
        this.releaseDate = this@toSeason.releaseDate
        this.viewSummaries.add(this@toSeason.toViewSummary())
    }.apply { this.viewSummaries.forEach { it.season = this } }

fun ReportSheetRow.toViewSummary() =
    ViewSummary().apply {
        this.createdDate = now()
        this.modifiedDate = now()
        this.startDate = this@toViewSummary.startDate
        this.endDate = this@toViewSummary.endDate
        this.duration = this@toViewSummary.duration
        this.hoursViewed = this@toViewSummary.hoursViewed
        this.views = this@toViewSummary.views
        this.viewRank = this@toViewSummary.viewRank
        this.cumulativeWeeksInTop10 = this@toViewSummary.cumulativeWeeksInTop10
    }

fun AbstractEntity.updateViewSummary(reportSheetRow: ReportSheetRow) {
    val viewSummaries =
        when (this) {
            is Movie -> this.viewSummaries
            is Season -> this.viewSummaries
            else -> return
        }
    val movie = if (this is Movie) this else null
    val season = if (this is Season) this else null

    val viewSummary =
        reportSheetRow.toViewSummary().apply {
            this.movie = movie
            this.season = season
        }
    val existingViewSummary =
        viewSummaries.firstOrNull {
            it.movie == movie && it.season == season && it.duration == viewSummary.duration && it.startDate == viewSummary.startDate
        }
    if (existingViewSummary != null) {
        viewSummary.viewRank?.let { existingViewSummary.viewRank = it }
        viewSummary.hoursViewed?.let { existingViewSummary.hoursViewed = it }
        viewSummary.views?.let { existingViewSummary.views = it }
        viewSummary.cumulativeWeeksInTop10?.let { existingViewSummary.cumulativeWeeksInTop10 = it }
    } else {
        viewSummaries.add(viewSummary)
    }

    if (movie is Movie) {
        movie.viewSummaries = viewSummaries
    }

    if (season is Season) {
        season.viewSummaries = viewSummaries
    }
}

fun now(): Instant = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
