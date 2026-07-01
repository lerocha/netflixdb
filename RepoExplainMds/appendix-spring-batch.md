# Appendix: Spring Batch concepts

Reference only—NetflixDB specifics are in [04-batch.md](04-batch.md).

## Core terms

| Term | Meaning |
|------|---------|
| **Job** | Ordered pipeline of steps; one logical run |
| **Step** | Chunk (read→process→write) or tasklet (single callback) |
| **Chunk** | Transaction boundary size; commit after N items |
| **JobRepository** | Metadata tables for executions, restart |
| **ItemReader** | Returns next item or `null` at end |
| **ItemProcessor** | Transform or filter (`null` = skip) |
| **ItemWriter** | Receives list per chunk |

## Chunk vs tasklet

```text
Chunk step:  read → process → write → COMMIT (repeat)
Tasklet step: execute once → FINISHED
```

NetflixDB uses chunks for Excel and DB export, tasklets for setup/verify/schema/zip.

## Fault tolerance

`.faultTolerant()` on chunk steps allows skip/retry policies (this project relies on defaults; invalid rows are filtered at staging).

## Restart

Step names must be stable across releases. NetflixDB ties names to `@Bean` methods via `callerBeanMethodName()`; shared helpers use explicit `stepName` strings.

## When to read Spring docs

- Custom skip/retry policies
- Partitioning remote steps
- Scheduling jobs without `spring.batch.job.enabled=true`

For this repo, reading `CreateNetflixDatabaseJobConfig.kt` with [04-batch.md](04-batch.md) is sufficient.
