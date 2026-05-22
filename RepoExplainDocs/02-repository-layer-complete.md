# NetflixDB Repository Layer - Complete Documentation

**Spring Data JPA, Query Methods, Custom Queries, and Data Access Patterns**

---

## Overview

The repository layer provides data access abstraction. Spring Data JPA automatically implements CRUD operations and custom query methods based on naming conventions.

**Location:** `src/main/kotlin/com/github/lerocha/netflixdb/repository`

---

## 1. Spring Data JPA Basics

### What Is a Repository?

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    // JpaRepository provides:
    // - findById(id): Optional<Movie>
    // - findAll(): List<Movie>
    // - save(movie): Movie
    // - delete(movie): void
    // - count(): Long
    // - exists(id): Boolean
    // etc.
}
```

**Inheritance Hierarchy:**
```
Repository (marker interface)
    ↓
CrudRepository (CRUD operations)
    ↓
PagingAndSortingRepository (pagination)
    ↓
JpaRepository (batch operations, flush, etc.)
```

---

## 2. Basic CRUD Operations (Automatic)

```kotlin
// All these are provided automatically by JpaRepository

// CREATE
val movie = Movie(id = 1, title = "Avatar")
movieRepository.save(movie)  // Returns saved entity

// READ
val movie: Optional<Movie> = movieRepository.findById(1)
val allMovies: List<Movie> = movieRepository.findAll()

// UPDATE
val existing = movieRepository.findById(1).orElseThrow()
val updated = existing.copy(title = "Avatar 2")
movieRepository.save(updated)

// DELETE
movieRepository.deleteById(1)
movieRepository.delete(movie)
movieRepository.deleteAll()

// COUNT
val count: Long = movieRepository.count()

// EXISTS
val exists: Boolean = movieRepository.existsById(1)
```

---

## 3. Query Methods by Naming Convention

Spring Data JPA generates queries automatically based on method names:

### Pattern: `find + <Entities> + By + <Conditions>`

```kotlin
// Simple equality
fun findByTitle(title: String): List<Movie>
// Generated SQL: SELECT * FROM movie WHERE title = ?

// Multiple conditions (AND)
fun findByTitleAndRuntime(title: String, runtime: Int): List<Movie>
// Generated SQL: SELECT * FROM movie WHERE title = ? AND runtime = ?

// OR condition
fun findByTitleOrOriginalTitle(title: String, original: String): List<Movie>
// Generated SQL: SELECT * FROM movie WHERE title = ? OR original_title = ?
```

### Comparison Operators

```kotlin
// Greater than
fun findByRuntimeGreaterThan(runtime: Int): List<Movie>
// SQL: WHERE runtime > ?

// Greater than or equal
fun findByRuntimeGreaterThanEqual(runtime: Int): List<Movie>
// SQL: WHERE runtime >= ?

// Less than
fun findByReleaseDateLessThan(date: LocalDate): List<Movie>
// SQL: WHERE release_date < ?

// Between
fun findByRuntimeBetween(min: Int, max: Int): List<Movie>
// SQL: WHERE runtime BETWEEN ? AND ?

// In (multiple values)
fun findByLocaleIn(locales: List<String>): List<Movie>
// SQL: WHERE locale IN (?, ?, ...)

// Not equal
fun findByLocaleIsNot(locale: String): List<Movie>
// SQL: WHERE locale != ?

// Is null
fun findByEndYearIsNull(): List<TvShow>
// SQL: WHERE end_year IS NULL

// Is not null
fun findByEndYearIsNotNull(): List<TvShow>
// SQL: WHERE end_year IS NOT NULL
```

### String Operations

```kotlin
// Contains (case-sensitive)
fun findByTitleContaining(fragment: String): List<Movie>
// SQL: WHERE title LIKE %fragment% (with escaping)

// Contains (case-insensitive)
fun findByTitleContainingIgnoreCase(fragment: String): List<Movie>
// SQL: WHERE LOWER(title) LIKE LOWER(%fragment%)

// Starts with
fun findByTitleStartingWith(prefix: String): List<Movie>
// SQL: WHERE title LIKE prefix%

// Ends with
fun findByTitleEndingWith(suffix: String): List<Movie>
// SQL: WHERE title LIKE %suffix

// Regex (database-dependent)
fun findByTitleRegex(pattern: String): List<Movie>
// SQL: WHERE title REGEXP pattern (MySQL)
```

### Sorting and Limiting

```kotlin
// Sorted
fun findByLocaleOrderByReleaseDateDesc(locale: String): List<Movie>
// SQL: WHERE locale = ? ORDER BY release_date DESC

// Multiple sort fields
fun findAllByOrderByReleaseDateDescTitleAsc(): List<Movie>
// SQL: ORDER BY release_date DESC, title ASC

// Limit/Top
fun findTop10ByLocaleOrderByViewsDesc(locale: String): List<Movie>
// SQL: WHERE locale = ? ORDER BY views DESC LIMIT 10

// First
fun findFirstByLocaleOrderByReleaseDateDesc(locale: String): Movie?
// SQL: WHERE locale = ? ORDER BY release_date DESC LIMIT 1
```

---

## 4. @Query Annotation (Custom JPQL)

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Simple JPQL query
    @Query("SELECT m FROM Movie m WHERE m.locale = ?1")
    fun findMoviesByLocale(locale: String): List<Movie>
    
    // Named parameters (clearer)
    @Query("SELECT m FROM Movie m WHERE m.locale = :locale AND m.runtime > :minRuntime")
    fun findMoviesByLocaleAndMinRuntime(
        @Param("locale") locale: String,
        @Param("minRuntime") minRuntime: Int
    ): List<Movie>
    
    // Count query
    @Query("SELECT COUNT(m) FROM Movie m WHERE m.isAvailableGlobally = true")
    fun countGloballyAvailableMovies(): Long
    
    // Exists query
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM Movie m WHERE m.title = :title")
    fun existsByTitle(@Param("title") title: String): Boolean
    
    // Join query (accessing related entities)
    @Query("""
        SELECT m FROM Movie m 
        JOIN m.genres g 
        WHERE g.name = :genreName
    """)
    fun findMoviesByGenre(@Param("genreName") genreName: String): List<Movie>
    
    // Aggregation queries
    @Query("SELECT m, COUNT(v) as viewCount FROM Movie m LEFT JOIN ViewSummary v ON m.id = v.movie.id GROUP BY m")
    fun findMoviesWithViewCount(): List<Any>
}
```

### JPQL vs SQL

```kotlin
// JPQL (Object-Oriented, preferred)
@Query("SELECT m FROM Movie m WHERE m.locale = :locale")
// References entity class names and fields

// Native SQL (last resort)
@Query("SELECT * FROM movie WHERE locale = :locale", nativeQuery = true)
// Raw SQL, less portable, database-specific
```

---

## 5. NetflixDB Repository Interfaces

### MovieRepository

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Named query method (convention-based)
    fun findByLocale(locale: String): List<Movie>
    
    // Find between dates
    fun findByReleaseDateBetween(start: LocalDate, end: LocalDate): List<Movie>
    
    // Find with contains (case-insensitive)
    fun findByTitleContainingIgnoreCase(title: String): List<Movie>
    
    // Top 10 movies by locale, ordered by rating
    fun findTop10ByLocaleOrderByIdDesc(locale: String): List<Movie>
    
    // Custom JPQL query
    @Query("""
        SELECT m FROM Movie m 
        WHERE m.releaseDate >= :date 
        AND m.isAvailableGlobally = true
        ORDER BY m.releaseDate DESC
    """)
    fun findRecentGlobalMovies(@Param("date") date: LocalDate): List<Movie>
}
```

### TvShowRepository

```kotlin
@Repository
interface TvShowRepository : JpaRepository<TvShow, Long> {
    
    // Find by title (case-insensitive)
    fun findByTitleContainingIgnoreCase(title: String): List<TvShow>
    
    // Find between years
    fun findByStartYearBetween(startYear: Int, endYear: Int): List<TvShow>
    
    // Find currently airing (endYear is null)
    fun findByEndYearIsNull(): List<TvShow>
    
    // Find with custom query
    @Query("""
        SELECT DISTINCT t FROM TvShow t 
        JOIN t.seasons s 
        WHERE s.releaseDate >= :date
        ORDER BY t.startYear DESC
    """)
    fun findShowsWithRecentSeasons(@Param("date") date: LocalDate): List<TvShow>
}
```

### SeasonRepository

```kotlin
@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    
    // Find seasons by TV show
    fun findByTvShowId(tvShowId: Long): List<Season>
    
    // Find seasons by season number
    fun findBySeasonNumber(seasonNumber: Int): List<Season>
    
    // Find seasons released after date
    fun findByReleaseDateAfterOrderByReleaseDateAsc(date: LocalDate): List<Season>
}
```

### GenreRepository

```kotlin
@Repository
interface GenreRepository : JpaRepository<Genre, Long> {
    
    // Find by name
    fun findByName(name: String): Genre?
    
    // Find multiple by names
    fun findByNameIn(names: List<String>): List<Genre>
    
    // Find by name (case-insensitive)
    fun findByNameContainingIgnoreCase(name: String): List<Genre>
}
```

### ViewSummaryRepository

```kotlin
@Repository
interface ViewSummaryRepository : JpaRepository<ViewSummary, Long> {
    
    // Find by duration and end date (for reports)
    fun findByDurationAndEndDate(duration: Duration, endDate: LocalDate): List<ViewSummary>
    
    // Top 10 movies for period
    fun findTop10ByDurationAndEndDateOrderByViewRankAsc(
        duration: Duration,
        endDate: LocalDate
    ): List<ViewSummary>
    
    // Custom aggregation query
    @Query("""
        SELECT v FROM ViewSummary v 
        WHERE v.duration = :duration 
        AND v.endDate = :endDate
        ORDER BY v.hoursViewed DESC
    """)
    fun findTopMoviesByEngagement(
        @Param("duration") duration: Duration,
        @Param("endDate") endDate: LocalDate
    ): List<ViewSummary>
}
```

---

## 6. Pagination and Sorting

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Paginated query
    fun findAll(pageable: Pageable): Page<Movie>
    
    // Paginated with condition
    fun findByLocale(locale: String, pageable: Pageable): Page<Movie>
}

// Usage
val pageNumber = 0  // First page (0-indexed)
val pageSize = 20
val sort = Sort.by("releaseDate").descending()
val pageable = PageRequest.of(pageNumber, pageSize, sort)

val page: Page<Movie> = movieRepository.findAll(pageable)
println("Total movies: ${page.totalElements}")
println("Total pages: ${page.totalPages}")
println("Current page content: ${page.content}")
println("Is last page: ${page.isLast}")
```

---

## 7. Delete Operations

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Delete by property (generates WHERE clause)
    fun deleteByLocale(locale: String): Long  // Returns count deleted
    
    // Delete all by multiple conditions
    fun deleteByReleaseDateBefore(date: LocalDate): Long
}

// Usage
val deletedCount = movieRepository.deleteByLocale("fr")
println("Deleted $deletedCount movies")
```

---

## 8. Batch Operations

```kotlin
// Save multiple (batch insert)
val movies = listOf(
    Movie(id = 1, title = "Avatar"),
    Movie(id = 2, title = "Avatar 2"),
    Movie(id = 3, title = "Avatar 3")
)
movieRepository.saveAll(movies)

// Delete multiple
movieRepository.deleteAllInBatch(movies)

// Delete by IDs
movieRepository.deleteAllById(listOf(1L, 2L, 3L))
```

---

## 9. Custom Repository Implementation

```kotlin
// Interface with custom methods
interface MovieRepositoryCustom {
    fun findMoviesWithGenres(genres: List<String>): List<Movie>
    fun findMoviesByEngagement(minViews: Long): List<Movie>
}

// Implementation
@Component
class MovieRepositoryCustomImpl(
    private val entityManager: EntityManager
) : MovieRepositoryCustom {
    
    override fun findMoviesWithGenres(genres: List<String>): List<Movie> {
        val criteriaBuilder = entityManager.criteriaBuilder
        val query = criteriaBuilder.createQuery(Movie::class.java)
        val root = query.from(Movie::class.java)
        val genreJoin = root.join<Movie, Genre>("genres")
        
        query.where(genreJoin.get<String>("name").`in`(genres))
        
        return entityManager.createQuery(query).resultList
    }
    
    override fun findMoviesByEngagement(minViews: Long): List<Movie> {
        return entityManager.createQuery("""
            SELECT m FROM Movie m
            LEFT JOIN ViewSummary v ON m.id = v.movie.id
            WHERE v.views >= :minViews
        """.trimIndent(), Movie::class.java)
            .setParameter("minViews", minViews)
            .resultList
    }
}

// Combined repository extending both
@Repository
interface MovieRepository : JpaRepository<Movie, Long>, MovieRepositoryCustom
```

---

## 10. @Query Annotations Reference

```kotlin
@Query("SELECT m FROM Movie m WHERE m.locale = ?1")
fun findByLocale(locale: String): List<Movie>
// ?1, ?2, ?3 = positional parameters (1-indexed)

@Query("SELECT m FROM Movie m WHERE m.locale = :locale")
fun findByLocale(@Param("locale") locale: String): List<Movie>
// Named parameters (clearer intent)

@Query(value = "SELECT * FROM movie WHERE locale = ?1", nativeQuery = true)
fun findByLocaleNative(locale: String): List<Movie>
// Native SQL (database-specific, less portable)

@Query("SELECT NEW map(m.id as id, m.title as title) FROM Movie m")
fun findAsMap(): List<Map<String, Any>>
// Project to different types

@Query("DELETE FROM Movie m WHERE m.locale = ?1")
@Modifying  // Required for DELETE/UPDATE queries
fun deleteByLocale(locale: String): Int
// Returns count of deleted records

@Query("UPDATE Movie m SET m.title = :title WHERE m.id = :id")
@Modifying(clearAutomatically = true)  // Clear cache after update
fun updateTitle(@Param("id") id: Long, @Param("title") title: String): Int
```

---

## 11. Projection (Selecting Specific Columns)

```kotlin
// DTO for projection
data class MovieSummary(
    val id: Long,
    val title: String,
    val runtime: Int
)

// Interface-based projection
interface MovieBasic {
    val id: Long
    val title: String
}

@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Project to DTO
    @Query("SELECT new com.github.lerocha.netflixdb.dto.MovieSummary(m.id, m.title, m.runtime) FROM Movie m")
    fun findAllSummaries(): List<MovieSummary>
    
    // Project to interface
    fun <T> findAllBy(type: Class<T>): List<T>
    
    // Usage:
    // val summaries = movieRepository.findAllSummaries()
    // val basics = movieRepository.findAllBy(MovieBasic::class.java)
}
```

---

## 12. Transaction Control

```kotlin
@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    
    // Read-only transaction (optimization)
    @Transactional(readOnly = true)
    fun findByLocale(locale: String): List<Movie>
    
    // No transaction (dangerous for writes)
    @Transactional(propagation = Propagation.NEVER)
    fun findById(id: Long): Optional<Movie>
}

// Usage in service
@Service
class MovieService(private val movieRepository: MovieRepository) {
    
    // Transaction started implicitly by Spring
    @Transactional
    fun saveMovie(movie: Movie) {
        movieRepository.save(movie)
        // Auto-commit on method exit
    }
    
    // Requires existing transaction
    @Transactional(propagation = Propagation.MANDATORY)
    fun updateMovie(movie: Movie) {
        movieRepository.save(movie)
    }
}
```

---

## 13. Query Execution Strategies

```kotlin
// STRATEGY 1: Query method (lazy loading)
val movies = movieRepository.findByLocale("en")  // SQL executed here
// Accesses movies.forEach { it.genres }         // Additional SQL for genres (N+1)

// STRATEGY 2: Fetch join (eagerly load relations)
@Query("""
    SELECT DISTINCT m FROM Movie m 
    LEFT JOIN FETCH m.genres 
    WHERE m.locale = :locale
""")
fun findByLocaleWithGenres(@Param("locale") locale: String): List<Movie>
// Single SQL with JOIN, all genres loaded

// STRATEGY 3: Entity graph (alternative to fetch join)
@EntityGraph(attributePaths = ["genres"])
fun findByLocale(locale: String): List<Movie>
// Similar to fetch join but more declarative
```

---

## 14. Common Repository Methods Reference

| Method | SQL | Returns |
|--------|-----|---------|
| `findAll()` | SELECT * | List<T> |
| `findAllById(ids)` | WHERE id IN (...) | List<T> |
| `findById(id)` | WHERE id = ? | Optional<T> |
| `findBy<Property>(value)` | WHERE property = ? | List<T> |
| `findBy<Property>Between(a,b)` | WHERE property BETWEEN ? AND ? | List<T> |
| `findBy<Property>In(values)` | WHERE property IN (...) | List<T> |
| `findBy<Property>Like(pattern)` | WHERE property LIKE ? | List<T> |
| `findBy<Property>IsNull()` | WHERE property IS NULL | List<T> |
| `findTop10By<Property>` | LIMIT 10 | List<T> |
| `findFirstBy<Property>` | LIMIT 1 | T? |
| `countBy<Property>` | COUNT(*) | Long |
| `existsBy<Property>` | EXISTS | Boolean |
| `deleteBy<Property>` | DELETE | Long |
| `save(entity)` | INSERT/UPDATE | T |
| `saveAll(entities)` | INSERT/UPDATE batch | List<T> |
| `delete(entity)` | DELETE | void |
| `deleteAll(entities)` | DELETE batch | void |
| `deleteAllById(ids)` | DELETE batch | void |

---

## 15. Performance Optimization Patterns

### Problem: N+1 Query Problem
```kotlin
// ❌ BAD: Triggers N+1 queries
val movies = movieRepository.findAll()
movies.forEach { 
    println(it.genres)  // Separate query per movie!
}

// ✅ GOOD: Single query with JOIN
@Query("""
    SELECT DISTINCT m FROM Movie m 
    LEFT JOIN FETCH m.genres
""")
fun findAllWithGenres(): List<Movie>
```

### Problem: Unnecessary Data Loading
```kotlin
// ❌ BAD: Loads entire Movie objects for count
val count = movieRepository.findAll().size

// ✅ GOOD: Count at database level
val count = movieRepository.count()
```

### Problem: Large Result Sets
```kotlin
// ❌ BAD: Load all 1 million movies into memory
val allMovies = movieRepository.findAll()

// ✅ GOOD: Paginate
val page = movieRepository.findAll(PageRequest.of(0, 100))
```

---

## 16. Best Practices

✅ **DO:**
- Use naming conventions for simple queries (clearer intent)
- Use `@Query` for complex queries (avoid over-complicated method names)
- Use `readOnly = true` for read-only operations
- Paginate large results
- Use fetch joins to avoid N+1 problems
- Test queries with different load patterns

❌ **DON'T:**
- Leave N+1 queries in production
- Use `nativeQuery = true` unless necessary
- Perform business logic in repository layer
- Return entire entities if only few fields needed
- Forget index definitions on frequently queried columns

---

**Used in:** Spring Batch processors, business logic layer, SQL export
