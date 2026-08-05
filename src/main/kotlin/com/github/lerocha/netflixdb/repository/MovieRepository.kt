package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.Movie
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/** Movies are deduplicated in batch by title and runtime minutes. */
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    fun findByTitleAndRuntime(
        title: String,
        runtime: Long,
    ): Movie?
}
