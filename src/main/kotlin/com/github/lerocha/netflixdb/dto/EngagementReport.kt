package com.github.lerocha.netflixdb.dto

import com.github.lerocha.netflixdb.entity.SummaryDuration
import java.time.LocalDate

enum class EngagementReport(
    val path: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val duration: SummaryDuration,
) {
    ENGAGEMENT_REPORT_2023_SECOND_HALF(
        path = "reports/What_We_Watched_A_Netflix_Engagement_Report_2023Jul-Dec.xlsx",
        startDate = LocalDate.parse("2023-07-01"),
        endDate = LocalDate.parse("2023-12-31"),
        duration = SummaryDuration.SEMI_ANNUALLY,
    ),
    ENGAGEMENT_REPORT_2024_FIRST_HALF(
        path = "reports/What_We_Watched_A_Netflix_Engagement_Report_2024Jan-Jun.xlsx",
        startDate = LocalDate.parse("2024-01-01"),
        endDate = LocalDate.parse("2024-06-30"),
        duration = SummaryDuration.SEMI_ANNUALLY,
    ),
}
