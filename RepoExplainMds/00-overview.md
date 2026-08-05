# Overview

NetflixDB is a **sample database generator**: it loads public Netflix engagement and top-10 Excel reports into a relational schema, then exports vendor-specific SQL scripts under `build/artifacts/`.

## Runtime flow

```
Spring Boot starts
    → JPA creates schema (ddlAuto=create on default profile)
    → Spring Batch runs createNetflixDatabaseJob
           ├─ [if ddlAuto=create] Excel import → Movie / Season / TvShow / ViewSummary
           ├─ verify weekly ViewSummary exists for previous Sunday
           └─ [if profile ≠ sqlite] DDL + INSERT export + zip
    → Artifacts: build/artifacts/netflixdb-{profile}.sql[.zip]
```

## Layers (single responsibility)

| Layer | Responsibility |
|-------|----------------|
| `entity` | JPA schema |
| `repository` | Persistence queries used by batch |
| `dto` | Excel row model + entity mapping |
| `batch` | Job orchestration, readers/writers |
| `service` | Multi-database SQL generation |

There is **no REST API**; the app exits the batch path after work completes (failed steps call `exitProcess(1)`).

## Data inputs

| File | Type | Used for |
|------|------|----------|
| `reports/What_We_Watched_...2024Jan-Jun.xlsx` | Semi-annual | `EngagementReport.ENGAGEMENT_REPORT_2024_01_01` |
| `reports/What_We_Watched_...2023Jul-Dec.xlsx` | Semi-annual | `EngagementReport.ENGAGEMENT_REPORT_2023_07_01` |
| `reports/all-weeks-global.xlsx` | Weekly top 10 | `top10ListReader` |

## Build all vendor scripts

```bash
docker compose up -d    # optional DB containers
./build.sh              # runs jar once per profile (h2, postgres, mysql, …)
```

See [06-configuration.md](06-configuration.md) for profiles and properties.
