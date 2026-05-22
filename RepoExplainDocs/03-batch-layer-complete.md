# NetflixDB Batch Processing Layer - Complete Documentation

**Spring Batch Components, CSV Reading, Data Transformation, and Database Writing**

---

## Overview

The batch layer imports Netflix data from CSV reports into the database. It uses Spring Batch's chunk-oriented processing:
- **Read:** CSV lines from Netflix reports
- **Process:** Parse and transform into entities
- **Write:** Insert into database in batches

**Location:** `src/main/kotlin/com/github/lerocha/netflixdb/batch`

### Source code map (read this first)

| Concept in this guide | Actual Kotlin source |
|----------------------|----------------------|
| Job configuration class | `CreateNetflixDatabaseJobConfig` |
| Job bean / name | `createNetflixDatabaseJob()` (step names match `@Bean` method names via `callerBeanMethodName()`) |
| Excel row DTO | `ReportSheetRow` in `dto/ReportSheetRow.kt` |
| Staging maps | `movieRowsByTitleAndRuntime`, `seasonRowsByTitleAndRuntime` |
| Import writers | `accumulateReportRows()` |
| Entity builders | `movieProcessor`, `seasonProcessor` beans |
| SQL artifact path | `DataSourceProperties.artifactFilename()` → `build/artifacts/netflixdb-{profile}.sql` |

The illustrative snippets below use simplified names (`ImportNetflixDataJobConfig`, CSV readers) to teach Spring Batch patterns. Behavior and structure match the repository code above.

---

## 1. CreateNetflixDatabaseJobConfig: The Main Configuration

```kotlin
@Configuration
@EnableBatchProcessing
// Tutorial name; see CreateNetflixDatabaseJobConfig in the repo.
class ImportNetflixDataJobConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {
    
    // ============================================
    // ANNOTATION: @Configuration
    // ============================================
    // Purpose: This class defines Spring beans
    // Effect: Methods with @Bean are registered as beans
    
    // ============================================
    // ANNOTATION: @EnableBatchProcessing
    // ============================================
    // Purpose: Activates Spring Batch
    // Effect: Auto-configures:
    //   - JobRepository (metadata store)
    //   - JobLauncher (job executor)
    //   - JobBuilderFactory (for building jobs)
    //   - StepBuilderFactory (for building steps)
    
    // ============================================
    // THE JOB: Three-step import pipeline
    // ============================================
    @Bean
    fun importNetflixDataJob(
        importMoviesStep: Step,           // Injected by Spring
        importTvShowsStep: Step,          // Injected by Spring
        exportSqlStep: Step               // Injected by Spring
    ): Job {
        return JobBuilder("importNetflixDataJob", jobRepository)
            .start(importMoviesStep)      // Step 1: Always runs first
            .next(importTvShowsStep)      // Step 2: Runs after step 1 completes
            .next(exportSqlStep)          // Step 3: Runs after step 2 completes
            .build()
    }
    
    // ============================================
    // STEP 1: Import Movies from CSV
    // ============================================
    @Bean
    fun importMoviesStep(
        movieItemReader: ItemReader<RawMovieDto>,
        movieProcessor: ItemProcessor<RawMovieDto, Movie>,
        movieItemWriter: ItemWriter<Movie>
    ): Step {
        return StepBuilder("importMoviesStep", jobRepository)
            .<RawMovieDto, Movie>chunk(10, transactionManager)
            // Generic syntax: .<InputType, OutputType>chunk(size, txManager)
            // chunk(10) = read 10, process 10, write 10, then commit
            
            .reader(movieItemReader)      // CSV reader
            .processor(movieProcessor)    // Parser/validator
            .writer(movieItemWriter)      // Database writer
            
            // ========== Error Handling ==========
            .faultTolerant()              // Enable error handling
            .skip(DataAccessException::class.java)  // Skip DB errors
            .skipLimit(5)                 // Allow max 5 skips
            .retry(OptimisticLockingFailureException::class.java)
            .retryLimit(3)                // Retry 3 times on lock conflict
            
            .build()
    }
    
    // ============================================
    // STEP 2: Import TV Shows from CSV
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
    // STEP 3: Export SQL Scripts (Tasklet)
    // ============================================
    @Bean
    fun exportSqlStep(
        exportSqlTasklet: ExportSqlScriptsTasklet
    ): Step {
        return StepBuilder("exportSqlStep", jobRepository)
            .tasklet(exportSqlTasklet, transactionManager)
            // Tasklet: single operation, no Reader/Processor/Writer
            .build()
    }
}
```

**Key Annotations:**
- `@Configuration`: Spring discovers and loads this class
- `@EnableBatchProcessing`: Activates Spring Batch infrastructure
- `@Bean`: Method returns object registered as Spring bean
- `<Type1, Type2>chunk()`: Generic for input/output types

---

## 2. ItemReader: Reading from CSV

```kotlin
@Component
class NetflixMovieCsvItemReader(
    @Value("classpath:reports/netflix-movies.csv")
    private val resource: Resource,
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
) : ItemReader<RawMovieDto> {
    
    // ============================================
    // ANNOTATION: @Component
    // ============================================
    // Purpose: Auto-registers class as Spring bean
    // Spring discovers it and injects into MovieRepository
    
    // ============================================
    // ANNOTATION: @Value
    // ============================================
    // Purpose: Injects configuration values from properties/classpath
    // "classpath:" = from classpath (JAR/build/resources)
    // "file:" = from file system
    // "${property.name}" = from application.yml
    
    private lateinit var csvParser: CSVParser
    private var initialized = false
    
    override fun read(): RawMovieDto? {
        // Called repeatedly in chunk loop
        // Return one item per call
        // Return null when exhausted (EOF)
        
        if (!initialized) {
            init()
        }
        
        val record = csvParser.nextRecord
        
        return if (record != null) {
            // Parse CSV row → DTO
            RawMovieDto(
                imdbId = record.get("imdb_id"),
                title = record.get("title"),
                originalTitle = record.get("original_title"),
                releaseDate = record.get("release_date"),
                runtime = record.get("runtime")?.toIntOrNull(),
                countryList = record.get("country")?.split(",")?.map { it.trim() } ?: listOf(),
                locale = record.get("locale"),
                genreNames = record.get("genres")?.split(",")?.map { it.trim() } ?: listOf()
            )
        } else {
            // EOF: return null signals end of data
            logger.info("Finished reading movies from CSV")
            null
        }
    }
    
    private fun init() {
        // Lazy initialization on first read
        logger.info("Initializing CSV reader from ${resource.filename}")
        
        val reader = resource.inputStream.bufferedReader()
        
        csvParser = CSVFormat.DEFAULT
            .withFirstRecordAsHeader()    // Use first row as column names
            .parse(reader)
        
        initialized = true
    }
}

// DTO for raw CSV data (before validation)
data class RawMovieDto(
    val imdbId: String,
    val title: String,
    val originalTitle: String,
    val releaseDate: String,
    val runtime: Int?,
    val countryList: List<String>,
    val locale: String,
    val genreNames: List<String>
)
```

**CSV File Format:**
```csv
imdb_id,title,original_title,release_date,runtime,country,locale,genres
81145628,Damsel,Damsel,2024-03-07,98,Global,en,Fantasy|Adventure
81235234,Avatar: The Way of Water,Avatar: The Way of Water,2022-12-16,192,Global,en,Science Fiction|Action
81345345,The Iron Claw,The Iron Claw,2023-12-22,163,Global,en,Drama|Sport
...
```

**Execution Flow:**
```
Spring Batch Chunk Loop:
    for i in 1..10:
        item = reader.read()  ◄─── Called here
        if item is null:
            exit loop
        process(item)
        add to chunk
    write(chunk)
    commit()
    
reader.read() returns:
    Call 1: RawMovieDto(imdbId="81145628", title="Damsel", ...)
    Call 2: RawMovieDto(imdbId="81235234", title="Avatar", ...)
    Call 3: RawMovieDto(imdbId="81345345", title="Iron Claw", ...)
    ...
    Call N: null  ◄─── EOF, Spring Batch stops reading
```

---

## 3. ItemProcessor: Validation & Transformation

```kotlin
@Component
class MovieProcessor(
    private val genreRepository: GenreRepository,
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
) : ItemProcessor<RawMovieDto, Movie> {
    
    // ============================================
    // INTERFACE: ItemProcessor<Input, Output>
    // ============================================
    // Transforms one input item to output item
    // Generic: <RawMovieDto, Movie>
    
    override fun process(raw: RawMovieDto): Movie? {
        // Called once per item in chunk
        
        // ========== VALIDATION ==========
        if (raw.title.isBlank()) {
            logger.warn("Skipping movie with blank title: ${raw.imdbId}")
            return null  // null = skip this item (won't be written)
        }
        
        if (raw.releaseDate.isEmpty()) {
            // Throw exception = fail step (or retry if configured)
            throw ProcessingException("Movie ${raw.title} missing release_date")
        }
        
        if (raw.runtime == null || raw.runtime <= 0) {
            logger.warn("Invalid runtime for ${raw.title}, using default")
            // Continue with default value
        }
        
        // ========== TRANSFORMATION ==========
        // Parse date string
        val parsedDate = LocalDate.parse(
            raw.releaseDate,
            DateTimeFormatter.ISO_DATE
        )
        
        // Look up genres in database
        val genres = genreRepository.findByNameIn(raw.genreNames)
        
        // Build entity
        return Movie(
            id = raw.imdbId.toLong(),
            title = raw.title.trim(),
            originalTitle = raw.originalTitle.trim(),
            releaseDate = parsedDate,
            runtime = raw.runtime ?: 0,
            locale = raw.locale,
            isAvailableGlobally = raw.countryList.contains("Global"),
            genres = genres.toMutableSet()
        )
    }
}

class ProcessingException(message: String) : Exception(message)
```

**Three Outcomes:**
```
1. Return valid Movie
   → Item added to chunk
   → Will be written to database
   → No exception

2. Return null
   → Item skipped
   → Won't be written
   → Logged as warning
   → Chunk processing continues

3. Throw exception
   → If .faultTolerant() + .skip() configured:
     → Item skipped, skip count incremented
   → If .faultTolerant() + .retry() configured:
     → Entire chunk retried (up to retryLimit)
   → If not fault-tolerant:
     → Step fails immediately
```

---

## 4. ItemWriter: Batch Database Insert

```kotlin
@Component
class MovieDatabaseWriter(
    private val movieRepository: MovieRepository,
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
) : ItemWriter<Movie> {
    
    // ============================================
    // INTERFACE: ItemWriter<Type>
    // ============================================
    // Writes a batch (chunk) of items
    
    override fun write(chunk: Chunk<out Movie>) {
        // Called when chunk is full (e.g., 10 items)
        // Receives all items in one batch
        
        logger.info("Writing ${chunk.items.size} movies to database")
        
        try {
            // Batch insert (much faster than individual saves)
            val saved = movieRepository.saveAll(chunk.items)
            logger.info("Successfully saved ${saved.size} movies")
            
        } catch (e: DataIntegrityViolationException) {
            // Database constraint violation
            logger.error("Constraint violation saving movies", e)
            throw e  // Re-throw to trigger skip/retry logic
        } catch (e: Exception) {
            logger.error("Unexpected error saving movies", e)
            throw ItemWriterException("Failed to write movies", e)
        }
    }
}

// ============================================
// ANNOTATION: @Component
// ============================================
// Auto-registers as Spring bean
// Can be injected wherever needed
```

**Execution Flow:**
```
Chunk Loop:
    items = []
    for i in 1..10:
        item = processor.process(item)
        items.add(item)
    
    writer.write(Chunk(items))  ◄─── Called with all 10
        ↓
        movieRepository.saveAll(items)
        ↓
        JPA batch insert SQL
        ↓
        INSERT INTO movie VALUES (...), (...), (...)  ← Single SQL
        ↓
    COMMIT TRANSACTION
    ↓
    Clear items, repeat chunk loop
```

---

## 5. Tasklet: Atomic Single Operation

```kotlin
@Component
class ExportSqlScriptsTasklet(
    private val movieRepository: MovieRepository,
    private val tvShowRepository: TvShowRepository,
    private val seasonRepository: SeasonRepository,
    private val sqlExportService: SqlExportService,
    private val logger: Logger = LoggerFactory.getLogger(javaClass)
) : Tasklet {
    
    // ============================================
    // INTERFACE: Tasklet
    // ============================================
    // Executes a single, complete task
    // No Reader/Processor/Writer pattern
    
    override fun execute(
        contribution: StepContribution,  // Reports step status/metrics
        chunkContext: ChunkContext       // Access to job/step context
    ): RepeatStatus {
        // Called once per step execution
        
        logger.info("=== Starting SQL export ===")
        val startTime = System.currentTimeMillis()
        
        try {
            // ========== FETCH DATA ==========
            logger.info("Fetching data from database...")
            val movies = movieRepository.findAll()
            val tvShows = tvShowRepository.findAll()
            val seasons = seasonRepository.findAll()
            
            logger.info("Loaded: ${movies.size} movies, ${tvShows.size} shows, ${seasons.size} seasons")
            
            // ========== GENERATE SQL FOR EACH VENDOR ==========
            logger.info("Generating SQL scripts for 5 database vendors...")
            
            val mysqlScript = sqlExportService.generateMysql(movies, tvShows, seasons)
            val postgresScript = sqlExportService.generatePostgresql(movies, tvShows, seasons)
            val oracleScript = sqlExportService.generateOracle(movies, tvShows, seasons)
            val sqlServerScript = sqlExportService.generateSqlServer(movies, tvShows, seasons)
            val sqliteScript = sqlExportService.generateSqlite(movies, tvShows, seasons)
            
            // ========== WRITE FILES ==========
            logger.info("Writing SQL files to disk...")
            val outputDir = File("build/artifacts").apply { mkdirs() }
            
            writeFile(outputDir, "netflix-mysql.sql", mysqlScript)
            writeFile(outputDir, "netflix-postgresql.sql", postgresScript)
            writeFile(outputDir, "netflix-oracle.sql", oracleScript)
            writeFile(outputDir, "netflix-sqlserver.sql", sqlServerScript)
            writeFile(outputDir, "netflix-sqlite.sql", sqliteScript)
            
            // ========== METRICS ==========
            val duration = System.currentTimeMillis() - startTime
            contribution.incrementSummary("bytesWritten", mysqlScript.length.toLong())
            
            logger.info("SQL export completed in ${duration}ms")
            
            // RETURN FINISHED = success
            return RepeatStatus.FINISHED
            
        } catch (e: Exception) {
            logger.error("SQL export failed", e)
            // Set step to failed status
            contribution.setExitStatus(ExitStatus.FAILED)
            throw e  // Step will be marked FAILED
        }
    }
    
    private fun writeFile(dir: File, filename: String, content: String) {
        val file = File(dir, filename)
        file.writeText(content, Charsets.UTF_8)
        logger.info("Wrote ${file.absolutePath} (${content.length} bytes)")
    }
}
```

**Return Values:**
```
RepeatStatus.FINISHED
    → Tasklet execution successful
    → Step completes successfully
    → Continue to next step

RepeatStatus.CONTINUABLE
    → Tasklet will be called again
    → Rare, for multi-iteration tasklets
    → Call execute() again
    → Eventually return FINISHED

throw Exception
    → Execution failed
    → If exception not caught: Step FAILED
    → contribution.setExitStatus(ExitStatus.FAILED)
```

---

## 6. Job Execution Flow Annotations

```kotlin
// When job starts, Spring calls:

@Bean
fun importNetflixDataJob(...): Job {
    // JobBuilderFactory creates JobBuilder
    // JobBuilder creates Job bean
    // Job is registered in application context
}

// When application starts:
// 1. JobLauncher discovered (auto-configured by @EnableBatchProcessing)
// 2. Job discovered (importNetflixDataJob bean)
// 3. jobLauncher.run(job, jobParameters) called

// Spring Batch creates:
class JobExecution {
    val jobInstance: JobInstance    // Uniquely identifies this run
    val startTime: LocalDateTime
    var endTime: LocalDateTime?
    var status: BatchStatus         // STARTED, COMPLETED, FAILED, etc.
    var exitCode: String
    var exitMessage: String?
}

class StepExecution {
    val stepName: String            // e.g., "importMoviesStep"
    val readCount: Int              // Items read
    val writeCount: Int             // Items written
    val skipCount: Int              // Items skipped
    val readSkipCount: Int          // Skip during read
    val processSkipCount: Int       // Skip during process
    val writeSkipCount: Int         // Skip during write
    var status: BatchStatus
}
```

---

## 7. Spring Batch Configuration Properties

```yaml
# application.yml

spring:
  batch:
    job:
      enabled: true                 # Auto-run jobs on startup
      names: importNetflixDataJob  # Which jobs to run
    
    jdbc:
      initialize-database: always   # Create batch metadata tables
    
    # Job launcher thread pool
    threads:
      corePoolSize: 2               # Min threads
      maxPoolSize: 4                # Max threads
      queueCapacity: 100            # Queue size

  jpa:
    # Hibernate configuration
    hibernate:
      ddl-auto: create-drop         # Create fresh schema
    
    # Batch write optimization
    properties:
      hibernate:
        jdbc:
          batch_size: 20            # JPA batch size (should match chunk size)
          fetch_size: 20            # Fetch size for reads
        order_inserts: true         # Optimize insert order
        order_updates: true         # Optimize update order
```

---

## 8. Error Handling Strategies

### Strategy 1: Skip on Error
```kotlin
.faultTolerant()
.skip(DataAccessException::class.java)
.skipLimit(5)

// Result: If DB error, skip item (skip_count++), continue
// Step completes: STATUS = COMPLETED (not FAILED)
```

### Strategy 2: Retry on Error
```kotlin
.faultTolerant()
.retry(OptimisticLockingFailureException::class.java)
.retryLimit(3)

// Result: On lock conflict, retry 3 times
// If still fails: skip (if .skip() also configured) or fail
```

### Strategy 3: Fail Immediately
```kotlin
// No .faultTolerant() at all

// Result: First error → Step FAILED
// Can restart later with jobLauncher.run()
```

---

## 9. Chunk Configuration Reference

```kotlin
// Chunk size affects:

// SMALL CHUNKS (chunk = 5)
.chunk(5)
// ✅ Pros: Frequent commits (safer), fine-grained restart
// ❌ Cons: More transaction overhead, slower

// MEDIUM CHUNKS (chunk = 10-100)
.chunk(10)
// ✅ Balanced: Performance and safety

// LARGE CHUNKS (chunk = 1000)
.chunk(1000)
// ✅ Pros: High performance, fewer commits
// ❌ Cons: Memory pressure, coarser restart

// Recommendation for NetflixDB:
// chunk = 10 (good balance for ~1000-2000 items)
```

---

## 10. Complete Batch Processing Sequence

```
APPLICATION START
    ↓
Spring loads @EnableBatchProcessing
    ↓
JobRepository initialized
    ↓
@Bean methods called (in config class):
    - importNetflixDataJob created
    - importMoviesStep created
    - importTvShowsStep created
    - exportSqlStep created
    ↓
JobLauncher discovered and started
    ↓
jobLauncher.run(importNetflixDataJob, jobParameters)
    ↓
STEP 1: importMoviesStep starts
    │
    ├─→ StepExecution created
    ├─→ Resources opened (CSV file, DB connection)
    │
    └─→ CHUNK LOOP (repeats until EOF):
        ├─→ Chunk = []
        │
        ├─→ For i = 1..10:
        │   ├─→ movieItemReader.read()
        │   │   ├─→ Parse CSV line
        │   │   └─→ Return RawMovieDto
        │   │
        │   ├─→ movieProcessor.process(RawMovieDto)
        │   │   ├─→ Validate data
        │   │   ├─→ Transform to Movie
        │   │   └─→ Return Movie (or null to skip)
        │   │
        │   └─→ Chunk.add(Movie)
        │
        ├─→ When chunk.size == 10:
        │   ├─→ movieItemWriter.write(Chunk)
        │   │   └─→ movieRepository.saveAll(10 movies)
        │   │
        │   └─→ COMMIT TRANSACTION
        │
        └─→ When movieItemReader.read() returns null:
            └─→ Exit chunk loop
    │
    ├─→ StepExecution.readCount = 1247
    ├─→ StepExecution.writeCount = 1247
    ├─→ StepExecution.skipCount = 2
    └─→ StepExecution.status = COMPLETED
    
STEP 2: importTvShowsStep (same pattern)
    │
    └─→ Status: COMPLETED
    
STEP 3: exportSqlStep starts
    │
    ├─→ StepExecution created
    ├─→ ExportSqlScriptsTasklet.execute() called (once)
    │   ├─→ Fetch all data from DB
    │   ├─→ Generate 5 SQL files
    │   ├─→ Write files to disk
    │   └─→ Return RepeatStatus.FINISHED
    │
    └─→ StepExecution.status = COMPLETED

JOB COMPLETION
    ├─→ JobExecution.status = COMPLETED
    ├─→ JobRepository saves metadata
    └─→ Application continues running
```

---

## 11. Key Annotations Summary

| Annotation | Class | Purpose |
|-----------|-------|---------|
| @Configuration | Class | Spring bean configuration |
| @EnableBatchProcessing | Class | Activate Spring Batch |
| @Bean | Method | Register as Spring bean |
| @Component | Class | Auto-register as Spring bean |
| @Value | Field | Inject configuration value |
| @Repository | Interface | Mark as data access layer |
| @Transactional | Method | Wrap in transaction |
| @Modifying | Method | For DELETE/UPDATE queries |
| @Query | Method | Custom JPQL query |
| @Param | Parameter | Named query parameter |

---

## 12. Common Issues & Solutions

### Issue 1: Duplicate Inserts on Restart
**Cause:** Job restarted without checkpoint tracking
**Solution:** JobRepository tracks progress, restart skips completed chunks

### Issue 2: N+1 Query Problem
**Cause:** Loading related entities (e.g., genres) one-by-one
**Solution:** Use fetch join or @EntityGraph in queries

### Issue 3: Memory Exhaustion
**Cause:** Chunk size too large
**Solution:** Reduce chunk size (e.g., from 1000 to 10)

### Issue 4: Slow Inserts
**Cause:** Individual inserts instead of batch
**Solution:** Use `saveAll()` and set `hibernate.jdbc.batch_size`

### Issue 5: Deadlocks in Database
**Cause:** Concurrent write conflicts
**Solution:** Reduce chunk size, increase `retryLimit`, add index to frequently locked columns

---

**Used in:** Data import from CSV reports, multi-format SQL export
