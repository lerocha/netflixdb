package com.github.lerocha.netflixdb.entity

import jakarta.persistence.Column
import jakarta.persistence.EntityListeners
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.Comment
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.Instant

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("Primary unique identifier")
    var id: Long? = null

    @CreatedDate
    @Column(nullable = false)
    @Comment("Date when this record was created")
    var createdDate: Instant? = null

    @LastModifiedDate
    @Column(nullable = false)
    @Comment("Date when this record was last modified")
    var modifiedDate: Instant? = null
}
