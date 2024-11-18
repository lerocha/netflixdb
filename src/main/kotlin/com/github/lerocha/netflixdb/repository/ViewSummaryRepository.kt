package com.github.lerocha.netflixdb.repository

import com.github.lerocha.netflixdb.entity.ViewSummary
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ViewSummaryRepository : JpaRepository<ViewSummary, Long>
