# Batch layer (`batch/`)

Single configuration class: **`CreateNetflixDatabaseJobConfig`**. Defines job `createNetflixDatabaseJob` and all steps.

## Job composition

| Condition | Steps included |
|-----------|----------------|
| Always | `setupStep` |
| `hibernate.ddlAuto == "create"` | Import engagement (×2), top-10, populate movie/season, `verifyContentStep` |
| `spring.datasource.name != "sqlite"` | Export schema, export data (×4 tables), `fileCompressionStep` |

SQLite profile skips file export (in-memory / file DB tooling differs).

## Step reference

| Step bean | Type | Purpose |
|-----------|------|---------|
| `setupStep` | Tasklet | Creates `build/artifacts/` parent dir |
| `importEngagementReport20240101Step` | Chunk 100 | Stages rows from H1 2024 workbook |
| `importEngagementReport20230701Step` | Chunk 100 | Stages rows from H2 2023 workbook |
| `importTop10ListStep` | Chunk 100 | Stages weekly global top-10 |
| `populateMovieTableStep` | Chunk 50 | Drains `movieRowsByTitleAndRuntime` → DB |
| `populateSeasonTableStep` | Chunk 50 | Drains `seasonRowsByTitleAndRuntime` → DB |
| `verifyContentStep` | Tasklet | Requires `ViewSummary` for previous Sunday `endDate` |
| `exportDatabaseSchemaStep` | Tasklet | Hibernate `SchemaExport` + header via `DatabaseExportService` |
| `{movie,tvShow,season,viewSummary}ExportStep` | Chunk 100 | Appends INSERTs to SQL file |
| `fileCompressionStep` | Tasklet | Zips `.sql` artifact |

Engagement import steps use **explicit step names** in `buildEngagementReportImportStep` so Spring Batch metadata stays unique (shared helper cannot use stack-trace naming).

## In-memory staging

```text
accumulateReportRows(chunk)
  → key = (title, runtime)
  → MOVIE  → movieRowsByTitleAndRuntime
  → TV_SHOW → seasonRowsByTitleAndRuntime
```

Populate steps use a one-shot reader: `pollNextRowSet` removes arbitrary first map entry until empty (`null` ends step).

## Processors

- **`movieProcessor`** — `fold` rows into one `Movie`, merge scalar fields, `updateViewSummary` each row.
- **`seasonProcessor`** — same for `Season`; resolves `TvShow` via `findByTitle` or new entity; `tvShowRepository.save` before return.

## Naming trick

`callerBeanMethodName()` reads `Exception().stackTrace[1].methodName` so **job/step names match `@Bean` method names**—important for restart and `build.sh` reruns. Must only be called directly from `@Bean` methods (not nested helpers except where explicit `stepName` is passed).

## Artifact path

`DataSourceProperties.artifactFilename()` → `build/artifacts/netflixdb-{spring.datasource.name}.sql`

## Failure handling

`failJobOnStepFailureListener` sets job `FAILED` and `exitProcess(1)` if any step exit code is `FAILED`.

## Further reading

Generic Spring Batch theory (chunks vs tasklets, JobRepository): [appendix-spring-batch.md](appendix-spring-batch.md).
