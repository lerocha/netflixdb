package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.math.BigDecimal
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(name = "fk_episode_season_id", columnList = "season_id", unique = false),
    ],
)
class Episode : AbstractEntity() {
    @Column(nullable = false)
    @Comment("The episode number")
    var episodeNumber: Int? = null

    @Column(length = 255, nullable = false)
    @Comment("The episode title")
    var title: String? = null

    @Column(length = 255, nullable = false)
    @Comment("The episode title in its original language")
    var originalTitle: String? = null

    @Column(nullable = false)
    @Comment("The total runtime in minutes")
    var runtime: BigDecimal? = null

    @Column(nullable = true)
    @Comment("Date when this title was released")
    var releaseDate: LocalDate? = null

    @ManyToOne
    @Comment("The season that this episode belongs to")
    @JoinColumn(nullable = false, foreignKey = ForeignKey(name = "fk_episode_season_id"))
    var season: Season? = null
}
