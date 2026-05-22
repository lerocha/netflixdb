# NetflixDB Complete Documentation - Master Index

**Comprehensive Guide to Every Layer, Component, Annotation, and Configuration**

---

## 📚 Documentation Modules

This documentation is organized into 7 comprehensive guides:

### 1. **Spring Batch Fundamentals** (3 guides)
- `spring-batch-guide-netflixdb.md` - Core concepts, architecture, and flow
- `spring-batch-code-reference.md` - Production Kotlin examples
- `spring-batch-visual-guide.md` - Diagrams, timelines, and decision trees

### 2. **Application Code** (4 guides)
- `01-entity-layer-complete.md` - JPA entities, relationships, annotations
- `02-repository-layer-complete.md` - Spring Data JPA, query methods
- `03-batch-layer-complete.md` - ItemReader, ItemProcessor, ItemWriter, Tasklets
- `04-configuration-build-complete.md` - Spring Boot config, Gradle, Docker

---

## 🗺️ Quick Navigation Map

```
NetflixDB Architecture
│
├─ ENTITY LAYER
│  ├─ @Entity / @Table
│  ├─ @Column / @Id
│  ├─ @ManyToOne / @OneToMany / @ManyToMany
│  ├─ @JoinColumn / @JoinTable
│  ├─ Movie entity
│  ├─ TvShow entity
│  ├─ Season entity
│  ├─ Genre entity
│  └─ ViewSummary entity
│  
├─ REPOSITORY LAYER
│  ├─ JpaRepository basics
│  ├─ Query method naming conventions
│  ├─ @Query custom queries
│  ├─ Pagination and sorting
│  ├─ MovieRepository
│  ├─ TvShowRepository
│  ├─ SeasonRepository
│  ├─ GenreRepository
│  └─ ViewSummaryRepository
│
├─ BATCH PROCESSING LAYER (`batch/CreateNetflixDatabaseJobConfig.kt`)
│  ├─ @Configuration (Spring Boot auto-enables Batch)
│  ├─ Job: `createNetflixDatabaseJob`
│  ├─ Chunk steps: engagement reports, top-10 Excel, populate movie/season
│  ├─ Tasklet steps: setup, verify content, export schema, zip artifact
│  ├─ ItemReader: `PoiItemReader<ReportSheetRow>` (engagement + top-10)
│  ├─ ItemProcessor: `movieProcessor`, `seasonProcessor`
│  ├─ In-memory staging: `accumulateReportRows()` → title/runtime maps
│  ├─ Export: `DatabaseExportService` + per-entity `exportDataStep`
│  └─ Chunk sizes: 100 (import/export), 50 (entity populate)
│
├─ CONFIGURATION LAYER
│  ├─ application.yml (Spring Boot properties)
│  ├─ Profile-specific configs (dev/test/prod)
│  ├─ build.gradle.kts (Gradle build)
│  ├─ docker-compose.yml (Database setup)
│  ├─ application.properties
│  ├─ Environment variables
│  └─ JVM system properties
│
└─ SPRING BATCH CONCEPTS
   ├─ Job execution flow
   ├─ Chunk vs Tasklet patterns
   ├─ JobRepository metadata
   ├─ Fault tolerance (skip/retry)
   ├─ Transaction boundaries
   └─ Performance optimization
```

---

## 📖 Documentation by Component

### ENTITY LAYER (01-entity-layer-complete.md)

| Entity | Purpose | Relationships |
|--------|---------|---------------|
| Movie | Film information | ← ViewSummary (one-to-many), → Genre (many-to-many) |
| TvShow | Television series | ← Season (one-to-many), → Genre (many-to-many) |
| Season | TV show season | → TvShow (many-to-one) |
| Genre | Content category | ← Movie (many-to-many), ← TvShow (many-to-many) |
| ViewSummary | Engagement metrics | → Movie (many-to-one) |

**Key Annotations:**
- `@Entity` - Mark as JPA entity
- `@Table(name = "...")` - Customize table name
- `@Column(nullable = false)` - Column constraints
- `@ManyToOne`, `@OneToMany`, `@ManyToMany` - Relationships
- `@JoinColumn`, `@JoinTable` - Foreign key mappings
- `@Enumerated(EnumType.STRING)` - Enum persistence
- `@PrePersist`, `@PreUpdate`, `@PreRemove` - Lifecycle hooks

**Quick Reference:**
```kotlin
// Example: Define a Movie entity
@Entity
@Table(name = "movie")
data class Movie(
    @Id val id: Long,
    @Column(nullable = false) val title: String,
    @Column(name = "release_date") val releaseDate: LocalDate,
    @ManyToMany @JoinTable(...) val genres: MutableSet<Genre>
)
```

---

### REPOSITORY LAYER (02-repository-layer-complete.md)

| Repository | Extends | Purpose | Key Methods |
|------------|---------|---------|-------------|
| MovieRepository | JpaRepository<Movie, Long> | Movie CRUD | findByLocale, findByReleaseDateBetween |
| TvShowRepository | JpaRepository<TvShow, Long> | TV Show CRUD | findByTitleContaining, findByEndYearIsNull |
| SeasonRepository | JpaRepository<Season, Long> | Season CRUD | findByTvShowId, findBySeasonNumber |
| GenreRepository | JpaRepository<Genre, Long> | Genre CRUD | findByName, findByNameIn |
| ViewSummaryRepository | JpaRepository<ViewSummary, Long> | Metrics CRUD | findByDurationAndEndDate |

**Query Method Patterns:**
- `findBy<Property>()` - Equality
- `findBy<Property>Between()` - Range
- `findBy<Property>In()` - Multiple values
- `findBy<Property>Containing()` - String contains
- `findTop10By<Property>()` - Limit results
- `findBy<Property>IsNull()` - Null check

**@Query Examples:**
```kotlin
@Query("SELECT m FROM Movie m WHERE m.locale = :locale")
fun findByLocale(@Param("locale") locale: String): List<Movie>

@Query("SELECT DISTINCT m FROM Movie m LEFT JOIN FETCH m.genres WHERE m.releaseDate >= :date")
fun findRecentWithGenres(@Param("date") date: LocalDate): List<Movie>
```

---

### BATCH PROCESSING LAYER (03-batch-layer-complete.md)

| Component | Type | Purpose | Input → Output |
|-----------|------|---------|-----------------|
| CreateNetflixDatabaseJobConfig | @Configuration | Job + step beans | `batch/` |
| importNetflixDataJob | @Bean | 3-step batch job | Start → Finish |
| importMoviesStep | @Bean | Chunk-oriented step | RawMovieDto → Movie |
| importTvShowsStep | @Bean | Chunk-oriented step | RawTvShowDto → TvShow |
| exportSqlStep | @Bean | Tasklet step | DB entities → SQL files |
| NetflixMovieCsvItemReader | ItemReader | Read CSV lines | String → RawMovieDto |
| MovieProcessor | ItemProcessor | Validate/transform | RawMovieDto → Movie (or null) |
| MovieDatabaseWriter | ItemWriter | Batch insert | List<Movie> → DB |
| ExportSqlScriptsTasklet | Tasklet | Generate SQL files | (none) → SQL files |

**Key Annotations:**
- `@Configuration` - Spring bean configuration class
- `@EnableBatchProcessing` - Activate Spring Batch
- `@Bean` - Register as Spring bean
- `@Component` - Auto-register class as Spring bean
- `@Value("classpath:...")` - Inject resource from classpath
- `@Transactional` - Wrap in transaction
- `@Modifying` - For DELETE/UPDATE JPA queries

**Chunk Processing Flow:**
```
Reader.read() × 10
    ↓
Processor.process() × 10
    ↓
Accumulate in chunk [10 items]
    ↓
Writer.write(chunk)
    ↓
COMMIT TRANSACTION
```

---

### CONFIGURATION LAYER (04-configuration-build-complete.md)

| File | Purpose | Key Settings |
|------|---------|--------------|
| application.yml | Default configuration | datasource, jpa, batch |
| application-dev.yml | Development config | H2 in-memory, show-sql: true |
| application-test.yml | Test config | In-memory H2, batch: disabled |
| application-prod.yml | Production config | PostgreSQL, validate schema |
| build.gradle.kts | Gradle build | Dependencies, plugins, tasks |
| docker-compose.yml | Database setup | MySQL, PostgreSQL, Oracle, SQL Server |
| .env | Environment variables | DB_USER, DB_PASSWORD (git-ignored) |

**Key Properties:**
```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/netflixdb
    username: sa
    password: (empty)
  
  jpa:
    hibernate:
      ddl-auto: create-drop        # create-drop (dev), validate (prod)
    show-sql: false
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        order_inserts: true
  
  batch:
    job:
      enabled: true
      names: importNetflixDataJob
    jdbc:
      initialize-database: always
```

---

## 🔍 Annotation Quick Reference

### JPA Annotations

| Annotation | Class | Purpose | Example |
|-----------|-------|---------|---------|
| @Entity | Class | JPA entity | @Entity class Movie |
| @Table | Class | Table name/schema | @Table(name = "movie") |
| @Id | Field | Primary key | @Id val id: Long |
| @Column | Field | Column properties | @Column(nullable = false) |
| @ManyToOne | Field | Many-to-one relation | @ManyToOne val genre: Genre |
| @OneToMany | Field | One-to-many relation | @OneToMany(mappedBy = "show") val seasons |
| @ManyToMany | Field | Many-to-many relation | @ManyToMany @JoinTable(...) val genres |
| @JoinColumn | Field | Foreign key column | @JoinColumn(name = "genre_id") |
| @JoinTable | Field | Association table | @JoinTable(name = "movie_genre") |
| @Enumerated | Field | Enum persistence | @Enumerated(EnumType.STRING) |
| @Temporal | Field | Date/time type | @Temporal(TemporalType.DATE) |
| @Transient | Field | Not persisted | @Transient val computed |
| @PrePersist | Method | Before insert | @PrePersist fun init() |
| @PostPersist | Method | After insert | @PostPersist fun log() |
| @PreUpdate | Method | Before update | @PreUpdate fun updateTime() |
| @PostRemove | Method | After delete | @PostRemove fun cleanup() |

### Spring Data JPA Annotations

| Annotation | Purpose | Example |
|-----------|---------|---------|
| @Repository | Mark as data access layer | @Repository interface MovieRepository |
| @Query | Custom JPQL query | @Query("SELECT m FROM Movie m WHERE m.locale = ?1") |
| @Param | Named query parameter | @Param("locale") locale: String |
| @Modifying | For DELETE/UPDATE | @Modifying @Query("DELETE FROM Movie m WHERE...") |
| @EntityGraph | Eager load relations | @EntityGraph(attributePaths = ["genres"]) |
| @Transactional | Wrap in transaction | @Transactional(readOnly = true) |

### Spring Batch Annotations

| Annotation | Purpose | Example |
|-----------|---------|---------|
| @Configuration | Spring bean config | @Configuration class JobConfig |
| @EnableBatchProcessing | Activate Spring Batch | @EnableBatchProcessing |
| @Bean | Register as Spring bean | @Bean fun importMoviesStep() |
| @Component | Auto-register bean | @Component class MovieProcessor |
| @Value | Inject config value | @Value("classpath:movies.csv") val resource |

---

## 🚀 Common Tasks & Where to Find Them

### Task: Add a New Entity
**Location:** `01-entity-layer-complete.md` → Section: "Core Annotations Reference"
**Steps:**
1. Create @Entity class with @Id
2. Define @Column fields with constraints
3. Add @ManyToOne or @OneToMany relations if needed
4. Repository auto-created by Spring Data JPA

### Task: Write a Custom Query
**Location:** `02-repository-layer-complete.md` → Section: "@Query Annotation"
**Options:**
1. **Naming convention:** `fun findByTitleAndLocale(...)`
2. **@Query JPQL:** `@Query("SELECT m FROM Movie m WHERE m.locale = ?1")`
3. **@Query native SQL:** `@Query(..., nativeQuery = true)`

### Task: Debug Batch Job Failure
**Location:** `03-batch-layer-complete.md` → Section: "Error Handling Strategies"
**Approaches:**
1. Check Spring Batch logs (step execution status)
2. Check JobRepository metadata (job_execution table)
3. Configure `.faultTolerant().skip()` or `.retry()`
4. Reduce chunk size if memory issues
5. Check ItemReader EOF condition (returns null?)

### Task: Optimize Performance
**Location:** `02-repository-layer-complete.md` → Section: "Performance Optimization Patterns"
**Techniques:**
1. Use `LEFT JOIN FETCH` to avoid N+1 queries
2. Use pagination for large result sets
3. Increase chunk size in Spring Batch
4. Set `hibernate.jdbc.batch_size` in properties
5. Create database indexes on frequently queried columns

### Task: Configure for Production
**Location:** `04-configuration-build-complete.md` → Section: "application-prod.yml"
**Changes:**
1. Switch datasource to PostgreSQL/Oracle/SQL Server
2. Change `ddl-auto: validate` (never modify schema)
3. Set `show-sql: false` (performance)
4. Use environment variables for secrets
5. Disable batch auto-run (`enabled: false`)

### Task: Add a New Spring Batch Step
**Location:** `03-batch-layer-complete.md` → Section: "ItemProcessor"
**Process:**
1. Create ItemReader<T> (or reuse existing)
2. Create ItemProcessor<T, U> for validation
3. Create ItemWriter<U> for persistence
4. Create @Bean for Step in JobConfig
5. Add `.next(newStep)` to Job definition

---

## 📊 Data Flow Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                    APPLICATION STARTUP                          │
└────────────────────────┬────────────────────────────────────────┘
                         │
        ┌────────────────┴────────────────┐
        │                                 │
        ▼                                 ▼
   ┌─────────────┐              ┌──────────────────┐
   │ Spring Data │              │ Spring Batch     │
   │ JPA         │              │ (@EnableBatch)   │
   │             │              │                  │
   │ Initializes │              │ JobRepository    │
   │ Entity      │              │ created          │
   │ schema      │              │                  │
   └────────────┬┘              └────────┬─────────┘
                │                        │
                ▼                        ▼
        ┌─────────────────────────────────────┐
        │  ImportNetflixDataJob ready         │
        │  - importMoviesStep                 │
        │  - importTvShowsStep                │
        │  - exportSqlStep                    │
        └────────────┬──────────────────────┘
                     │
                     ▼
        ┌────────────────────────────────┐
        │  JobLauncher.run(job)          │
        │  ↓                             │
        │  STEP 1: importMoviesStep      │
        │  ├─ Read CSV (MovieReader)     │
        │  ├─ Process (MovieProcessor)   │
        │  └─ Write batch (MovieWriter)  │
        │  ↓                             │
        │  STEP 2: importTvShowsStep     │
        │  (Same chunk pattern)          │
        │  ↓                             │
        │  STEP 3: exportSqlStep         │
        │  └─ Tasklet: Generate SQL      │
        │  ↓                             │
        │  Job COMPLETED                 │
        └────────────┬───────────────────┘
                     │
                     ▼
        ┌─────────────────────────────────┐
        │  build/artifacts/               │
        │  - netflix-mysql.sql            │
        │  - netflix-postgresql.sql       │
        │  - netflix-oracle.sql           │
        │  - netflix-sqlserver.sql        │
        │  - netflix-sqlite.sql           │
        └─────────────────────────────────┘

DATABASE:
┌──────────────────────────────────────────────────────┐
│ movie  │ tv_show │ season │ genre │ view_summary │   │
│ ─────  │ ─────── │ ────── │ ────── │ ──────────── │   │
│ 1247   │   345   │  1500  │  20   │   5000       │   │
└──────────────────────────────────────────────────────┘
```

---

## 🔗 Cross-Reference Guide

**If you want to understand...**

→ **"How are movies stored?"**
   - See: `01-entity-layer-complete.md` → Section: "Movie Entity"
   - Also: `spring-batch-guide-netflixdb.md` → Section: "NetflixDB Architecture Overview"

→ **"How does the batch import work?"**
   - See: `03-batch-layer-complete.md` → Section: "CreateNetflixDatabaseJobConfig"
   - Also: `spring-batch-code-reference.md` → Section: "Complete Execution Trace"

→ **"How do I query movies?"**
   - See: `02-repository-layer-complete.md` → Section: "Query Methods by Naming Convention"
   - Also: `spring-batch-code-reference.md` → Section: "Repository Interfaces"

→ **"What happens when the application starts?"**
   - See: `spring-batch-visual-guide.md` → Section: "Complete Sequence"
   - Also: `04-configuration-build-complete.md` → Section: "Spring Boot Initialization"

→ **"How do I set up for production?"**
   - See: `04-configuration-build-complete.md` → Section: "application-prod.yml"
   - Also: `04-configuration-build-complete.md` → Section: "Environment Variables for Secrets"

→ **"What if the batch job fails?"**
   - See: `03-batch-layer-complete.md` → Section: "Error Handling Strategies"
   - Also: `spring-batch-visual-guide.md` → Section: "Error Handling Flows"

→ **"How do I improve query performance?"**
   - See: `02-repository-layer-complete.md` → Section: "Performance Optimization Patterns"
   - Also: `spring-batch-visual-guide.md` → Section: "Memory Profile Comparison"

---

## 📝 Summary Table: All Components

| Component | File | Type | Purpose | Key Dependency |
|-----------|------|------|---------|-----------------|
| Movie | Entity | @Entity | Film information | @ManyToMany Genre |
| TvShow | Entity | @Entity | TV series | @OneToMany Season |
| Season | Entity | @Entity | Season details | @ManyToOne TvShow |
| Genre | Entity | @Entity | Content category | @ManyToMany Movie/TvShow |
| ViewSummary | Entity | @Entity | Metrics/reporting | @ManyToOne Movie |
| MovieRepository | Interface | @Repository | Movie CRUD/queries | JpaRepository |
| TvShowRepository | Interface | @Repository | TvShow CRUD/queries | JpaRepository |
| SeasonRepository | Interface | @Repository | Season CRUD/queries | JpaRepository |
| GenreRepository | Interface | @Repository | Genre CRUD/queries | JpaRepository |
| ViewSummaryRepository | Interface | @Repository | Metrics queries | JpaRepository |
| CreateNetflixDatabaseJobConfig | Class | @Configuration | Job definition | Spring Boot Batch auto-config |
| importNetflixDataJob | Method | @Bean | Main batch job | 3 steps |
| importMoviesStep | Method | @Bean | Movie import step | Reader/Processor/Writer |
| importTvShowsStep | Method | @Bean | Show import step | Reader/Processor/Writer |
| exportSqlStep | Method | @Bean | SQL export step | Tasklet |
| NetflixMovieCsvItemReader | Class | ItemReader | CSV reading | Resource |
| MovieProcessor | Class | ItemProcessor | Data transformation | GenreRepository |
| MovieDatabaseWriter | Class | ItemWriter | Batch database writes | MovieRepository |
| ExportSqlScriptsTasklet | Class | Tasklet | SQL file generation | All repositories |

---

## ✅ Next Steps After Reading

1. **Understand the data model**
   - Start with: `01-entity-layer-complete.md`
   - Then read: `spring-batch-guide-netflixdb.md` (Architecture section)

2. **Learn batch processing**
   - Read: `spring-batch-guide-netflixdb.md` (all sections)
   - Supplement with: `spring-batch-code-reference.md`
   - Visualize with: `spring-batch-visual-guide.md`

3. **Deep dive into code**
   - See: `03-batch-layer-complete.md` (each component)
   - See: `02-repository-layer-complete.md` (query patterns)

4. **Set up locally**
   - Follow: `04-configuration-build-complete.md` → Docker Compose section
   - Run: `docker-compose up -d`
   - Build: `./gradlew bootRun`

5. **Deploy to production**
   - See: `04-configuration-build-complete.md` → application-prod.yml
   - Set environment variables
   - Use Docker image with secrets management

---

## 📞 FAQ

**Q: Which Spring Batch concepts do I need to understand?**
A: At minimum: Job, Step, ItemReader, ItemProcessor, ItemWriter, Chunk, Tasklet. Start with `spring-batch-guide-netflixdb.md` Core Concepts section.

**Q: How do I modify the batch logic?**
A: Edit `CreateNetflixDatabaseJobConfig.kt`, `ReportSheetRow.kt` (mappers), or `DatabaseExportService.kt`. See `03-batch-layer-complete.md` for details.

**Q: How do I add a new table/entity?**
A: Create entity class with @Entity annotation. See `01-entity-layer-complete.md` for all annotations. Spring Data JPA auto-creates repository.

**Q: How do I query the database?**
A: Use repositories (auto-generated) or write custom @Query methods. See `02-repository-layer-complete.md` for examples.

**Q: How do I debug a failed batch job?**
A: Check logs, check JobRepository tables (BATCH_JOB_EXECUTION), verify ItemReader returns null at EOF. See `03-batch-layer-complete.md` Common Issues section.

**Q: How do I deploy this?**
A: Use Docker image with PostgreSQL database. Set environment variables for secrets. See `04-configuration-build-complete.md` for full instructions.

---

**Last Updated:** May 22, 2026  
**NetflixDB Version:** 1.0.49  
**Spring Boot:** 3.2.0+  
**Java:** JDK 21+
