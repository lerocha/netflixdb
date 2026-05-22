# NetflixDB Entity Layer - Complete Documentation

**JPA/Hibernate Entities, Annotations, Relationships, and Schema Design**

---

## Overview

The entity layer defines the database schema using JPA annotations. Hibernate auto-generates tables when the application starts. All entities are Kotlin data classes for immutability and conciseness.

**Location:** `src/main/kotlin/com/github/lerocha/netflixdb/entity`

---

## 1. Core Annotations Reference

### @Entity
```kotlin
@Entity
class Movie { ... }
```
**Purpose:** Marks class as a JPA entity (mapped to database table)
**Effect:** Hibernate creates a table for this class
**Required:** Yes, for all domain objects

---

### @Table
```kotlin
@Entity
@Table(name = "movie")
class Movie { ... }
```
**Purpose:** Customizes table name and schema
**Attributes:**
- `name`: Table name (default = class name lowercase)
- `schema`: Database schema (if multi-schema DB)
- `uniqueConstraints`: Add unique constraints
- `indexes`: Define database indexes

**Example with constraints:**
```kotlin
@Table(
    name = "movie",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["id"])
    ],
    indexes = [
        Index(name = "idx_movie_locale", columnList = "locale"),
        Index(name = "idx_movie_release_date", columnList = "release_date")
    ]
)
```

---

### @Id
```kotlin
@Entity
class Movie {
    @Id
    val id: Long  // Primary key
}
```
**Purpose:** Marks field as primary key
**Required:** Exactly one per entity
**Type:** Usually Long, String, or UUID

---

### @Column
```kotlin
@Column(name = "movie_title", nullable = false, length = 255)
val title: String
```
**Purpose:** Customizes column properties
**Key Attributes:**
- `name`: Column name (default = field name)
- `nullable`: Allow NULL values (default = true)
- `length`: String max length (VARCHAR size)
- `unique`: Unique constraint
- `precision`: Decimal precision (for numbers)
- `scale`: Decimal places (for numbers)

**Examples:**
```kotlin
@Column(nullable = false)  // NOT NULL constraint
val title: String

@Column(length = 1000)  // VARCHAR(1000)
val description: String

@Column(unique = true)  // UNIQUE constraint
val imdbId: String

@Column(precision = 10, scale = 2)  // DECIMAL(10, 2) for money
val budget: BigDecimal
```

---

### @Temporal (for Date/Time)
```kotlin
@Temporal(TemporalType.DATE)
val releaseDate: LocalDate

@Temporal(TemporalType.TIMESTAMP)
val createdAt: LocalDateTime
```
**Purpose:** Specifies how date/time is stored in DB
**Values:**
- `DATE`: Only date (YYYY-MM-DD)
- `TIME`: Only time (HH:MM:SS)
- `TIMESTAMP`: Both date and time

**Modern Kotlin approach (preferred):**
```kotlin
// Hibernate 5.3+ auto-detects these types
val releaseDate: LocalDate  // Stored as DATE
val createdAt: LocalDateTime  // Stored as TIMESTAMP
```

---

### @Enumerated
```kotlin
@Enumerated(EnumType.STRING)
val duration: Duration  // Stored as string "WEEKLY" or "SEMI_ANNUALLY"

@Enumerated(EnumType.ORDINAL)
val status: Status  // Stored as integer (0, 1, 2...)
```
**Purpose:** Defines how enums are persisted
**EnumType.STRING:** Better (human-readable, safe for refactoring)
**EnumType.ORDINAL:** Faster but fragile (breaks if enum order changes)

---

### @Transient
```kotlin
@Entity
class Movie {
    @Transient
    val derivedValue: String  // NOT stored in DB
        get() = "$title ($releaseDate)"
}
```
**Purpose:** Field is NOT stored in database
**Use Case:** Calculated properties, helper methods

---

## 2. Relationship Annotations

### @ManyToOne (Many movies have one genre)

```kotlin
@Entity
class Movie {
    @ManyToOne
    @JoinColumn(name = "genre_id")  // Foreign key column name
    val genre: Genre
}
```

**Generated SQL:**
```sql
CREATE TABLE movie (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255),
    genre_id BIGINT,
    FOREIGN KEY (genre_id) REFERENCES genre(id)
);
```

**Semantics:**
```
Many movies → One genre
Movie.genre → Single Genre object
```

**Fetch strategies:**
```kotlin
@ManyToOne(fetch = FetchType.EAGER)  // Load immediately with Movie
val genre: Genre

@ManyToOne(fetch = FetchType.LAZY)   // Load only when accessed
val genre: Genre
```

---

### @OneToMany (One TV show has many seasons)

```kotlin
@Entity
class TvShow {
    @OneToMany(
        mappedBy = "tvShow",           // Field in Season that references back
        cascade = [CascadeType.ALL],   // Delete seasons when show deleted
        orphanRemoval = true           // Remove seasons not in list
    )
    val seasons: MutableSet<Season> = mutableSetOf()
}

@Entity
class Season {
    @ManyToOne
    @JoinColumn(name = "tv_show_id")
    val tvShow: TvShow
}
```

**Generated SQL:**
```sql
CREATE TABLE season (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255),
    tv_show_id BIGINT,
    FOREIGN KEY (tv_show_id) REFERENCES tv_show(id)
);
-- Note: No join table, FK is in season table (child side)
```

**Behavior:**
```kotlin
val show = TvShow(id = 1, title = "Stranger Things")
show.seasons.add(Season(id = 1, seasonNumber = 1, tvShow = show))
sessionFactory.save(show)
// Automatically saves all seasons (CascadeType.ALL)

show.seasons.clear()
sessionFactory.save(show)
// Automatically deletes all seasons (orphanRemoval = true)
```

---

### @ManyToMany (Movies have many genres, genres have many movies)

```kotlin
@Entity
class Movie {
    @ManyToMany
    @JoinTable(
        name = "movie_genre",                          // Join table name
        joinColumns = [JoinColumn(name = "movie_id")],     // FK to Movie
        inverseJoinColumns = [JoinColumn(name = "genre_id")]  // FK to Genre
    )
    val genres: MutableSet<Genre> = mutableSetOf()
}

@Entity
class Genre {
    @ManyToMany(mappedBy = "genres")  // Owning side is Movie
    val movies: MutableSet<Movie> = mutableSetOf()
}
```

**Generated SQL:**
```sql
-- Association table
CREATE TABLE movie_genre (
    movie_id BIGINT,
    genre_id BIGINT,
    PRIMARY KEY (movie_id, genre_id),
    FOREIGN KEY (movie_id) REFERENCES movie(id),
    FOREIGN KEY (genre_id) REFERENCES genre(id)
);
```

**Behavior:**
```kotlin
val movie = Movie(id = 1)
val action = Genre(id = 1, name = "Action")
val drama = Genre(id = 2, name = "Drama")

movie.genres.add(action)
movie.genres.add(drama)
session.save(movie)
// Inserts into movie_genre table:
// (movie_id=1, genre_id=1)
// (movie_id=1, genre_id=2)
```

---

### @OneToOne (One movie has one review)

```kotlin
@Entity
class Movie {
    @OneToOne
    @JoinColumn(name = "review_id")
    val review: Review?
}

@Entity
class Review {
    @OneToOne(mappedBy = "review")  // Bidirectional
    val movie: Movie
}
```

---

## 3. Movie Entity

```kotlin
@Entity
@Table(name = "movie")
data class Movie(
    @Id
    val id: Long,  // IMDB ID
    
    @Column(nullable = false)
    val title: String,  // Display title
    
    @Column(nullable = false)
    val originalTitle: String,  // Original language title
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,  // Film release date
    
    @Column(nullable = false)
    val runtime: Int = 0,  // Duration in minutes
    
    @Column(nullable = false)
    val locale: String = "en",  // Language code (ISO 639-1)
    
    @Column(name = "available_globally")
    val isAvailableGlobally: Boolean = true,  // Netflix availability
    
    @ManyToMany
    @JoinTable(
        name = "movie_genre",
        joinColumns = [JoinColumn(name = "movie_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")]
    )
    val genres: MutableSet<Genre> = mutableSetOf()  // Associated genres
) {
    // Default constructor for JPA
    constructor() : this(
        id = 0,
        title = "",
        originalTitle = "",
        releaseDate = LocalDate.now(),
        runtime = 0,
        locale = "en",
        isAvailableGlobally = true,
        genres = mutableSetOf()
    )
}
```

**Database Schema:**
```sql
CREATE TABLE movie (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_title VARCHAR(255) NOT NULL,
    release_date DATE,
    runtime INT NOT NULL,
    locale VARCHAR(10) NOT NULL,
    available_globally BOOLEAN NOT NULL
);

CREATE INDEX idx_movie_locale ON movie(locale);
CREATE INDEX idx_movie_release_date ON movie(release_date);
```

---

## 4. TvShow Entity

```kotlin
@Entity
@Table(name = "tv_show")
data class TvShow(
    @Id
    val id: Long,  // IMDB ID
    
    @Column(nullable = false)
    val title: String,
    
    @Column(nullable = false)
    val originalTitle: String,
    
    @Column(name = "start_year")
    val startYear: Int?,  // Year aired
    
    @Column(name = "end_year")
    val endYear: Int?,  // Final year (null if ongoing)
    
    @Column(nullable = false)
    val locale: String = "en",
    
    @Column(name = "available_globally")
    val isAvailableGlobally: Boolean = true,
    
    @OneToMany(
        mappedBy = "tvShow",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    val seasons: MutableSet<Season> = mutableSetOf(),
    
    @ManyToMany
    @JoinTable(
        name = "tv_show_genre",
        joinColumns = [JoinColumn(name = "tv_show_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")]
    )
    val genres: MutableSet<Genre> = mutableSetOf()
) {
    constructor() : this(
        id = 0,
        title = "",
        originalTitle = "",
        startYear = null,
        endYear = null,
        locale = "en",
        isAvailableGlobally = true,
        seasons = mutableSetOf(),
        genres = mutableSetOf()
    )
}
```

---

## 5. Season Entity

```kotlin
@Entity
@Table(name = "season")
data class Season(
    @Id
    val id: Long,
    
    @Column(name = "season_number", nullable = false)
    val seasonNumber: Int,  // Season 1, 2, 3...
    
    @Column(nullable = false)
    val title: String,
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,
    
    @Column(nullable = false)
    val runtime: Int,  // Average episode duration
    
    @ManyToOne
    @JoinColumn(name = "tv_show_id", nullable = false)
    val tvShow: TvShow
) {
    constructor() : this(
        id = 0,
        seasonNumber = 0,
        title = "",
        releaseDate = LocalDate.now(),
        runtime = 0,
        tvShow = TvShow()
    )
}
```

---

## 6. Genre Entity

```kotlin
@Entity
@Table(name = "genre")
data class Genre(
    @Id
    val id: Long,
    
    @Column(nullable = false, unique = true)
    val name: String
) {
    @ManyToMany(mappedBy = "genres")
    val movies: MutableSet<Movie> = mutableSetOf()
    
    @ManyToMany(mappedBy = "genres")
    val tvShows: MutableSet<TvShow> = mutableSetOf()
    
    constructor() : this(id = 0, name = "")
}
```

---

## 7. ViewSummary Entity (Aggregation/Reporting)

```kotlin
@Entity
@Table(name = "view_summary")
data class ViewSummary(
    @Id
    val id: Long,
    
    @ManyToOne
    @JoinColumn(name = "movie_id")
    val movie: Movie?,  // Null if this is for TV show
    
    @Column(name = "view_rank")
    val viewRank: Int,  // Rank in top 10
    
    @Column(name = "hours_viewed")
    val hoursViewed: Long,  // Total hours watched
    
    @Column(name = "views")
    val views: Long,  // Number of viewers
    
    @Column(name = "cumulative_weeks_in_top10")
    val cumulativeWeeksInTop10: Int,  // How long in top 10
    
    @Column(name = "duration")
    @Enumerated(EnumType.STRING)
    val duration: Duration,  // WEEKLY or SEMI_ANNUALLY
    
    @Column(name = "start_date")
    val startDate: LocalDate,  // Report period start
    
    @Column(name = "end_date")
    val endDate: LocalDate  // Report period end
) {
    constructor() : this(
        id = 0,
        movie = null,
        viewRank = 0,
        hoursViewed = 0,
        views = 0,
        cumulativeWeeksInTop10 = 0,
        duration = Duration.WEEKLY,
        startDate = LocalDate.now(),
        endDate = LocalDate.now()
    )
}

enum class Duration {
    WEEKLY,
    SEMI_ANNUALLY
}
```

---

## 8. Entity Relationships Diagram

```
┌─────────────────┐
│     Movie       │
├─────────────────┤
│ id (PK)         │
│ title           │
│ runtime         │
│ releaseDate     │
└────────┬────────┘
         │
         │ @ManyToMany
         │ (via movie_genre table)
         │
         ▼
┌─────────────────┐
│     Genre       │
├─────────────────┤
│ id (PK)         │
│ name            │
└─────────────────┘


┌──────────────────┐
│    TvShow        │
├──────────────────┤
│ id (PK)          │
│ title            │
│ startYear        │
└────────┬─────────┘
         │
         │ @OneToMany
         │ (mappedBy = "tvShow")
         │
         ▼
┌──────────────────┐
│     Season       │
├──────────────────┤
│ id (PK)          │
│ seasonNumber     │
│ tvShowId (FK)    │ ◄─── Foreign key back to TvShow
└──────────────────┘


┌──────────────────────┐
│    ViewSummary       │
├──────────────────────┤
│ id (PK)              │
│ movieId (FK)         │ ◄─── @ManyToOne pointing to Movie
│ viewRank             │
│ hoursViewed          │
│ duration (enum)      │
│ startDate            │
│ endDate              │
└──────────────────────┘
```

---

## 9. JPA Lifecycle Hooks

```kotlin
@Entity
class Movie {
    @PrePersist
    fun beforeInsert() {
        logger.info("Inserting movie: $title")
        // Set defaults before insert
    }
    
    @PostPersist
    fun afterInsert() {
        logger.info("Successfully inserted movie with id: $id")
    }
    
    @PreUpdate
    fun beforeUpdate() {
        logger.info("Updating movie: $title")
    }
    
    @PostUpdate
    fun afterUpdate() {
        logger.info("Successfully updated movie")
    }
    
    @PreRemove
    fun beforeDelete() {
        logger.info("Deleting movie: $title")
    }
    
    @PostRemove
    fun afterDelete() {
        logger.info("Movie deleted")
    }
}
```

**Execution order:**
```
INSERT: @PrePersist → SQL INSERT → @PostPersist
UPDATE: @PreUpdate → SQL UPDATE → @PostUpdate
DELETE: @PreRemove → SQL DELETE → @PostRemove
SELECT: No hooks (entities just loaded)
```

---

## 10. Cascade Types Explained

```kotlin
// CascadeType.ALL = all operations cascade
@OneToMany(cascade = [CascadeType.ALL])
val children: MutableSet<Child>

// CascadeType.PERSIST = save parent → saves children
@OneToMany(cascade = [CascadeType.PERSIST])
val children: MutableSet<Child>

// CascadeType.MERGE = merge parent → merges children
@OneToMany(cascade = [CascadeType.MERGE])
val children: MutableSet<Child>

// CascadeType.REMOVE = delete parent → deletes children
@OneToMany(cascade = [CascadeType.REMOVE])
val children: MutableSet<Child>

// CascadeType.REFRESH = reload parent → reloads children
@OneToMany(cascade = [CascadeType.REFRESH])
val children: MutableSet<Child>

// CascadeType.DETACH = detach parent → detaches children
@OneToMany(cascade = [CascadeType.DETACH])
val children: MutableSet<Child>
```

---

## 11. FetchType Explained

```kotlin
// EAGER: Load immediately with parent
// Danger: Can load entire database if not careful
@ManyToOne(fetch = FetchType.EAGER)
val genre: Genre  // Loaded immediately

// SQL Generated:
// SELECT m.*, g.* FROM movie m
// LEFT JOIN genre g ON m.genre_id = g.id

---

// LAZY: Load only when accessed
// Safer: Only loads what you use
@ManyToOne(fetch = FetchType.LAZY)
val genre: Genre  // Loaded when you access genre

// SQL Generated:
// SELECT * FROM movie m
// (genre loaded later when accessed)
```

---

## 12. Configuration for Auto-Schema Generation

```yaml
# application.yml
spring:
  jpa:
    hibernate:
      ddl-auto: create-drop  # Create fresh schema on startup
      # Options:
      # - validate: Check schema matches entities
      # - update: Add new columns/tables
      # - create: Create fresh (destroy existing)
      # - create-drop: Create on startup, drop on shutdown
    
    show-sql: false  # Don't log SQL (too verbose)
    
    properties:
      hibernate:
        format_sql: true              # Pretty-print SQL
        dialect: org.hibernate.dialect.H2Dialect
        generate_statistics: false    # Performance metrics
```

---

## 13. Common Patterns

### Optional Many-to-One
```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "genre_id")
val genre: Genre?  // Can be null
```

### Immutable Entity
```kotlin
@Entity
data class Movie(
    @Id val id: Long,
    val title: String
    // All fields val (not var) = immutable
)
```

### Soft Delete (Mark as deleted, don't actually delete)
```kotlin
@Entity
class Movie {
    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null
    
    val isDeleted: Boolean
        get() = deletedAt != null
}

// Query:
// SELECT * FROM movie WHERE deleted_at IS NULL
```

### Audit Fields
```kotlin
@Entity
class Movie {
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
    
    @Column(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
    
    @PreUpdate
    fun updateTimestamp() {
        // Will be called before UPDATE
        // Can't update createdAt (updatable = false)
    }
}
```

---

## 14. Summary Table

| Annotation | Purpose | Example |
|-----------|---------|---------|
| @Entity | Mark as JPA entity | @Entity |
| @Table | Customize table name | @Table(name = "movie") |
| @Id | Primary key | @Id val id: Long |
| @Column | Column properties | @Column(nullable = false) |
| @ManyToOne | Many-to-one relation | @ManyToOne val genre: Genre |
| @OneToMany | One-to-many relation | @OneToMany(mappedBy = "show") val seasons |
| @ManyToMany | Many-to-many relation | @ManyToMany @JoinTable(...) val genres |
| @JoinColumn | Foreign key column | @JoinColumn(name = "genre_id") |
| @JoinTable | Association table | @JoinTable(name = "movie_genre") |
| @Enumerated | Enum handling | @Enumerated(EnumType.STRING) |
| @Transient | Not persisted | @Transient val derived |
| @Temporal | Date/time handling | @Temporal(TemporalType.DATE) |
| @PrePersist | Before insert | @PrePersist fun setup() |

---

## Quick Reference: Entity Best Practices

✅ **DO:**
- Use `val` for immutability
- Use Kotlin data classes for clarity
- Always provide default constructor for JPA
- Use `@ManyToOne` with appropriate `FetchType`
- Name `@JoinColumn` explicitly for clarity

❌ **DON'T:**
- Use mutable objects as entities
- Forget default constructor
- Use `FetchType.EAGER` carelessly (N+1 queries)
- Have circular @OneToMany dependencies without `mappedBy`
- Persist large objects directly (use @Transient)

---

**Used in:** Spring Data JPA repository pattern, Batch processors, SQL export
