package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.TvShow
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** TV show titles are unique; used when linking seasons during import. */
@Repository
interface TvShowRepository : JpaRepository<TvShow, Long> {
    /** Matches normalized show title after [com.github.lerocha.netflixdb.dto.toTvShowTitle]. */
    fun findByTitle(title: String): TvShow?
}
