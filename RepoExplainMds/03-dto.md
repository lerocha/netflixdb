# DTO layer (`dto/`)

Translates Excel rows into JPA entities. Keeps batch configuration free of field-by-field mapping noise.

## Files

| File | Role |
|------|------|
| `ReportSheetRow.kt` | Mutable row model + `toMovie` / `toSeason` / `toTvShow` / `toViewSummary` / `updateViewSummary` |
| `EngagementReport.kt` | Enum of classpath workbook + fixed `startDate` / `endDate` / `duration` |
| `StreamingCategory.kt` | `MOVIE` / `TV_SHOW` + `String.toCategory()` from sheet names |

## `ReportSheetRow` lifecycle

1. **Read** — `PoiItemReader` row mappers in `CreateNetflixDatabaseJobConfig` fill a `ReportSheetRow`.
2. **Stage** — `accumulateReportRows()` groups rows by `(title, runtime)` into in-memory maps.
3. **Materialize** — `movieProcessor` / `seasonProcessor` fold `Set<ReportSheetRow>` into one entity, calling `updateViewSummary` per row.

## Parsing rules (non-obvious)

| Rule | Where |
|------|--------|
| Title `English // Original` | `parseEngagementTitles()` → `title` + `originalTitle` |
| TV show name strips `: Season N` | `toTvShowTitle()` |
| Season number from digits after last `:` | `extractSeasonNumber()` |
| Category from sheet name or column | `toCategory()` — “film” → MOVIE, “tv” → TV_SHOW |
| Reproducible timestamps in SQL | `now()` fixed to `2024-01-01` UTC |

## `updateViewSummary` merge key

Existing summary matched when **same parent** (movie or season), same `duration`, same `startDate`. Incoming rank/views/hours update the existing row; otherwise a new `ViewSummary` is appended.

## `EngagementReport` windows

| Constant | Period | `SummaryDuration` |
|----------|--------|-------------------|
| `ENGAGEMENT_REPORT_2024_01_01` | 2024-01-01 … 2024-06-30 | `SEMI_ANNUALLY` |
| `ENGAGEMENT_REPORT_2023_07_01` | 2023-07-01 … 2023-12-31 | `SEMI_ANNUALLY` |

Weekly top-10 rows set `duration = WEEKLY` and derive `startDate` as six days before the `week` Sunday column.
