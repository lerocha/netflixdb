package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.TvShow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** TV show titles are unique; used when linking seasons during import. */
@Repository
interface TvShowRepository : JpaRepository<TvShow, Long> {
    fun findByTitle(title: String): TvShow?
}
