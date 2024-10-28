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
import java.util.UUID

@MappedSuperclass
@EntityListeners(AuditingEntityListener::class)
abstract class AbstractEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Comment("Primary unique identifier")
    var id: UUID? = null

    @CreatedDate
    @Column(nullable = false)
    @Comment("The date and time when this record was created")
    var createdDate: Instant? = null

    @LastModifiedDate
    @Column(nullable = false)
    @Comment("The date and time when this record was last modified")
    var modifiedDate: Instant? = null
}