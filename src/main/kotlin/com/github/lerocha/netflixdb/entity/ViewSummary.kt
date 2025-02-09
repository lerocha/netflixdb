package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate

@Entity
@Table(
//    indexes = [
//        Index(name = "fk_view_summary_movie_id", columnList = "movie_id", unique = false),
//        Index(name = "fk_view_summary_season_id", columnList = "season_id", unique = false),
//    ],
)
class ViewSummary : AbstractEntity() {
    @Column(nullable = false)
    @Comment("The first day of the period this summary refers to")
    var startDate: LocalDate? = null

    @Column(nullable = false)
    @Comment("The last day of the period this summary refers to")
    var endDate: LocalDate? = null

    @Column(length = 20, nullable = false)
    @Enumerated(EnumType.STRING)
    @Comment("The duration of the period this summary refers to")
    var duration: SummaryDuration? = null

    @Column(nullable = true)
    @Comment("The rank during this period")
    var viewRank: Int? = null

    @Column(nullable = false)
    @Comment("The total hours viewed during this period")
    var hoursViewed: Int? = null

    @Column(nullable = true)
    @Comment("The number of views during this period")
    var views: Int? = null

    @Column(nullable = true)
    @Comment("The number of cumulative weeks in top 10 list")
    var cumulativeWeeksInTop10: Int? = null

    @ManyToOne
    @Comment("The movie for this weekly summary")
    @JoinColumn(nullable = true, foreignKey = ForeignKey(name = "fk_view_summary_movie_id"))
    var movie: Movie? = null

    @ManyToOne
    @Comment("The season for this weekly summary")
    @JoinColumn(nullable = true, foreignKey = ForeignKey(name = "fk_view_summary_season_id"))
    var season: Season? = null
}
