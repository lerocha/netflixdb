package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.TvShow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface TvShowRepository : JpaRepository<TvShow, UUID> {
    fun findByTitle(title: String): TvShow?
}
