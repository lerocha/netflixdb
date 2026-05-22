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

/** Normalized row from an Excel report before persistence. */
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
        createdDate = now()
        modifiedDate = now()
        title = this@toMovie.title
        originalTitle = this@toMovie.originalTitle
        runtime = this@toMovie.runtime
        releaseDate = this@toMovie.releaseDate
        availableGlobally = this@toMovie.availableGlobally
        locale = this@toMovie.locale
        viewSummaries.add(toViewSummary())
    }.apply { viewSummaries.forEach { it.movie = this } }

fun ReportSheetRow.toTvShow() =
    TvShow().apply {
        createdDate = now()
        modifiedDate = now()
        title = this@toTvShow.title?.toTvShowTitle()
        originalTitle = this@toTvShow.originalTitle?.toTvShowTitle()
        availableGlobally = this@toTvShow.availableGlobally
        locale = this@toTvShow.locale
    }

/** Strips a trailing ": Season N" suffix used in engagement report TV titles. */
fun String?.toTvShowTitle(): String? =
    this?.split(":")?.lastOrNull()?.let { suffix ->
        this.replace(":$suffix", "").trim()
    }

fun ReportSheetRow.toSeason() =
    Season().apply {
        createdDate = now()
        modifiedDate = now()
        seasonNumber = title?.extractSeasonNumber()
        this.title = this@toSeason.title
        originalTitle = this@toSeason.originalTitle
        runtime = this@toSeason.runtime
        releaseDate = this@toSeason.releaseDate
        viewSummaries.add(toViewSummary())
    }.apply { viewSummaries.forEach { it.season = this } }

private fun String.extractSeasonNumber(): Int? =
    split(":").lastOrNull()?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }?.toInt()

fun ReportSheetRow.toViewSummary() =
    ViewSummary().apply {
        createdDate = now()
        modifiedDate = now()
        startDate = this@toViewSummary.startDate
        endDate = this@toViewSummary.endDate
        duration = this@toViewSummary.duration
        hoursViewed = this@toViewSummary.hoursViewed
        views = this@toViewSummary.views
        viewRank = this@toViewSummary.viewRank
        cumulativeWeeksInTop10 = this@toViewSummary.cumulativeWeeksInTop10
    }

/**
 * Upserts a [ViewSummary] on the owning [Movie] or [Season].
 * Matches on parent entity, [SummaryDuration], and [ViewSummary.startDate]; newer metrics overwrite nullable fields only.
 */
fun AbstractEntity.updateViewSummary(reportSheetRow: ReportSheetRow) {
    val viewSummaries =
        when (this) {
            is Movie -> viewSummaries
            is Season -> viewSummaries
            else -> return
        }
    val movie = this as? Movie
    val season = this as? Season

    val incoming =
        reportSheetRow.toViewSummary().apply {
            this.movie = movie
            this.season = season
        }

    val existing =
        viewSummaries.firstOrNull {
            it.movie == movie &&
                it.season == season &&
                it.duration == incoming.duration &&
                it.startDate == incoming.startDate
        }

    if (existing != null) {
        incoming.viewRank?.let { existing.viewRank = it }
        incoming.hoursViewed?.let { existing.hoursViewed = it }
        incoming.views?.let { existing.views = it }
        incoming.cumulativeWeeksInTop10?.let { existing.cumulativeWeeksInTop10 = it }
        return
    }

    viewSummaries.add(incoming)
    if (movie != null) movie.viewSummaries = viewSummaries
    if (season != null) season.viewSummaries = viewSummaries
}

/** Fixed import timestamp so generated SQL artifacts are reproducible across runs. */
fun now(): Instant = LocalDate.of(2024, 1, 1).atStartOfDay().toInstant(ZoneOffset.UTC)
