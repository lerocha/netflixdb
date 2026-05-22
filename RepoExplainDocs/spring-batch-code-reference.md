# Spring Batch Code Reference: NetflixDB Patterns

**Kotlin-first examples with detailed explanations**

---

## 1. Basic Job Configuration (ImportNetflixDataJobConfig.kt)

```kotlin
@Configuration
@EnableBatchProcessing
class ImportNetflixDataJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository
) {
    
    // ============================================
    // DEFINE THE COMPLETE JOB
    // ============================================
    @Bean
    fun importNetflixDataJob(
        importMoviesStep: Step,
        importTvShowsStep: Step,
        exportSqlStep: Step
    ): Job {
        return JobBuilder("importNetflixDataJob", jobRepository)
            .start(importMoviesStep)           // Step 1: Run first
            .next(importTvShowsStep)           // Step 2: Run after Step 1 completes
            .next(exportSqlStep)               // Step 3: Run after Step 2 completes
            .build()
    }
    
    // ============================================
    // STEP 1: IMPORT MOVIES (CHUNK-BASED)
    // ============================================
    @Bean
    fun importMoviesStep(
        movieItemReader: ItemReader<RawMovieDto>,
        movieProcessor: ItemProcessor<RawMovieDto, Movie>,
        movieItemWriter: ItemWriter<Movie>
    ): Step {
        return StepBuilder("importMoviesStep", jobRepository)
            .<RawMovieDto, Movie>chunk(10, transactionManager)  // Read 10, process 10, write 10
            .reader(movieItemReader)
            .processor(movieProcessor)
            .writer(movieItemWriter)
            .faultTolerant()
            .skip(DataAccessException::class.java)             // Skip DB errors
            .skipLimit(5)                                       // Max 5 skips before failing
            .retry(OptimisticLockingFailureException::class.java)
            .retryLimit(3)                                      // Retry 3 times on lock conflicts
            .build()
    }
    
    // ============================================
    // STEP 2: IMPORT TV SHOWS (SAME PATTERN)
    // ============================================
    @Bean
    fun importTvShowsStep(
        tvShowItemReader: ItemReader<RawTvShowDto>,
        tvShowProcessor: ItemProcessor<RawTvShowDto, TvShow>,
        tvShowItemWriter: ItemWriter<TvShow>
    ): Step {
        return StepBuilder("importTvShowsStep", jobRepository)
            .<RawTvShowDto, TvShow>chunk(10, transactionManager)
            .reader(tvShowItemReader)
            .processor(tvShowProcessor)
            .writer(tvShowItemWriter)
            .faultTolerant()
            .skip(DataAccessException::class.java)
            .skipLimit(5)
            .build()
    }
    
    // ============================================
    // STEP 3: EXPORT SQL SCRIPTS (TASKLET)
    // ============================================
    @Bean
    fun exportSqlStep(
        exportSqlTasklet: ExportSqlScriptsTasklet
    ): Step {
        return StepBuilder("exportSqlStep", jobRepository)
            .tasklet(exportSqlTasklet, transactionManager)      // Single tasklet, no chunking
            .build()
    }
}
```

**Key Concepts:**
- `@EnableBatchProcessing` → Activates Spring Batch autoconfiguration
- `.chunk(10)` → Batch size: read 10, process 10, write 10 together
- `.next()` → Chain steps in order
- `.faultTolerant()` → Enable error handling (skip, retry)

---

## 2. ItemReader: Reading from CSV

```kotlin
@Component
class NetflixMovieCsvItemReader(
    @Value("classpath:reports/netflix-movies.csv")
    private val resource: Resource
) : ItemReader<RawMovieDto> {
    
    private lateinit var csvParser: CSVParser
    private var initialized = false
    
    override fun read(): RawMovieDto? {
        // Lazy initialization (first call to read())
        if (!initialized) {
            val reader = resource.inputStream.bufferedReader()
            csvParser = CSVFormat.DEFAULT
                .withFirstRecordAsHeader()           // Use first row as column names
                .parse(reader)
            initialized = true
        }
        
        // Read next record
        val record = try {
            csvParser.nextRecord ?: return null     // EOF: return null to signal end
        } catch (e: IOException) {
            logger.error("Error reading CSV", e)
            throw ItemReaderException("Failed to read CSV", e)
        }
        
        return RawMovieDto(
            imdbId = record.get("imdb_id"),
            title = record.get("title"),
            releaseDate = record.get("release_date"),
            runtime = record.get("runtime")?.toIntOrNull(),
            countryList = record.get("country").split(",").map { it.trim() },
            locale = record.get("locale"),
            genreNames = record.get("genres").split(",").map { it.trim() }
        )
    }
}

// Data transfer object for raw CSV data
data class RawMovieDto(
    val imdbId: String,
    val title: String,
    val releaseDate: String,
    val runtime: Int?,
    val countryList: List<String>,
    val locale: String,
    val genreNames: List<String>
)
```

**How It Works:**
```
Application calls reader.read() repeatedly:

Call 1: Returns RawMovieDto(id="81145628", title="Damsel", ...)
Call 2: Returns RawMovieDto(id="81235234", title="Avatar", ...)
Call 3: Returns RawMovieDto(id="81345345", title="The Iron Claw", ...)
...
Call N: Returns null → Spring Batch knows to stop reading
```

---

## 3. ItemProcessor: Transforming & Validating

```kotlin
@Component
class MovieProcessor(
    private val genreRepository: GenreRepository
) : ItemProcessor<RawMovieDto, Movie> {
    
    override fun process(raw: RawMovieDto): Movie? {
        // VALIDATION
        if (raw.title.isBlank()) {
            logger.warn("Skipping movie with blank title: $raw")
            return null  // Return null = skip this item (won't be written)
        }
        
        if (raw.releaseDate.isEmpty()) {
            throw ProcessingException("Movie ${raw.title} missing release date")  // Fail the step
        }
        
        // TRANSFORMATION
        val genres = genreRepository.findByNameIn(raw.genreNames)
        
        return Movie(
            id = raw.imdbId.toLong(),
            title = raw.title.trim(),
            originalTitle = raw.title,
            releaseDate = LocalDate.parse(raw.releaseDate, DateTimeFormatter.ISO_DATE),
            runtime = raw.runtime ?: 0,
            locale = raw.locale,
            isAvailableGlobally = raw.countryList.contains("Global"),
            genres = genres.toMutableSet()
        )
    }
}
```

**Processing Outcomes:**
1. **Normal:** Return transformed object → will be written
2. **Skip:** Return `null` → item skipped (won't be written, doesn't fail)
3. **Fail:** Throw exception → step fails (or retried if configured)

---

## 4. ItemWriter: Writing to Database

```kotlin
@Component
class MovieDatabaseWriter(
    private val movieRepository: MovieRepository
) : ItemWriter<Movie> {
    
    override fun write(chunk: Chunk<out Movie>) {
        logger.info("Writing ${chunk.items.size} movies to database")
        
        try {
            // Batch write: insert/update all items at once
            // Much more efficient than individual saves
            val saved = movieRepository.saveAll(chunk.items)
            logger.info("Successfully saved ${saved.size} movies")
            
            // Optional: flush to ensure writes are immediately visible
            // (Depends on JPA flush mode configuration)
            
        } catch (e: DataIntegrityViolationException) {
            logger.error("Constraint violation saving movies", e)
            throw  // Re-throw to trigger retry/skip logic
        }
    }
}
```

**The Chunk Paradigm:**
```
Chunk 1: [Movie1, Movie2, ..., Movie10]  → writer.write(Chunk1)
         ↓
         INSERT all 10 in single batch operation
         ↓
         COMMIT transaction

Chunk 2: [Movie11, Movie12, ..., Movie20] → writer.write(Chunk2)
         ↓
         INSERT all 10 in single batch operation
         ↓
         COMMIT transaction
```

---

## 5. Tasklet: Single Atomic Operation

```kotlin
@Component
class ExportSqlScriptsTasklet(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val seasonRepository: SeasonRepository,
    private val viewSummaryRepository: ViewSummaryRepository,
    private val sqlExportService: SqlExportService
) : Tasklet {
    
    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext
    ): RepeatStatus {
        logger.info("=== Starting SQL export for all database vendors ===")
        val startTime = System.currentTimeMillis()
        
        try {
            // STEP 1: Fetch all data from database
            logger.info("Fetching data from database...")
            val movies = movieRepository.findAll()
            val tvShows = tvShowRepository.findAll()
            val seasons = seasonRepository.findAll()
            val viewSummaries = viewSummaryRepository.findAll()
            
            logger.info("Fetched: ${movies.size} movies, ${tvShows.size} TV shows")
            
            // STEP 2: Generate SQL for each vendor
            logger.info("Generating SQL scripts...")
            
            val mysqlScript = sqlExportService.generateMysql(
                movies, tvShows, seasons, viewSummaries
            )
            val postgresScript = sqlExportService.generatePostgresql(
                movies, tvShows, seasons, viewSummaries
            )
            val oracleScript = sqlExportService.generateOracle(
                movies, tvShows, seasons, viewSummaries
            )
            val sqlServerScript = sqlExportService.generateSqlServer(
                movies, tvShows, seasons, viewSummaries
            )
            val sqliteScript = sqlExportService.generateSqlite(
                movies, tvShows, seasons, viewSummaries
            )
            
            // STEP 3: Write to files
            logger.info("Writing SQL files...")
            
            val outputDir = File("build/artifacts").apply { mkdirs() }
            
            writeFile(File(outputDir, "netflix-mysql.sql"), mysqlScript)
            writeFile(File(outputDir, "netflix-postgresql.sql"), postgresScript)
            writeFile(File(outputDir, "netflix-oracle.sql"), oracleScript)
            writeFile(File(outputDir, "netflix-sqlserver.sql"), sqlServerScript)
            writeFile(File(outputDir, "netflix-sqlite.sql"), sqliteScript)
            
            // STEP 4: Log metrics
            val duration = System.currentTimeMillis() - startTime
            contribution.incrementSummary("bytesWritten", mysqlScript.length.toLong())
            
            logger.info("SQL export complete in ${duration}ms")
            
            // Return FINISHED to indicate success
            return RepeatStatus.FINISHED
            
        } catch (e: Exception) {
            logger.error("SQL export failed", e)
            contribution.setExitStatus(ExitStatus.FAILED)
            throw e  // Will trigger step failure
        }
    }
    
    private fun writeFile(file: File, content: String) {
        file.writeText(content, Charsets.UTF_8)
        logger.info("Created: ${file.absolutePath} (${content.length} bytes)")
    }
}
```

**Key Tasklet Patterns:**
- Run once, completely
- Return `RepeatStatus.FINISHED` when done
- Can update `StepContribution` with metrics
- Return `RepeatStatus.CONTINUABLE` if it should run again (rare)

---

## 6. Entity Classes (Domain Model)

```kotlin
@Entity
@Table(name = "movie")
class Movie(
    @Id
    val id: Long,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(nullable = false)
    val originalTitle: String,
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,
    
    @Column(nullable = false)
    val runtime: Int = 0,
    
    @Column(nullable = false)
    val locale: String = "en",
    
    @Column(name = "available_globally")
    val isAvailableGlobally: Boolean = true,
    
    @ManyToMany
    @JoinTable(
        name = "movie_genre",
        joinColumns = [JoinColumn(name = "movie_id")],
        inverseJoinColumns = [JoinColumn(name = "genre_id")]
    )
    val genres: MutableSet<Genre> = mutableSetOf()
) {
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

@Entity
@Table(name = "tv_show")
class TvShow(
    @Id
    val id: Long,
    
    @Column(nullable = false)
    val title: String,
    
    @OneToMany(mappedBy = "tvShow", cascade = [CascadeType.ALL])
    val seasons: MutableSet<Season> = mutableSetOf()
) {
    constructor() : this(id = 0, title = "", seasons = mutableSetOf())
}

@Entity
@Table(name = "season")
class Season(
    @Id
    val id: Long,
    
    @Column(name = "season_number")
    val seasonNumber: Int,
    
    @Column(nullable = false)
    val title: String,
    
    @Column(name = "release_date")
    val releaseDate: LocalDate,
    
    @Column(nullable = false)
    val runtime: Int,
    
    @ManyToOne
    @JoinColumn(name = "tv_show_id")
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

// Aggregated view for reporting
@Entity
@Table(name = "view_summary")
class ViewSummary(
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

## 7. Repository Interfaces

```kotlin
// Spring Data JPA repositories (auto-implement CRUD)

@Repository
interface MovieRepository : JpaRepository<Movie, Long> {
    fun findByLocale(locale: String): List<Movie>
    fun findByReleaseDateBetween(start: LocalDate, end: LocalDate): List<Movie>
}

@Repository
interface TvShowRepository : JpaRepository<TvShow, Long> {
    fun findByTitleContainingIgnoreCase(title: String): List<TvShow>
}

@Repository
interface SeasonRepository : JpaRepository<Season, Long> {
    fun findByTvShowId(tvShowId: Long): List<Season>
}

@Repository
interface ViewSummaryRepository : JpaRepository<ViewSummary, Long> {
    fun findByDurationAndEndDate(duration: Duration, endDate: LocalDate): List<ViewSummary>
}

@Repository
interface GenreRepository : JpaRepository<Genre, Long> {
    fun findByNameIn(names: List<String>): List<Genre>
}
```

---

## 8. Application Properties

```yaml
# application.yml - Spring Batch Configuration

spring:
  application:
    name: netflixdb
  
  # Database Configuration
  datasource:
    url: jdbc:h2:file:./data/netflixdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    username: sa
    password: 
    driver-class-name: org.h2.Driver
  
  jpa:
    hibernate:
      ddl-auto: create-drop  # Create fresh schema each time
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        jdbc:
          batch_size: 20              # JPA batch size
          fetch_size: 20
        order_inserts: true           # Optimize batch inserts
        order_updates: true
  
  # Spring Batch Configuration
  batch:
    job:
      enabled: true                   # Auto-run jobs on startup
      names: importNetflixDataJob     # Which job to run
    
    jdbc:
      initialize-database: always     # Create batch metadata tables
    
    threads:
      corePoolSize: 2                 # For async processing
  
  # Logging
  jpa:
    show-sql: false                   # Don't log SQL (too verbose)
  logging:
    level:
      org.springframework.batch: INFO
      com.github.lerocha.netflixdb: DEBUG
```

---

## 9. Complete Execution Trace

```kotlin
/*
 * This shows what happens when the application starts with Spring Batch enabled
 */

// APPLICATION STARTUP
logger.info("Starting NetflixDB application...")

// Spring loads @EnableBatchProcessing
logger.info("Initializing Spring Batch...")

// JobRepository tables created in H2
logger.info("Creating batch metadata tables (job_instance, job_execution, step_execution)")

// Job beans loaded
logger.info("Loading ImportNetflixDataJobConfig...")
logger.info("  - importNetflixDataJob bean created")
logger.info("  - importMoviesStep bean created")
logger.info("  - importTvShowsStep bean created")
logger.info("  - exportSqlStep bean created")

// Job execution initiated
logger.info("JobLauncher.run() called...")
logger.info("Creating JobExecution...")
logger.info("  - JobInstance: importNetflixDataJob (run #1)")
logger.info("  - Status: STARTED")

// ====== STEP 1: importMoviesStep ======
logger.info("Starting step: importMoviesStep")
logger.info("Opening resources (CSV file, database connection)")

// Chunk loop iteration
logger.info("--- Chunk 1 (movies 1-10) ---")
logger.info("MovieCsvItemReader.read() called 10 times...")
logger.info("  Read: Movie(id=81145628, title='Damsel')")
logger.info("  Read: Movie(id=81235234, title='Avatar')")
// ... 8 more reads
logger.info("MovieProcessor.process() called 10 times...")
logger.info("  Validated and transformed 10 movies")
logger.info("MovieDatabaseWriter.write() called once")
logger.info("  Batch inserting 10 movies into database")
logger.info("  COMMIT transaction")

logger.info("--- Chunk 2 (movies 11-20) ---")
// ... more chunks

logger.info("MovieCsvItemReader.read() returns null")
logger.info("EOF reached, exiting chunk loop")
logger.info("Closing resources")
logger.info("StepExecution complete for importMoviesStep")
logger.info("  - read_count: 1247")
logger.info("  - write_count: 1247")
logger.info("  - skip_count: 2")
logger.info("  - Duration: 5234ms")

// ====== STEP 2: importTvShowsStep ======
logger.info("Starting step: importTvShowsStep")
// ... similar trace as Step 1
logger.info("StepExecution complete for importTvShowsStep")
logger.info("  - read_count: 345")
logger.info("  - write_count: 345")

// ====== STEP 3: exportSqlStep ======
logger.info("Starting step: exportSqlStep")
logger.info("ExportSqlScriptsTasklet.execute() called")
logger.info("  Fetching all data from database...")
logger.info("  Generating SQL scripts for 5 vendors...")
logger.info("  Writing: build/artifacts/netflix-mysql.sql")
logger.info("  Writing: build/artifacts/netflix-postgresql.sql")
logger.info("  Writing: build/artifacts/netflix-oracle.sql")
logger.info("  Writing: build/artifacts/netflix-sqlserver.sql")
logger.info("  Writing: build/artifacts/netflix-sqlite.sql")
logger.info("  Tasklet returning RepeatStatus.FINISHED")
logger.info("StepExecution complete for exportSqlStep")
logger.info("  - Duration: 2145ms")

// ====== JOB COMPLETION ======
logger.info("All steps completed successfully")
logger.info("JobExecution.setStatus(COMPLETED)")
logger.info("Saving final metadata to JobRepository...")
logger.info("")
logger.info("========== JOB SUMMARY ==========")
logger.info("Job Name: importNetflixDataJob")
logger.info("Status: COMPLETED")
logger.info("Total Duration: 7379ms")
logger.info("Items Processed: 1592")
logger.info("SQL Files Generated: 5")
logger.info("=================================")
```

---

## 10. Error Handling Scenarios

```kotlin
// SCENARIO 1: CSV parsing error during read
override fun read(): RawMovieDto? {
    return try {
        csvParser.nextRecord?.let { record ->
            RawMovieDto(...)
        }
    } catch (e: IOException) {
        // Don't silence this - let Spring Batch handle it
        throw ItemReaderException("CSV parse failed", e)
    }
}

// SCENARIO 2: Invalid data during processing
override fun process(raw: RawMovieDto): Movie? {
    return when {
        raw.title.isBlank() -> {
            logger.warn("Skipping movie with blank title")
            null  // Skip this item gracefully
        }
        raw.releaseDate.isEmpty() -> {
            throw ProcessingException("Missing release date")  // Fail and retry
        }
        else -> Movie(...)  // Success
    }
}

// SCENARIO 3: Database constraint violation during write
override fun write(chunk: Chunk<out Movie>) {
    try {
        movieRepository.saveAll(chunk.items)
    } catch (e: DataIntegrityViolationException) {
        // If configured with .skip(), this item will be skipped
        // If configured with .retry(), entire chunk will be retried
        throw e
    }
}
```

---

## Summary: Processing Pipeline

```
CSV File
    ↓
ItemReader.read()
    ↓ (one movie at a time)
[Movie1, Movie2, ..., Movie10]
    ↓
ItemProcessor.process(each)
    ↓ (validate & transform)
[Movie1*, Movie2*, ..., Movie10*]
    ↓
Accumulate in chunk (when chunk.size == 10)
    ↓
ItemWriter.write(chunk)
    ↓ (batch insert to DB)
COMMIT
    ↓
Clear chunk, repeat
```

**When ItemReader returns null → Job Step completes**

---

**Key Files to Study in NetflixDB Repository:**
1. `ImportNetflixDataJobConfig.kt` → Job & step configuration
2. `NetflixMovieCsvItemReader.kt` → CSV reading
3. `MovieProcessor.kt` → Data transformation
4. `MovieDatabaseWriter.kt` → Database writes
5. `ExportSqlScriptsTasklet.kt` → SQL export logic
6. Entity classes under `src/main/kotlin/com/github/lerocha/netflixdb/entity`
7. `application.yml` → Batch configuration
