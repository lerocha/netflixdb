# Spring Batch Explained

## What is Spring Batch?

Spring Batch is a framework for **processing large amounts of data** in a controlled, structured way.

**Problem it solves:**
```kotlin
// ❌ Without Spring Batch - Manual, error-prone
val lines = readAllCsvLines()  // Load 1000+ lines into memory
for (line in lines) {
    val movie = parseMovie(line)
    try {
        saveToDatabase(movie)
    } catch (e: Exception) {
        // Where did you leave off? How do you retry?
    }
}

// ✅ With Spring Batch - Structured, fault-tolerant
val job = jobBuilder
    .start(importMoviesStep)
    .next(exportSqlStep)
    .build()
jobLauncher.run(job, jobParameters)
```

## Key Components

### Job
**What:** Container for ordered steps
**Purpose:** Defines complete batch process
```kotlin
@Bean
fun importNetflixDataJob(): Job {
    return jobBuilder
        .start(step1)
        .next(step2)
        .next(step3)
        .build()
}
```

### Step
**What:** Single phase in a job
**Two types:**
1. **Chunk-oriented** - Read/Process/Write pattern
2. **Tasklet** - Single atomic operation

### ItemReader
**What:** Reads one item at a time
**Purpose:** Input source (CSV, database, etc.)

```kotlin
class MovieCsvItemReader : ItemReader<Movie> {
    override fun read(): Movie? {
        val line = csvFile.readLine() ?: return null  // null = EOF
        return parseMovie(line)
    }
}
```

### ItemProcessor
**What:** Transforms one item to another
**Purpose:** Validation, transformation

```kotlin
class MovieProcessor : ItemProcessor<RawMovie, Movie> {
    override fun process(raw: RawMovie): Movie? {
        if (raw.title.isBlank()) return null  // Skip invalid
        return Movie(...)
    }
}
```

### ItemWriter
**What:** Writes batch of items
**Purpose:** Persistence

```kotlin
class MovieWriter : ItemWriter<Movie> {
    override fun write(chunk: Chunk<out Movie>) {
        movieRepository.saveAll(chunk.items)  // Batch insert
    }
}
```

### Tasklet
**What:** Single operation
**Purpose:** Setup/cleanup/export

```kotlin
class ExportTasklet : Tasklet {
    override fun execute(...): RepeatStatus {
        // Generate SQL files
        return RepeatStatus.FINISHED
    }
}
```

## Chunk-Oriented Processing

**Chunk = batch of items processed together**

```
ChunkSize = 10:

┌─────────────────────────────────────┐
│  Chunk Loop                         │
├─────────────────────────────────────┤
│                                     │
│  for i in 1..10:                   │
│    item = reader.read()            │
│    processed = processor.process()  │
│    add to chunk                    │
│                                     │
│  when chunk is full:               │
│    writer.write(chunk)             │ ← All 10 at once
│    COMMIT TRANSACTION              │
│    clear chunk                     │
│                                     │
│  when reader returns null:         │
│    exit loop                       │
│                                     │
└─────────────────────────────────────┘
```

**Memory efficiency:**
- Only 10 items in memory (not 1000+)
- Automatic batch writes (faster)
- Automatic transaction management (ACID)

## Error Handling

### Skip on Error
```kotlin
.faultTolerant()
.skip(DataAccessException::class.java)
.skipLimit(5)

// If DB error: skip item, continue
// Step completes: STATUS = COMPLETED (not failed)
```

### Retry on Error
```kotlin
.faultTolerant()
.retry(OptimisticLockingFailureException::class.java)
.retryLimit(3)

// On lock error: retry 3 times
// If still fails: skip (if configured)
```

### Fail Immediately
```kotlin
// No .faultTolerant()

// First error: Step FAILED
// Can restart later without re-processing
```

## JobRepository

**Metadata store** - tracks execution details

```
job_instance
├── job_name = "importNetflixDataJob"
├── version = 0

job_execution
├── status = "COMPLETED"
├── start_time = 2024-06-15 10:30:00
├── end_time = 2024-06-15 10:35:00

step_execution
├── step_name = "importMoviesStep"
├── read_count = 1247
├── write_count = 1247
├── skip_count = 2
```

**Why it matters:**
- Track progress
- Enable restart from last checkpoint
- Query execution history

## NetflixDB Job Structure

```
ImportNetflixDataJob
├── Step 1: importMoviesStep (chunk-oriented)
│   ├── Reader: Read from netflix-movies.csv
│   ├── Processor: Validate and transform
│   └── Writer: Insert into database
│
├── Step 2: importTvShowsStep (chunk-oriented)
│   ├── Reader: Read from netflix-tvshows.csv
│   ├── Processor: Validate and transform
│   └── Writer: Insert into database
│
└── Step 3: exportSqlStep (tasklet)
    └── Task: Generate SQL files for 5 vendors
```

## Configuration

```yaml
spring:
  batch:
    job:
      enabled: true                    # Auto-run on startup
      names: importNetflixDataJob     # Which job to run
    
    jdbc:
      initialize-database: always      # Create batch tables
    
    threads:
      corePoolSize: 2
      maxPoolSize: 4

  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 20              # Must match chunk size
        order_inserts: true           # Optimize inserts
```

## Complete Execution Flow

```
App Start
  ↓
Spring Batch initialized
  ↓
JobLauncher.run(job)
  ↓
Step 1: importMoviesStep
  ├─ Open reader (CSV file)
  ├─ Chunk loop (10 items per iteration)
  │   ├─ Read 10 movies
  │   ├─ Process 10 movies
  │   ├─ Write 10 to database
  │   └─ COMMIT
  ├─ Repeat until EOF
  └─ Close reader
  ↓
Step 2: importTvShowsStep (same pattern)
  ↓
Step 3: exportSqlStep
  ├─ ExportTasklet.execute()
  ├─ Read all from database
  ├─ Generate 5 SQL files
  └─ Return FINISHED
  ↓
Job COMPLETED
  ↓
Metadata saved to JobRepository
  ↓
Application continues running
```

## Best Practices

✅ Use appropriate chunk size (10-100)
✅ Configure fault tolerance for resilience
✅ Monitor JobRepository for execution history
✅ Use TaskLet for atomic operations
✅ Use Chunk for item-by-item processing

❌ Don't forget @EnableBatchProcessing
❌ Don't ignore error handling
❌ Don't use huge chunks (memory issues)
❌ Don't skip JobRepository monitoring
