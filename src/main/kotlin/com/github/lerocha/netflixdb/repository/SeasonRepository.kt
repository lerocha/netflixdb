package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.Season
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    fun findByTitle(title: String): Season?
}
