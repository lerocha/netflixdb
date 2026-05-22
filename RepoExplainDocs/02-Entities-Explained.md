# Entity Layer Complete Explanation

## What Are Entities?

**Entity = Java/Kotlin class that represents a database table**

When you define:
```kotlin
@Entity
@Table(name = "movie")
data class Movie(
    @Id val id: Long,
    @Column(nullable = false) val title: String
)
```

Hibernate automatically creates:
```sql
CREATE TABLE movie (
    id BIGINT PRIMARY KEY,
    title VARCHAR(255) NOT NULL
);
```

## Understanding Annotations

### @Entity
**Purpose:** Tell Hibernate "this class is a database table"
**Effect:** Hibernate creates a table when application starts
**Required:** Yes, for every class mapped to database

### @Table(name = "movie")
**Purpose:** Customize table name
**Without it:** Table would be named "Movie" (class name)
**With it:** Table is named "movie" (snake_case convention)

### @Id
**Purpose:** Mark primary key
**Required:** Exactly one per entity
**Type:** Usually Long (auto-incrementing) or String (UUID)

### @Column(...)
**Purpose:** Customize how field is stored
**Options:**
- `nullable = false` → NOT NULL constraint
- `length = 255` → VARCHAR(255)
- `unique = true` → UNIQUE constraint
- `name = "release_date"` → Custom column name

## The 5 Core Entities

### 1. Movie (1247 rows)

```kotlin
@Entity
@Table(name = "movie")
data class Movie(
    @Id
    val id: Long,                              // IMDB ID (primary key)
    
    @Column(nullable = false)
    val title: String,                         // Display title
    
    @Column(nullable = false)
    val originalTitle: String,                 // Original language title
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,                // When film was released
    
    @Column(nullable = false)
    val runtime: Int = 0,                      // Duration in minutes
    
    @Column(nullable = false)
    val locale: String = "en",                 // Language (en, fr, es, etc.)
    
    @Column(name = "available_globally")
    val isAvailableGlobally: Boolean = true,   // Available worldwide?
    
    @ManyToMany
    @JoinTable(
        name = "movie_genre",
        joinColumns = [JoinColumn(name = "movie_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")]
    )
    val genres: MutableSet<Genre> = mutableSetOf()  // Associated genres
)
```

### 2. TvShow (345 rows)

```kotlin
@Entity
@Table(name = "tv_show")
data class TvShow(
    @Id
    val id: Long,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(name = "start_year")
    val startYear: Int?,
    
    @Column(name = "end_year")
    val endYear: Int?,                         // null = still airing
    
    @OneToMany(
        mappedBy = "tvShow",
        cascade = [CascadeType.ALL],
        orphanRemoval = true
    )
    val seasons: MutableSet<Season> = mutableSetOf(),
    
    @ManyToMany
    @JoinTable(name = "tv_show_genre", ...)
    val genres: MutableSet<Genre> = mutableSetOf()
)
```

### 3. Season (1500+ rows)

```kotlin
@Entity
@Table(name = "season")
data class Season(
    @Id
    val id: Long,
    
    @Column(name = "season_number", nullable = false)
    val seasonNumber: Int,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,
    
    @Column(nullable = false)
    val runtime: Int,
    
    @ManyToOne
    @JoinColumn(name = "tv_show_id", nullable = false)
    val tvShow: TvShow
)
```

### 4. Genre (20 rows)

```kotlin
@Entity
@Table(name = "genre")
data class Genre(
    @Id
    val id: Long,
    
    @Column(nullable = false, unique = true)
    val name: String
)
```

### 5. ViewSummary (5000+ rows)

```kotlin
@Entity
@Table(name = "view_summary")
data class ViewSummary(
    @Id
    val id: Long,
    
    @ManyToOne
    @JoinColumn(name = "movie_id")
    val movie: Movie?,
    
    @Column(name = "view_rank")
    val viewRank: Int,
    
    @Column(name = "hours_viewed")
    val hoursViewed: Long,
    
    @Column(name = "views")
    val views: Long,
    
    @Column(name = "cumulative_weeks_in_top10")
    val cumulativeWeeksInTop10: Int,
    
    @Column(name = "duration")
    @Enumerated(EnumType.STRING)
    val duration: Duration,
    
    @Column(name = "start_date")
    val startDate: LocalDate,
    
    @Column(name = "end_date")
    val endDate: LocalDate
)

enum class Duration {
    WEEKLY,
    SEMI_ANNUALLY
}
```

## Relationships Explained

### One-to-Many: TvShow → Season

```
1 TvShow ← → Many Seasons
"Stranger Things" has 5 seasons
```

### Many-to-Many: Movie ← → Genre

```
Many Movies ← → Many Genres
"Avatar" has [Action, Adventure, Science Fiction]
```

## Annotations Summary

| Annotation | Purpose |
|-----------|---------|
| @Entity | Mark as entity |
| @Table | Customize table name |
| @Id | Primary key |
| @Column | Column properties |
| @ManyToOne | Many-to-one relation |
| @OneToMany | One-to-many relation |
| @ManyToMany | Many-to-many relation |
| @JoinColumn | Foreign key |
| @JoinTable | Association table |
| @Enumerated | Enum storage |

**Next:** Learn how to query these entities with repositories.
