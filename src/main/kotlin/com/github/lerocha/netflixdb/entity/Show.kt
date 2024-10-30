package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import org.hibernate.annotations.Comment
import java.math.BigDecimal
import java.time.LocalDate

@Entity
class Show : AbstractEntity() {
    @Column(length = 255, nullable = false)
    @Comment("The show title")
    var title: String? = null

    @Column(length = 255, nullable = false)
    @Comment("The show title in its original language")
    var originalTitle: String? = null

    @Column(length = 100, nullable = true)
    @Comment("The category of this show, such as Films, TV, etc.")
    var category: String? = null

    @Column(nullable = false)
    @Comment("Show total runtime in hours")
    var runtime: BigDecimal? = null

    @Column(nullable = true)
    @Comment("Date when this title was released")
    var releaseDate: LocalDate? = null

    @Column(nullable = false)
    @Comment("The total hours viewed")
    var hoursViewed: BigDecimal? = BigDecimal.ZERO

    @Column(nullable = false)
    @Comment("A flag that indicates if this title is available outside US")
    var availableGlobally: Boolean? = false
}
