# Spring Batch Deep Dive: Understanding NetflixDB

**Target Audience:** Backend engineers wanting to understand the batch data import pipeline in the NetflixDB repository.

---

## Table of Contents

1. [Core Concepts](#core-concepts)
2. [Architecture Overview](#architecture-overview)
3. [Two Processing Approaches: Chunks vs Tasklets](#two-processing-approaches-chunks-vs-tasklets)
4. [How NetflixDB Uses Spring Batch](#how-netflixdb-uses-spring-batch)
5. [Execution Flow Diagram](#execution-flow-diagram)
6. [Key Components Deep Dive](#key-components-deep-dive)
7. [JobRepository & Metadata](#jobrepository--metadata)
8. [Common Patterns & Best Practices](#common-patterns--best-practices)

---

## Core Concepts

### What is Spring Batch?

Spring Batch is a lightweight framework for implementing robust, scalable batch applications. In the context of NetflixDB:

**Purpose:** Read Netflix engagement data from Excel reports → Transform → Insert into database → Export to multi-vendor SQL scripts

**Implementation:** `src/main/kotlin/com/github/lerocha/netflixdb/batch/CreateNetflixDatabaseJobConfig.kt`

**Why Spring Batch?**
- Handles large data volumes efficiently
- Built-in transaction management and error recovery
- Restart capability (if a job fails, restart from the failed step)
- Logging, tracing, and statistics collection
- Skip & retry logic for fault tolerance

### The Problem It Solves

Without Spring Batch, you'd manually write:
```kotlin
// ❌ Without Spring Batch - Manual and error-prone
val lines = readCsvFile("netflix-report.csv")
for (line in lines) {
    try {
        val movie = parseMovie(line)
        saveToDatabase(movie)
        commitTransaction()
    } catch (e: Exception) {
        // How do you retry? Skip? Where did you leave off?
        handleError(e)
    }
}
```

**With Spring Batch:**
```kotlin
// ✅ With Spring Batch - Structured, reusable, fault-tolerant
val job = jobBuilderFactory.get("importNetflixDataJob")
    .start(importMoviesStep())  // Step 1
    .next(importTvShowsStep())  // Step 2
    .next(exportSqlStep())      // Step 3
    .build()
```

---

## Architecture Overview

### The Hierarchy

```
Job (ImportNetflixDataJob)
├── Step 1: Import Movies
│   ├── ItemReader (reads CSV lines)
│   ├── ItemProcessor (transforms to Movie entity)
│   └── ItemWriter (saves to DB in chunks)
├── Step 2: Import TV Shows
│   ├── ItemReader
│   ├── ItemProcessor
│   └── ItemWriter
└── Step 3: Export SQL Scripts
    └── Tasklet (single task - generate SQL)
```

### Entity Relationship

```
┌─────────────────┐
│  JobLauncher    │ Starts the job
└────────┬────────┘
         │
         ↓
┌─────────────────┐
│   Job           │ Contains ordered steps
└────────┬────────┘
         │
         ├─→ Step 1 ──→ Chunk-oriented (Reader → Processor → Writer)
         ├─→ Step 2 ──→ Chunk-oriented
         └─→ Step 3 ──→ Tasklet (single action)
```

### Key Components

| Component | Role | NetflixDB Example |
|-----------|------|-------------------|
| **Job** | Container for related steps | `ImportNetflixDataJob` |
| **Step** | Independent, sequential phase | "Import movies," "Export SQL" |
| **ItemReader** | Reads data one item at a time | `CsvFileItemReader` for Netflix reports |
| **ItemProcessor** | Transforms/validates items | Convert CSV row → Movie entity |
| **ItemWriter** | Writes chunk of items | Saves 10 movies to DB (then commit) |
| **Tasklet** | Single task (setup/cleanup) | Generate and export SQL scripts |
| **JobRepository** | Persists job metadata | Stores execution status, restartability |
| **JobLauncher** | Executes the job | Triggered at application startup |

---

## Two Processing Approaches: Chunks vs Tasklets

Spring Batch offers **two fundamentally different** ways to implement a Step.

### 1. Chunk-Oriented Processing

**Use Case:** Processing large volumes of data (like Netflix reports with 1000s of movies).

**How It Works:**

```
Commit Interval = 10 (example)

Loop:
  1. Read item 1 from ItemReader
  2. Process item 1 with ItemProcessor
  3. Add to chunk list
  4. Repeat steps 1-3 until 10 items collected
  5. Write all 10 items via ItemWriter (single batch operation)
  6. COMMIT TRANSACTION
  7. Clear chunk list, repeat loop
```

**Visual:**
```
Read → Process → Read → Process → Read → Process → Read → Process → Read → Process
↓      ↓         ↓      ↓         ↓      ↓         ↓      ↓         ↓      ↓
[Item1, Item2, Item3, Item4, Item5, Item6, Item7, Item8, Item9, Item10]
                                    ↓
                            Write all 10 to DB
                                    ↓
                            COMMIT TRANSACTION
```

**Code Example:**
```kotlin
@Bean
fun importMoviesStep(
    jobRepository: JobRepository,
    transactionManager: PlatformTransactionManager,
    movieReader: ItemReader<Movie>,
    movieProcessor: ItemProcessor<Movie, Movie>,
    movieWriter: ItemWriter<Movie>
): Step {
    return StepBuilder("importMovies", jobRepository)
        .<Movie, Movie> chunk(10, transactionManager)  // Commit every 10 items
        .reader(movieReader)
        .processor(movieProcessor)
        .writer(movieWriter)
        .build()
}
```

**Advantages:**
- ✅ High performance (batch writes vs individual writes)
- ✅ Memory efficient (only chunk in memory)
- ✅ Built-in transaction management
- ✅ Ideal for large datasets

---

### 2. Tasklet Approach

**Use Case:** Single, atomic tasks like setup/cleanup or exporting data.

**How It Works:**

```
Execute tasklet.execute() method once
(or repeatedly until it returns RepeatStatus.FINISHED)
```

**Code Example:**
```kotlin
@Component
class ExportSqlScriptsTasklet : Tasklet {
    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext
    ): RepeatStatus {
        // Generate SQL scripts for MySQL, PostgreSQL, Oracle, etc.
        val mysqlScript = generateMysqlScript()
        val postgresScript = generatePostgresScript()
        
        // Write to files
        writeSqlFile("netflix-mysql.sql", mysqlScript)
        writeSqlFile("netflix-postgres.sql", postgresScript)
        
        return RepeatStatus.FINISHED  // Execution complete
    }
}
```

**Tasklet in Job Config:**
```kotlin
@Bean
fun exportSqlStep(
    jobRepository: JobRepository,
    transactionManager: PlatformTransactionManager,
    exportTasklet: ExportSqlScriptsTasklet
): Step {
    return StepBuilder("exportSql", jobRepository)
        .tasklet(exportTasklet, transactionManager)
        .build()
}
```

**Advantages:**
- ✅ Simple for single operations
- ✅ Full control over logic
- ✅ Good for cleanup/setup tasks

**Disadvantages:**
- ❌ Not ideal for processing millions of records
- ❌ Must manage transactions manually if needed
- ❌ No built-in chunking/batching

---

## How NetflixDB Uses Spring Batch

### The CreateNetflixDatabaseJobConfig

NetflixDB uses Spring Batch for this workflow:

```
┌──────────────────────────────────────────────────────────────┐
│                 ImportNetflixDataJobConfig                    │
└──────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┼───────────────┐
              ↓               ↓               ↓
        ┌──────────┐  ┌──────────────┐  ┌────────────┐
        │  Step 1  │→ │   Step 2     │→ │   Step 3   │
        └──────────┘  └──────────────┘  └────────────┘
        
Import Movies  Import TV Shows  Export SQL Scripts
(Chunk-based)  (Chunk-based)    (Tasklet)
```

### Example Flow

**1. Application Startup:**
```
SpringBoot Application Starts
    ↓
@SpringBootApplication detects @EnableBatchProcessing
    ↓
JobLauncher is initialized
    ↓
ImportNetflixDataJob is loaded (ImportNetflixDataJobConfig bean)
    ↓
jobLauncher.run() is called → Job execution begins
```

**2. Step 1: Import Movies**
```
MovieCsvItemReader.read()
    ↓ (reads line from Netflix-movies.csv)
    ↓ movie_id,title,release_date,runtime
    ↓ 81145628,"Damsel",2024-03-07,98
    ↓
MovieProcessor.process(raw movie data)
    ↓ (validate, transform, set defaults)
    ↓
Add to chunk → [Movie1, Movie2, ..., Movie10]
    ↓
When chunk reaches 10 items:
    ↓
MovieWriter.write([Movie1-Movie10])
    ↓ (JPA batch save)
    ↓
COMMIT TRANSACTION
    ↓
Clear chunk, repeat
```

**3. Step 2: Import TV Shows**
```
Same pattern as Step 1
```

**4. Step 3: Export SQL Scripts**
```
ExportSqlScriptsTasklet.execute()
    ↓
Read all data from database
    ↓
Generate SQL CREATE TABLE statements
Generate SQL INSERT statements
    ↓
Write to:
  - netflix-mysql.sql
  - netflix-postgresql.sql
  - netflix-oracle.sql
  - netflix-sqlserver.sql
  - netflix-sqlite.sql
    ↓
Return RepeatStatus.FINISHED
```

---

## Execution Flow Diagram

### Complete Sequence

```
APPLICATION START
    │
    ├─→ Spring loads @EnableBatchProcessing
    │
    ├─→ JobRepository initialized (metadata store)
    │
    ├─→ JobLauncher created
    │
    ├─→ ImportNetflixDataJob loaded from config
    │
    └─→ jobLauncher.run(job, jobParameters)
        │
        ├─→ JobExecution created
        │   (unique run identifier + status tracking)
        │
        ├─→ STEP 1: importMoviesStep()
        │   │
        │   ├─→ StepExecution created
        │   │
        │   ├─→ Open reader, processor, writer
        │   │
        │   └─→ CHUNK LOOP:
        │       ├─→ While data available:
        │       │   ├─ Read 10 movies from CSV
        │       │   ├─ Process each one
        │       │   ├─ Write 10 to DB
        │       │   └─ COMMIT
        │       │
        │       └─→ When reader returns null (EOF):
        │           └─ Step execution complete
        │
        ├─→ STEP 2: importTvShowsStep()
        │   (same pattern as Step 1)
        │
        ├─→ STEP 3: exportSqlStep()
        │   │
        │   ├─→ StepExecution created
        │   │
        │   └─→ ExportSqlScriptsTasklet.execute()
        │       ├─→ Generate SQL files
        │       ├─→ Write to disk
        │       └─→ Return RepeatStatus.FINISHED
        │
        └─→ JobExecution.setStatus(COMPLETED)
            │
            └─→ All metadata saved to JobRepository
```

---

## Key Components Deep Dive

### 1. ItemReader<T>

**Interface:**
```kotlin
interface ItemReader<T> {
    fun read(): T?  // Returns next item or null when exhausted
}
```

**In NetflixDB:**
```kotlin
// Pseudo-code based on typical implementation
class NetflixCsvItemReader : ItemReader<Movie> {
    private val reader = BufferedReader(FileReader("netflix-movies.csv"))
    private val csvParser = CSVParser(reader)
    
    override fun read(): Movie? {
        val record = csvParser.nextRecord() ?: return null  // EOF
        return Movie(
            id = record[0].toLong(),
            title = record[1],
            releaseDate = LocalDate.parse(record[2]),
            runtime = record[3].toInt()
        )
    }
}
```

**Key Points:**
- Called repeatedly in a loop
- Returns one item per call
- Returns `null` when data exhausted
- Spring Batch calls this in a loop until it gets `null`

---

### 2. ItemProcessor<I, O>

**Interface:**
```kotlin
interface ItemProcessor<I, O> {
    fun process(item: I): O?  // Transform input to output (or null to skip)
}
```

**In NetflixDB:**
```kotlin
@Component
class MovieProcessor : ItemProcessor<RawMovieDto, Movie> {
    
    override fun process(rawMovie: RawMovieDto): Movie {
        // Validate
        if (rawMovie.title.isBlank()) {
            throw IllegalArgumentException("Title cannot be blank")
        }
        
        // Transform
        return Movie(
            id = rawMovie.imdbId.toLong(),
            title = rawMovie.title.trim(),
            releaseDate = parseDate(rawMovie.releaseDate),
            runtime = rawMovie.runtime ?: 0,
            isAvailableGlobally = rawMovie.countryList.contains("Global")
        )
    }
}
```

**Key Points:**
- Transforms one item to another
- Can return `null` to skip the item (won't be written)
- Can throw exception to skip with error handling
- Called once per item in chunk

---

### 3. ItemWriter<T>

**Interface:**
```kotlin
interface ItemWriter<T> {
    fun write(chunk: Chunk<out T>)  // Write all items in chunk
}
```

**In NetflixDB:**
```kotlin
@Component
class MovieDatabaseWriter(
    private val movieRepository: MovieRepository
) : ItemWriter<Movie> {
    
    override fun write(chunk: Chunk<out Movie>) {
        // Save all 10 (or however many) movies at once
        movieRepository.saveAll(chunk.items)
    }
}
```

**Key Points:**
- Receives a batch (Chunk) of items
- Writes all at once (batch operation)
- More efficient than writing one-by-one
- Called every commit interval (e.g., every 10 items)

---

### 4. Tasklet

**Interface:**
```kotlin
interface Tasklet {
    fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext
    ): RepeatStatus
}
```

**In NetflixDB (Export SQL):**
```kotlin
@Component
class ExportSqlScriptsTasklet(
    private val sqlExportService: SqlExportService
) : Tasklet {
    
    override fun execute(
        contribution: StepContribution,
        chunkContext: ChunkContext
    ): RepeatStatus {
        logger.info("Starting SQL export...")
        
        // Get all data from database
        val movies = movieRepository.findAll()
        val tvShows = tvShowRepository.findAll()
        
        // Generate SQL for each vendor
        val mysqlSql = sqlExportService.generateMysql(movies, tvShows)
        val postgresqlSql = sqlExportService.generatePostgresql(movies, tvShows)
        val oracleSql = sqlExportService.generateOracle(movies, tvShows)
        val sqlserverSql = sqlExportService.generateSqlserver(movies, tvShows)
        val sqliteSql = sqlExportService.generateSqlite(movies, tvShows)
        
        // Write files
        writeFile("build/artifacts/netflix-mysql.sql", mysqlSql)
        writeFile("build/artifacts/netflix-postgresql.sql", postgresqlSql)
        writeFile("build/artifacts/netflix-oracle.sql", oracleSql)
        writeFile("build/artifacts/netflix-sqlserver.sql", sqlserverSql)
        writeFile("build/artifacts/netflix-sqlite.sql", sqliteSql)
        
        logger.info("SQL export complete!")
        return RepeatStatus.FINISHED
    }
}
```

**Key Points:**
- Executes a single task
- Full control over logic
- Returns `RepeatStatus.FINISHED` when done
- No Reader/Processor/Writer needed

---

## JobRepository & Metadata

### What It Tracks

Spring Batch maintains metadata about every job execution:

```
job_instance
├── job_name = "ImportNetflixDataJob"
├── job_key = "<hash of parameters>"
└── version = 0

job_execution
├── job_instance_id = 1
├── version = 1
├── start_time = 2024-06-15 10:30:00
├── end_time = 2024-06-15 10:35:00
├── status = "COMPLETED"
├── exit_code = "COMPLETED"
└── exit_message = null

step_execution
├── job_execution_id = 1
├── step_name = "importMoviesStep"
├── start_time = 2024-06-15 10:30:00
├── end_time = 2024-06-15 10:32:00
├── status = "COMPLETED"
├── read_count = 500        ← How many items read
├── write_count = 500       ← How many items written
└── skip_count = 0          ← How many items skipped
```

### Why It Matters

**Restart Capability:**
```
Job Run 1: Imported 1-500 movies, failed on 501
  → Metadata saved: completed up to item 500

Job Run 2: Resume from item 501
  → No re-reading items 1-500
  → Huge time savings for large jobs
```

### In NetflixDB

NetflixDB uses an **in-memory or persistent JobRepository** (configured in application.yml):

```yaml
spring:
  batch:
    job:
      enabled: true  # Auto-run jobs on startup
    jdbc:
      initialize-database: always  # Create batch tables if not exist
```

---

## Common Patterns & Best Practices

### Pattern 1: Multi-Database Export

**Problem:** Netflix data needs to work with MySQL, PostgreSQL, Oracle, SQL Server, SQLite.

**Solution:** Use a Tasklet to generate vendor-specific SQL

```kotlin
@Bean
fun exportSqlStep(): Step {
    return StepBuilder("exportSql", jobRepository)
        .tasklet(ExportSqlScriptsTasklet(), transactionManager)
        .build()
}

class ExportSqlScriptsTasklet : Tasklet {
    override fun execute(...): RepeatStatus {
        // Single source of data
        val data = movieRepository.findAll()
        
        // Multiple output formats
        writeFile("netflix-mysql.sql", SqlGenerator.forMysql(data))
        writeFile("netflix-postgres.sql", SqlGenerator.forPostgres(data))
        writeFile("netflix-oracle.sql", SqlGenerator.forOracle(data))
        writeFile("netflix-sqlserver.sql", SqlGenerator.forSqlserver(data))
        writeFile("netflix-sqlite.sql", SqlGenerator.forSqlite(data))
        
        return RepeatStatus.FINISHED
    }
}
```

---

### Pattern 2: Chunk-Based Import

**Problem:** Netflix reports have thousands of movies/shows. Reading all into memory would crash.

**Solution:** Process in chunks (e.g., 10 at a time)

```kotlin
@Bean
fun importMoviesStep(): Step {
    return StepBuilder("importMovies", jobRepository)
        .<RawMovieDto, Movie> chunk(10)  // Process 10 at a time
        .reader(movieCsvReader())
        .processor(movieProcessor())
        .writer(movieDatabaseWriter())
        .build()
}
```

**Benefits:**
- Only 10 movies in memory at once (not all 50,000)
- Database batch writes (faster than inserts one-by-one)
- Automatic commit/rollback per chunk
- Easy to restart from failed chunk

---

### Pattern 3: Error Handling

```kotlin
@Bean
fun importMoviesStep(): Step {
    return StepBuilder("importMovies", jobRepository)
        .<RawMovieDto, Movie> chunk(10)
        .reader(movieCsvReader())
        .processor(movieProcessor())
        .writer(movieDatabaseWriter())
        .faultTolerant()
        .skip(DataAccessException::class.java)  // Skip on DB errors
        .skipLimit(5)                            // Allow up to 5 skips
        .retry(OptimisticLockingFailureException::class.java)
        .retryLimit(3)                           // Retry 3 times on lock fail
        .build()
}
```

---

### Pattern 4: Flow Control

```kotlin
@Bean
fun importNetflixDataJob(): Job {
    return JobBuilder("importNetflixDataJob", jobRepository)
        .start(importMoviesStep())      // Step 1
        .next(importTvShowsStep())      // Step 2 (after Step 1)
        .next(importSeasonsStep())      // Step 3 (after Step 2)
        .next(exportSqlStep())          // Step 4 (after Step 3)
        .build()
}
```

**Flow Options:**
- `.next(step)` → Always continue to next step
- `.on("COMPLETED").to(step)` → Conditional flow based on exit status
- `.end()` → Stop execution
- `.fail()` → Mark job as failed

---

### Pattern 5: Job Parameters

```kotlin
// Pass parameters at runtime
val jobParameters = JobParameters(
    mapOf(
        "reportDate" to JobParameter("2024-06-15", true),
        "region" to JobParameter("US", true)
    )
)

jobLauncher.run(job, jobParameters)
```

**Access in Tasklet:**
```kotlin
class MovieProcessor : ItemProcessor<RawMovieDto, Movie> {
    
    @Value("#{jobParameters['reportDate']}")
    lateinit var reportDate: String
    
    override fun process(item: RawMovieDto): Movie {
        return Movie(
            ...
            reportDate = LocalDate.parse(reportDate)
        )
    }
}
```

---

## NetflixDB Architecture Summary

### The Complete Picture

```
┌─────────────────────────────────────────────────────────────┐
│                  Spring Boot Application                     │
│               (ImportNetflixDataJobConfig)                   │
└─────────────────────────────────────────────────────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ↓                  ↓                  ↓
┌─────────────────┐ ┌─────────────────┐ ┌──────────────────┐
│  Step 1         │ │  Step 2         │ │  Step 3          │
│ Movies Import   │ │ TV Shows Import │ │ SQL Export       │
│ (Chunk-based)   │ │ (Chunk-based)   │ │ (Tasklet)        │
└─────────────────┘ └─────────────────┘ └──────────────────┘
        │                   │                      │
        └───────────────────┴──────────────────────┘
                            │
                            ↓
                   ┌─────────────────┐
                   │  JobRepository  │
                   │  (Metadata)     │
                   └─────────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         │                  │                  │
         ↓                  ↓                  ↓
    ┌─────────┐      ┌───────────┐      ┌─────────┐
    │ Movies  │      │ TV Shows  │      │ Seasons │
    │Database │      │ Database  │      │Database │
    └─────────┘      └───────────┘      └─────────┘
         │                  │                  │
         └───────────────────┴──────────────────┘
                            │
                            ↓
              ┌─────────────────────────────┐
              │  SQL File Generator         │
              │  (Netflix-mysql.sql)        │
              │  (Netflix-postgres.sql)     │
              │  (Netflix-oracle.sql)       │
              │  (Netflix-sqlserver.sql)    │
              │  (Netflix-sqlite.sql)       │
              └─────────────────────────────┘
```

---

## Quick Reference: When to Use What

| Use Case | Use This | Why |
|----------|----------|-----|
| Read CSV, transform, insert into DB | Chunk-oriented | Efficient for large datasets |
| Setup/cleanup before/after job | Tasklet | Simple, one-off action |
| Generate SQL files from data | Tasklet | Atomic operation, no chunking needed |
| Process millions of records | Chunk-oriented | Memory efficient, batch writes |
| Single database operation | Tasklet | No need for reader/processor/writer |
| Conditional logic between steps | Job with transitions | Flow control needed |

---

## Key Takeaways for Understanding NetflixDB

1. **NetflixDB uses Spring Batch because:**
   - It needs to import data from multiple CSV files
   - Data volume is too large for in-memory processing
   - Multi-step pipeline (import → validate → export)
   - Fault tolerance and restart capability needed

2. **The job structure:**
   - Step 1 & 2: Chunk-oriented (CSV → DB)
   - Step 3: Tasklet (DB → multi-vendor SQL)

3. **Why chunks over tasklets:**
   - Chunk gives you Reader/Processor/Writer pattern
   - Automatic batch writes and commits
   - Memory efficient (only chunk in memory)

4. **Why tasklet for export:**
   - Single operation: "export all data to SQL files"
   - Not processing individual items
   - Simple, atomic task

5. **JobRepository importance:**
   - Tracks execution metadata
   - Enables restart from last successful chunk
   - Critical for long-running, fault-prone jobs

---

## Next Steps

To deepen understanding:

1. Read `CreateNetflixDatabaseJobConfig.kt` in the repository (helpers: `accumulateReportRows`, `mapEngagementReportRow`, `mapTop10ListRow`, `callerBeanMethodName`)
2. Study the `MovieCsvItemReader`, `MovieProcessor`, `MovieDatabaseWriter` implementations
3. Review the `ExportSqlScriptsTasklet` implementation
4. Experiment with modifying chunk sizes and observing performance
5. Check the JobRepository tables to see execution metadata

---

**Created for:** Understanding NetflixDB architecture  
**Language:** Kotlin/Spring Boot  
**Framework:** Spring Batch 5.x  
**Target Audience:** Senior Backend Engineers
