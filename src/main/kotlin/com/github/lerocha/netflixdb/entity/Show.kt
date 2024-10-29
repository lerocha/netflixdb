package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import org.hibernate.annotations.Comment
import java.math.BigDecimal

@Entity
class Show : AbstractEntity() {
    @Column(length = 255, nullable = false)
    @Comment("The show title")
    var title: String? = null

    @Column(length = 100, nullable = false)
    @Comment("The category of this show, such as Films, TV, etc.")
    var category: String? = null

    @Column(nullable = false)
    var runtime: BigDecimal? = null
}
