package com.github.lerocha.netflixdb.entity

import com.github.lerocha.netflixdb.dto.StreamingCategory
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import org.hibernate.annotations.Comment
import java.time.LocalDate

@Entity
@Table(
    indexes = [
        Index(name = "idx_show_title", columnList = "title", unique = true),
    ],
)
class Show : AbstractEntity() {
    @Column(length = 255, nullable = false)
    @Comment("The show title")
    var title: String? = null

    @Column(length = 255, nullable = false)
    @Comment("The show title in its original language")
    var originalTitle: String? = null

    @Enumerated(EnumType.STRING)
    @Comment("The category of this show, such as Films, TV, etc.")
    var category: StreamingCategory? = null

    @Column(nullable = false)
    @Comment("The total runtime in minutes")
    var runtime: Long? = null

    @Column(nullable = true)
    @Comment("Date when this title was released")
    var releaseDate: LocalDate? = null

    @Column(nullable = false)
    @Comment("A flag that indicates if this title is available outside US")
    var availableGlobally: Boolean? = false
}
