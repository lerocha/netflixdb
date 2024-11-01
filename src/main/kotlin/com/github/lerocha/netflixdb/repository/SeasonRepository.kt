package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.Season
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface SeasonRepository : JpaRepository<Season, UUID> {
    fun findByTitle(title: String): Season?
}
