package com.github.lerocha.netflixdb.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Index
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate
import java.util.Locale

@Entity
@Table(
    indexes = [
        Index(name = "idx_movie_title", columnList = "title", unique = true),
    ],
)
class Movie : AbstractEntity() {
    @Column(length = 255, nullable = false)
    @Comment("The movie title")
    var title: String? = null

    @Column(length = 255, nullable = false)
    @Comment("The movie title in its original language")
    var originalTitle: String? = null

    @Column(nullable = true)
    @Comment("The total runtime in minutes")
    var runtime: Long? = null

    @Column(nullable = true)
    @Comment("Date when this title was released")
    var releaseDate: LocalDate? = null

    @Column(nullable = true)
    @Comment("A flag that indicates if this title is available outside US")
    var availableGlobally: Boolean? = false

    @Column(length = 10, nullable = true)
    @Comment("The original language of the movie in ISO 639 language code")
    var language: Locale? = null

    @OneToMany(mappedBy = "movie", fetch = FetchType.LAZY, cascade = [(CascadeType.ALL)])
    var viewSummaries: MutableList<ViewSummary> = mutableListOf()
}
