package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(name = "fk_show_id_season_id", columnList = "show_id, season_id", unique = false)
    ]
)
class WeeklySummary : AbstractEntity() {
    @Column(nullable = false)
    @Comment("The first day of the week this summary refers to")
    var week: LocalDate? = null

    @Column(nullable = false)
    @Comment("The weekly rank")
    var rank: Int? = null

    @Column(nullable = false)
    @Comment("The weekly hours viewed")
    var hoursViewed: Int? = null

    @Column(nullable = false)
    @Comment("The number of views during this week")
    var views: Int? = null

    @ManyToOne
    @Comment("The show for this weekly summary")
    var show: Show? = null

    @ManyToOne
    @Comment("The season for this weekly summary")
    var season: Season? = null
}