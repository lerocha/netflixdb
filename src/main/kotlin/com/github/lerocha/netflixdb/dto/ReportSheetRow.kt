package com.github.lerocha.netflixdb.dto

import com.github.lerocha.netflixdb.entity.Movie
import com.github.lerocha.netflixdb.entity.Season
import com.github.lerocha.netflixdb.entity.SummaryDuration
import com.github.lerocha.netflixdb.entity.TvShow
import com.github.lerocha.netflixdb.entity.ViewSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class ReportSheetRow(
    var title: String? = null,
    var originalTitle: String? = null,
    var category: StreamingCategory? = null,
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
        this.viewSummaries.add(this@toMovie.toViewSummary())
    }.apply { this.viewSummaries.forEach { it.movie = this } }

fun ReportSheetRow.toTvShow() =
    TvShow().apply {
        this.createdDate = now()
        this.modifiedDate = now()
        this.title =
            this@toTvShow.title?.split(":")?.lastOrNull()?.let { last ->
                this@toTvShow.title?.replace(":$last", "")?.trim()
            }
        this.originalTitle =
            this@toTvShow.originalTitle?.split(":")?.lastOrNull()?.let { last ->
                this@toTvShow.originalTitle?.replace(":$last", "")?.trim()
            }
        this.availableGlobally = this@toTvShow.availableGlobally
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

fun now(): Instant = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
