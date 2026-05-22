# Spring Batch: Visual Decisions & Comparisons

Quick reference for understanding Spring Batch decisions in NetflixDB

---

## 1. Chunk vs Tasklet Decision Tree

```
┌─────────────────────────────────────┐
│  What does this step need to do?    │
└────────────┬────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
    ▼                 ▼
┌──────────────┐  ┌──────────────────────┐
│Process many  │  │Single atomic action? │
│individual    │  │(setup/cleanup/etc)   │
│items?        │  └──────────────────────┘
└──────────────┘             │
    │                        ▼
    │                    USE TASKLET
    │                        │
    │                ┌───────┴────────┐
    │                │                │
    │            ┌───▼────┐       ┌───▼────┐
    │            │ Clean  │       │ Export │
    │            │ up old │       │ data   │
    │            │ files  │       │ to SQL │
    │            └────────┘       └────────┘
    │
    ▼
USE CHUNK-ORIENTED
(ItemReader → ItemProcessor → ItemWriter)
    │
    ├─→ Memory efficient (chunk size at a time)
    ├─→ Automatic transaction management
    ├─→ Built-in batch write optimization
    ├─→ Easy to configure skip/retry
    └─→ Better for millions of records

NetflixDB Example:
    CHUNK: Read 10 movies from CSV → Process → Write 10 to DB
    CHUNK: Read 10 TV shows from CSV → Process → Write 10 to DB
    TASKLET: Take all DB data → Generate SQL files → Export
```

---

## 2. ItemReader Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│            Spring Batch Chunk Loop                       │
└─────────────────────────────────────────────────────────┘

for chunk in job_steps:
    items = []
    while items.size < commit_interval:
        item = itemReader.read()              ◄─── READ ONE ITEM
        if item is null:
            break                             ◄─── EOF: EXIT LOOP
        items.add(item)
    
    itemProcessor.process(item) for each in items
    itemWriter.write(items)                   ◄─── WRITE ALL
    commit()                                  ◄─── COMMIT

┌──────────────────────────────────────────────────────────┐
│                     ItemReader.read()                     │
├──────────────────────────────────────────────────────────┤
│ Call 1:  Read line from CSV → Return RawMovieDto        │
│ Call 2:  Read line from CSV → Return RawMovieDto        │
│ Call 3:  Read line from CSV → Return RawMovieDto        │
│ ...                                                      │
│ Call N:  Read from CSV → No more lines → Return null    │
│          Spring Batch: "OK, reader is done"              │
└──────────────────────────────────────────────────────────┘

          NETFLIX MOVIE CSV FILE
          ┌─────────────────────────────┐
    ┌────▶│ imdb_id,title,runtime,...   │
    │     │ 81145628,Damsel,98,...      │ Read by ItemReader
    │     │ 81235234,Avatar,192,...     │ (one line at a time)
    │     │ 81345345,The Iron Claw,...  │
    │     │ ...                         │
    │     └─────────────────────────────┘
    │              EOF
    │              ▼
    │         reader.read() returns null
    │              ▼
    │         Step execution completes
    └──────────────────────────────────
```

---

## 3. Chunk Processing Timeline

```
COMMIT INTERVAL = 10 items per chunk

Time    Event                           Memory State
────    ─────────────────────────────   ──────────────────
T0      reader.read() → Movie1          [Movie1]
        processor → Movie1*             [Movie1*]

T1      reader.read() → Movie2          [Movie1*, Movie2]
        processor → Movie2*             [Movie1*, Movie2*]

T2      reader.read() → Movie3          [Movie1*, Movie2*, Movie3]
        processor → Movie3*             [Movie1*, Movie2*, Movie3*]

...

T9      reader.read() → Movie10         [Movie1*, Movie2*, ..., Movie10]
        processor → Movie10*            [Movie1*, Movie2*, ..., Movie10*]
        
        → WRITER CALLED ◄───────────────
        writer.write([Movie1-10])       INSERT INTO movie VALUES (...)
        ↓                               (all 10 movies in one batch)
        ✓ COMMIT TRANSACTION
        ↓
        [Clear chunk]                   [] (memory cleared)

T10     reader.read() → Movie11         [Movie11]
        processor → Movie11*            [Movie11*]

...continue pattern...

TN      reader.read() → null (EOF)
        → Chunk loop exits
        → Step execution completes
```

---

## 4. Error Handling Flows

### Scenario A: Skip on Error

```
Job Configuration:
    .faultTolerant()
    .skip(DataAccessException::class.java)
    .skipLimit(5)

Processing:
    Chunk [Movie1, Movie2, ..., Movie10]
        │
        ├─→ Movie1: Process OK ✓
        ├─→ Movie2: Process OK ✓
        ├─→ Movie3: Write to DB → DataAccessException ✗
        │           → SKIP this item
        │           → Continue with Movie4
        ├─→ Movie4: Process OK ✓
        ├─→ Movie5: Write to DB → DataAccessException ✗
        │           → SKIP this item
        │           → Continue with Movie6
        ...
        └─→ Skip count: 2/5 allowed
        
    ✓ Step completes with 8 written, 2 skipped
    
Result:
    - Job: COMPLETED
    - Step: COMPLETED with warning
    - step_execution.skip_count = 2
```

### Scenario B: Retry on Error

```
Job Configuration:
    .faultTolerant()
    .retry(OptimisticLockingFailureException::class.java)
    .retryLimit(3)

Processing:
    Chunk [Movie1, Movie2, ..., Movie10]
        │
        ├─→ Movie5: Write to DB → OptimisticLockingFailureException
        │           Retry 1: Try again → OptimisticLockingFailureException
        │           Retry 2: Try again → OptimisticLockingFailureException
        │           Retry 3: Try again → OptimisticLockingFailureException
        │           Max retries exceeded → SKIP or FAIL
        │
        └─→ (depending on skip/fail configuration)

Result (if .skip() also configured):
    - Movie5 skipped
    - Job: COMPLETED
```

### Scenario C: Fail on Error

```
Job Configuration:
    (No .faultTolerant() configured)

Processing:
    Chunk [Movie1, Movie2, ..., Movie10]
        │
        ├─→ Movie3: Write to DB → DataAccessException
        │           NO SKIP CONFIGURED
        │           → ROLLBACK entire chunk
        │           → MARK STEP AS FAILED
        │
        └─→ Step execution STOPS

Result:
    - step_execution.status = FAILED
    - job_execution.status = FAILED
    - All changes in this chunk rolled back
    
Restart Behavior:
    - Can call jobLauncher.run(job, jobParameters) again
    - Spring Batch remembers last successful chunk
    - Resume from Movie11 onwards (skip Movie1-10)
    - Or restart from Movie1 if restart policy configured
```

---

## 5. JobRepository: Metadata Tracking

```
                    ┌──────────────────┐
                    │  Job Execution   │
                    │  Attempt #1      │
                    └────────┬─────────┘
                             │
                    ┌────────▼──────────┐
                    │ Step Execution 1  │  importMoviesStep
                    │ read_count: 1247  │  write_count: 1247
                    │ skip_count: 2     │  status: COMPLETED
                    └────────┬──────────┘
                             │
                    ┌────────▼──────────┐
                    │ Step Execution 2  │  importTvShowsStep
                    │ read_count: 345   │  write_count: 345
                    │ skip_count: 0     │  status: COMPLETED
                    └────────┬──────────┘
                             │
                    ┌────────▼──────────┐
                    │ Step Execution 3  │  exportSqlStep
                    │ status: FAILED    │  error: SQL export timeout
                    └───────────────────┘

RESTART: Run job again with same parameters
    ↓
    Job: "Hey JobRepository, has this job run before?"
    JobRepository: "Yes! Last run got through steps 1 & 2, failed at step 3"
    ↓
    Smart Restart:
    - Skip Step 1 (already completed)
    - Skip Step 2 (already completed)
    - Retry Step 3 (failed)
    ↓
    Result:
    - Much faster (no re-reading CSV files)
    - Avoids duplicate inserts
    - Continues from failure point
```

---

## 6. NetflixDB Execution Timeline

```
Timeline of Netflix Data Import Job

START (0ms)
    ↓
STEP 1: importMoviesStep (0-5000ms)
    ├─→ Open netflix-movies.csv
    ├─→ Chunk 1 (movies 1-10)    [100ms]  → Write 10 rows
    ├─→ Chunk 2 (movies 11-20)   [100ms]  → Write 10 rows
    ├─→ Chunk 3 (movies 21-30)   [100ms]  → Write 10 rows
    │   ... 
    ├─→ Chunk 125 (movies 1241-1247) [70ms]  → Write 7 rows (last chunk)
    └─→ Close file
    │
    Total: 1247 movies inserted in ~5000ms (Memory: only 10 movies at a time)

STEP 2: importTvShowsStep (5000-5900ms)
    ├─→ Open netflix-tvshows.csv
    ├─→ Chunk 1-35 (345 TV shows)
    └─→ Close file
    │
    Total: 345 TV shows inserted in ~900ms

STEP 3: exportSqlStep (5900-8000ms)
    ├─→ Fetch all 1247 movies from DB       [200ms]
    ├─→ Fetch all 345 TV shows from DB      [150ms]
    ├─→ Generate MySQL SQL script           [400ms]
    ├─→ Generate PostgreSQL SQL script      [400ms]
    ├─→ Generate Oracle SQL script          [400ms]
    ├─→ Generate SQL Server SQL script      [400ms]
    ├─→ Generate SQLite SQL script          [400ms]
    └─→ Write all files to disk             [100ms]
    │
    Total: All 5 vendor SQL files in ~2100ms

END (8000ms)
    ↓
JOB COMPLETED SUCCESSFULLY

Files Created:
    ✓ build/artifacts/netflix-mysql.sql (45MB)
    ✓ build/artifacts/netflix-postgresql.sql (45MB)
    ✓ build/artifacts/netflix-oracle.sql (48MB)
    ✓ build/artifacts/netflix-sqlserver.sql (48MB)
    ✓ build/artifacts/netflix-sqlite.sql (42MB)
```

---

## 7. Memory Profile Comparison

### Without Spring Batch (❌ Bad)
```
Read ALL 1247 movies into memory:
    List<Movie> movies = new ArrayList<>()
    while (hasNextLine()) {
        movies.add(parseLine())    ← Add all 1247 to memory
    }
    
    Memory Usage:
    - 1247 Movie objects
    - Each ~500 bytes = ~625KB minimum
    - Plus strings: ~2-3MB
    - Plus collections overhead
    
    Total: ~5-10MB for just movies
    Plus TV shows: +2-3MB
    Plus metadata: +1MB
    
    TOTAL: ~10-15MB constant memory footprint
    
    PROBLEM: What if you have 10 million items?
    10M items × 500 bytes = 5GB RAM needed!
```

### With Spring Batch (✓ Good)
```
Process in chunks of 10:
    for each chunk:
        items = []
        for (1 to 10):
            items.add(read())     ← Only 10 in memory
        process(items)
        write(items)
        clear(items)              ← Free memory
    
    Memory Usage:
    - Only 10 Movie objects at a time
    - ~5KB
    - Constant regardless of total dataset size
    
    TOTAL: ~5KB constant memory footprint
    
    BENEFIT: Can process 10 million items with same memory!
```

---

## 8. Transaction Boundaries

```
WITHOUT SPRING BATCH (Manual):
    │
    ├─→ BEGIN TRANSACTION
    ├─→ Insert Movie 1
    ├─→ Insert Movie 2
    ├─→ Insert Movie 3 (fails - FK constraint)
    ├─→ ROLLBACK ALL      ◄─── Lost all 3 inserts!
    │
    Problem: Either all-or-nothing, no granularity

WITH SPRING BATCH (Chunk-based):
    │
    ├─→ CHUNK 1
    │   ├─→ BEGIN TRANSACTION
    │   ├─→ Insert Movie 1
    │   ├─→ Insert Movie 2
    │   ├─→ Insert Movie 3 (fails - FK constraint)
    │   ├─→ ROLLBACK only Chunk 1
    │   └─→ COMMIT (if skip configured)
    │
    ├─→ CHUNK 2
    │   ├─→ BEGIN TRANSACTION
    │   ├─→ Insert Movie 4
    │   ├─→ Insert Movie 5
    │   └─→ COMMIT      ◄─── This chunk succeeds
    │
    Benefit: Failure isolated to single chunk, not entire job
```

---

## 9. Configuration Quick Reference

```yaml
# SCENARIO 1: Large dataset, strict consistency
spring.batch:
  chunk-size: 100
  skip-limit: 0              # Fail on any error
  retry-limit: 0             # No retry
  
Result: Fast processing, all-or-nothing, fail on first error

---

# SCENARIO 2: Large dataset, tolerant to minor errors
spring.batch:
  chunk-size: 50
  skip-limit: 100            # Allow up to 100 skips
  retry-limit: 3             # Retry 3 times
  
Result: Balanced - most data processed, some items skipped

---

# SCENARIO 3: Critical data, no losses
spring.batch:
  chunk-size: 10             # Small chunks for granularity
  skip-limit: 0              # No skips
  retry-limit: 5             # Retry aggressively
  
Result: Slow but safe - every item must succeed or fail entire job

---

# NetflixDB Configuration (Educational/Sample)
spring.batch:
  chunk-size: 10
  skip-limit: 5              # Skip a few bad movies
  retry-limit: 3             # Retry on transient failures
  
Result: Good for learning - handles minor issues, still mostly completes
```

---

## 10. Step Status Transitions

```
                    ┌──────────┐
                    │ STARTING │
                    └────┬─────┘
                         │
                    ┌────▼──────────┐
                    │ READING DATA  │
                    │ (ItemReader)  │
                    └────┬──────────┘
                         │
                    ┌────▼──────────┐
                    │ PROCESSING    │
                    │ (ItemProcessor)
                    └────┬──────────┘
                         │
                    ┌────▼──────────┐
                    │ WRITING       │
                    │ (ItemWriter)  │
                    └────┬──────────┘
                         │
                    ┌────▼──────────────────┐
                    │ ERROR OCCURRED?       │
                    └────┬──────────┬───────┘
                         │          │
                         │          ▼
                    YES  │      ┌───────────┐
                         ├─────▶│ .skip()?  │
                         │      └──┬────┬───┘
                         │       NO │    │ YES
                         │      ┌──▼──┐ │
                         │      │FAIL?│ │
                         │      └──┬──┘ │
                         │      YES│    │
                         │      ┌──▼──────────────────┐
                         │      │ STEP_FAILED         │
                         │      │ Job stops           │
                         │      │ Can be restarted    │
                         │      └─────────────────────┘
                         │            
                         │      NO (continue)
                         │      ┌──▼──────────────┐
                         │      │ Clear chunk &   │
                         │      │ Retry loop      │
                         │      └────────┬────────┘
                         │              │
                    NO  │              │ (no more data)
                         └──────────┬───┘
                                    │
                         ┌──────────▼────────┐
                         │ STEP_COMPLETED    │
                         │ Exit Status OK    │
                         └───────────────────┘
```

---

## 11. Common Mistakes & How Spring Batch Prevents Them

```
MISTAKE 1: Reading entire CSV into memory
❌ Without Spring Batch:
    val allMovies = readAllLinesIntoList()  // 1GB RAM!
    allMovies.forEach { ... }

✅ With Spring Batch:
    reader.read() returns one item at a time
    Only 10 items in memory (chunk size)

---

MISTAKE 2: Partial data inserts on failure
❌ Without Spring Batch:
    for (movie in movies) {
        try {
            db.insert(movie)
        } catch (e) {
            // Oops, some movies inserted, some not
            // Database in inconsistent state
        }
    }

✅ With Spring Batch:
    Chunk transaction: all-or-nothing per chunk
    Failure rolls back entire chunk
    JobRepository remembers progress
    Can restart cleanly

---

MISTAKE 3: No way to restart failed jobs
❌ Without Spring Batch:
    Job fails halfway through
    How do you know where it failed?
    Restart reads entire file again
    Re-inserts already-imported data

✅ With Spring Batch:
    JobRepository tracks every step
    Restart skips completed steps
    Resumes from failure point

---

MISTAKE 4: No insights into what processed
❌ Without Spring Batch:
    Job completes
    How many items read? Written? Skipped?
    No built-in metrics

✅ With Spring Batch:
    Detailed execution metadata stored
    Read count, write count, skip count
    Duration, exit status, error messages
    All queryable in job_execution tables
```

---

## Key Takeaway: The Chunk-Oriented Processing Advantage

```
┌─────────────────────────────────────────────────────────────┐
│                  THE CHUNK ADVANTAGE                         │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Problem: Process 1 million items                           │
│                                                              │
│  ❌ Without Chunking:                                        │
│     Read item 1 → Process → Write → Commit (1M times!)     │
│     = 1M individual writes = VERY SLOW                      │
│                                                              │
│  ✅ With Chunking (size=1000):                             │
│     Read 1000 items                                         │
│     Process 1000 items                                      │
│     Batch Write 1000 items (1 operation)                    │
│     Commit (1000 times instead of 1M!)                      │
│     = ~1000x faster                                         │
│                                                              │
│  ✅ With Even Larger Chunking (size=10000):                │
│     10,000x faster!                                         │
│     Still only ~100 batch operations instead of 1M          │
│                                                              │
│  Memory: CONSTANT (only chunk_size items at a time)         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

**For NetflixDB:**
- **Why Chunks?** Because importing thousands of movies/shows, chunk batching is 100x faster than individual inserts
- **Why Tasklet for Export?** Because export is single operation: "read all data, generate SQL", not item-by-item processing
- **Why JobRepository?** Because if import fails halfway, can restart without re-reading CSV or duplicate-inserting movies

This is the power of Spring Batch.
