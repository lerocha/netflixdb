# Repository layer (`repository/`)

Spring Data JPA interfaces; **only used from the batch job** (no custom `@Query` in this project).

## Interfaces

| Repository | Extends | Custom methods | Batch usage |
|------------|---------|----------------|-------------|
| `MovieRepository` | `JpaRepository<Movie, Long>` | `findByTitleAndRuntime` | Available for dedupe (populate uses in-memory sets) |
| `TvShowRepository` | `JpaRepository<TvShow, Long>` | `findByTitle` | `seasonProcessor` links seasons to existing shows |
| `SeasonRepository` | `JpaRepository<Season, Long>` | `findByTitleAndRuntime` | Same as movies |
| `ViewSummaryRepository` | `JpaRepository<ViewSummary, Long>` | `findAllByEndDate` | `verifyContentStep` |

## Query method examples (actual signatures)

```kotlin
// MovieRepository
fun findByTitleAndRuntime(title: String, runtime: Long): Movie?

// TvShowRepository
fun findByTitle(title: String): TvShow?

// ViewSummaryRepository
fun findAllByEndDate(endDate: LocalDate): List<ViewSummary>
```

Spring derives SQL from method names (`WHERE title = ? AND runtime = ?`, etc.).

## Export steps

`CreateNetflixDatabaseJobConfig.exportDataStep` calls `findAll()` via `RepositoryItemReaderBuilder` with sort `id ASC` and page size 100—no repository changes required.

Inherited `save` / `saveAll` persist populated entities in chunk writers.
