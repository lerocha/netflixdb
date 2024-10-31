package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(name = "fk_view_summary_show_id", columnList = "show_id", unique = false),
        Index(name = "fk_view_summary_season_id", columnList = "season_id", unique = false),
    ],
)
class ViewSummary : AbstractEntity() {
    @Column(nullable = false)
    @Comment("The first day of the period this summary refers to")
    var startDate: LocalDate? = null

    @Column(nullable = false)
    @Comment("The last day of the period this summary refers to")
    var endDate: LocalDate? = null

    @Column(nullable = false)
    @Comment("The rank during this period")
    var rank: Int? = null

    @Column(nullable = false)
    @Comment("The total hours viewed during this period")
    var hoursViewed: Int? = null

    @Column(nullable = false)
    @Comment("The number of views during this period")
    var views: Int? = null

    @ManyToOne
    @Comment("The show for this weekly summary")
    @JoinColumn(nullable = false, foreignKey = ForeignKey(name = "fk_view_summary_show_id"))
    var show: Show? = null

    @ManyToOne
    @Comment("The season for this weekly summary")
    @JoinColumn(nullable = true, foreignKey = ForeignKey(name = "fk_view_summary_season_id"))
    var season: Season? = null
}
