package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.ViewSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

/** Supports post-import verification against the latest weekly report end date. */
@Repository
interface ViewSummaryRepository : JpaRepository<ViewSummary, Long> {
    fun findAllByEndDate(endDate: LocalDate): List<ViewSummary>
}
