package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.Comment

@Entity
@Table(
    indexes = [
        Index(name = "fk_show_id", columnList = "show_id", unique = false)
    ]
)
class Season : AbstractEntity() {
    @Column(length = 255, nullable = false)
    @Comment("The season's title")
    var title: String? = null

    @ManyToOne
    @Comment("The show that this season belongs to")
    var show: Show? = null
}
