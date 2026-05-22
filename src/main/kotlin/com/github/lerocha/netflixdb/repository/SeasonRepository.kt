package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.Season
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Seasons are deduplicated in batch by title and runtime minutes. */
@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    fun findByTitleAndRuntime(
        title: String,
        runtime: Long,
    ): Season?
}
