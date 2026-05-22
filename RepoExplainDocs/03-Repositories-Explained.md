# Spring Data JPA Repositories Explained

## What is a Repository?

A **Repository** is an interface that provides methods to access database tables without writing SQL.

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    // JpaRepository automatically provides:
    // - findById(id): Optional<Movie>
    // - findAll(): List<Movie>
    // - save(movie): Movie
    // - delete(movie): void
    // - count(): Long
    // etc.
}
```

## Auto-Generated Methods

### Basic CRUD

```kotlin
// CREATE
val movie = Movie(id = 1, title = "Avatar")
movieRepository.save(movie)

// READ
val movie: Optional<Movie> = movieRepository.findById(1)
val allMovies: List<Movie> = movieRepository.findAll()

// UPDATE
val updated = existing.copy(title = "Avatar 2")
movieRepository.save(updated)

// DELETE
movieRepository.deleteById(1)
movieRepository.delete(movie)
```

## Query Methods by Name Convention

Spring Data JPA **generates SQL automatically** based on method names:

```kotlin
// Pattern: find + <Entities> + By + <Conditions>

// Simple equality
fun findByLocale(locale: String): List<Movie>
// SQL: SELECT * FROM movie WHERE locale = ?

// Multiple conditions (AND)
fun findByTitleAndRuntime(title: String, runtime: Int): List<Movie>
// SQL: SELECT * FROM movie WHERE title = ? AND runtime = ?

// Between
fun findByRuntimeBetween(min: Int, max: Int): List<Movie>
// SQL: SELECT * FROM movie WHERE runtime BETWEEN ? AND ?

// Contains (case-insensitive)
fun findByTitleContainingIgnoreCase(fragment: String): List<Movie>
// SQL: WHERE LOWER(title) LIKE LOWER(%fragment%)

// In list
fun findByLocaleIn(locales: List<String>): List<Movie>
// SQL: WHERE locale IN (?, ?, ...)

// Is null
fun findByEndYearIsNull(): List<TvShow>
// SQL: WHERE end_year IS NULL

// Top 10
fun findTop10ByLocaleOrderByReleaseDateDesc(locale: String): List<Movie>
// SQL: WHERE locale = ? ORDER BY release_date DESC LIMIT 10
```

## NetflixDB Repositories

### MovieRepository

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    fun findByLocale(locale: String): List<Movie>
    fun findByReleaseDateBetween(start: LocalDate, end: LocalDate): List<Movie>
    fun findByTitleContainingIgnoreCase(title: String): List<Movie>
}
```

### TvShowRepository

```kotlin
@Repository
interface TvShowRepository : JpaRepository<TvShow, Long> {
    fun findByTitleContainingIgnoreCase(title: String): List<TvShow>
    fun findByEndYearIsNull(): List<TvShow>  // Still airing
}
```

### SeasonRepository

```kotlin
@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    fun findByTvShowId(tvShowId: Long): List<Season>
    fun findBySeasonNumber(seasonNumber: Int): List<Season>
}
```

### GenreRepository

```kotlin
@Repository
interface GenreRepository : JpaRepository<Genre, Long> {
    fun findByName(name: String): Genre?
    fun findByNameIn(names: List<String>): List<Genre>
}
```

### ViewSummaryRepository

```kotlin
@Repository
interface ViewSummaryRepository : JpaRepository<ViewSummary, Long> {
    fun findByDurationAndEndDate(duration: Duration, endDate: LocalDate): List<ViewSummary>
    fun findTop10ByDurationAndEndDateOrderByViewRankAsc(
        duration: Duration,
        endDate: LocalDate
    ): List<ViewSummary>
}
```

## Custom Queries with @Query

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // JPQL Query (Object-Oriented)
    @Query("SELECT m FROM Movie m WHERE m.locale = :locale")
    fun findByLocale(@Param("locale") locale: String): List<Movie>
    
    // With join (eager loading)
    @Query("""
        SELECT DISTINCT m FROM Movie m 
        LEFT JOIN FETCH m.genres 
        WHERE m.releaseDate >= :date
    """)
    fun findRecentWithGenres(@Param("date") date: LocalDate): List<Movie>
    
    // Native SQL (when JPQL not enough)
    @Query("SELECT * FROM movie WHERE locale = ?1", nativeQuery = true)
    fun findByLocaleNative(locale: String): List<Movie>
}
```

## Pagination

```kotlin
// Get page 0 (first page) with 20 items per page
val pageNumber = 0
val pageSize = 20
val sort = Sort.by("releaseDate").descending()
val pageable = PageRequest.of(pageNumber, pageSize, sort)

val page: Page<Movie> = movieRepository.findAll(pageable)
println("Total: ${page.totalElements}")
println("Pages: ${page.totalPages}")
println("Content: ${page.content}")
```

## Performance Optimization

### Problem: N+1 Query Problem

```kotlin
// ❌ BAD: Triggers multiple queries
val movies = movieRepository.findAll()
movies.forEach { 
    println(it.genres)  // Separate query per movie!
}

// ✅ GOOD: Single query with join
@Query("""
    SELECT DISTINCT m FROM Movie m 
    LEFT JOIN FETCH m.genres
""")
fun findAllWithGenres(): List<Movie>
```

### Problem: Loading Too Much Data

```kotlin
// ❌ BAD: Load 1000 movies, use only 10
val allMovies = movieRepository.findAll()
val first10 = allMovies.take(10)

// ✅ GOOD: Load only 10
val first10 = movieRepository.findAll(PageRequest.of(0, 10)).content
```

## Best Practices

✅ Use naming conventions for simple queries
✅ Use @Query for complex queries
✅ Use pagination for large datasets
✅ Use fetch joins to avoid N+1 problems
✅ Use readOnly = true for read-only operations

❌ Don't overuse EAGER loading
❌ Don't load entire datasets
❌ Don't forget @Param annotations
❌ Don't mix too much logic in queries
