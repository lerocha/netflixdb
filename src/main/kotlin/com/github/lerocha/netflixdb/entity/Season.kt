package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(name = "fk_season_show_id", columnList = "show_id", unique = false),
        Index(name = "fk_season_tv_show_id", columnList = "tv_show_id", unique = false),
    ],
)
class Season : AbstractEntity() {
    @Column(nullable = true)
    @Comment("The season number")
    var number: Int? = null

    @Column(length = 255, nullable = false)
    @Comment("The season title")
    var title: String? = null

    @Column(length = 255, nullable = false)
    @Comment("The season title in its original language")
    var originalTitle: String? = null

    @Column(nullable = false)
    @Comment("The total runtime in minutes")
    var runtime: Long? = null

    @Column(nullable = true)
    @Comment("Date when this title was released")
    var releaseDate: LocalDate? = null

    @ManyToOne
    @Comment("The TV show that this season belongs to")
    @JoinColumn(nullable = true, foreignKey = ForeignKey(name = "fk_season_tv_show_id"))
    var tvShow: TvShow? = null

    @OneToMany(mappedBy = "season", fetch = FetchType.LAZY)
    var episodes: MutableList<Episode> = mutableListOf()
}
