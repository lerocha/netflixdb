package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.WeeklySummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface WeeklySummaryRepository : JpaRepository<WeeklySummary, UUID>
