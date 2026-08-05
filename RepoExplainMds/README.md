# NetflixDB — Developer documentation

Canonical onboarding docs for this repository. Each guide maps to **one Kotlin package** (or cross-cutting concern) and avoids repeating the same architecture narrative elsewhere.

> **Note:** An older copy lived in `RepoExplainDocs/`; that folder now redirects here.

## Read in this order

| # | Guide | Package / path |
|---|--------|----------------|
| 1 | [00-overview.md](00-overview.md) | Whole application |
| 2 | [01-entities.md](01-entities.md) | `entity/` |
| 3 | [02-repositories.md](02-repositories.md) | `repository/` |
| 4 | [03-dto.md](03-dto.md) | `dto/` |
| 5 | [04-batch.md](04-batch.md) | `batch/` |
| 6 | [05-service.md](05-service.md) | `service/` |
| 7 | [06-configuration.md](06-configuration.md) | `resources/`, `build.gradle.kts`, Docker |
| A | [appendix-spring-batch.md](appendix-spring-batch.md) | Optional Spring Batch concepts |

## Source tree (100% Kotlin coverage)

```
src/main/kotlin/com/github/lerocha/netflixdb/
├── NetflixDbApplication.kt      → 00-overview
├── entity/                      → 01-entities
├── repository/                  → 02-repositories
├── dto/                         → 03-dto
├── batch/                       → 04-batch
└── service/                     → 05-service

src/main/resources/
├── application*.yml             → 06-configuration
└── reports/*.xlsx               → 03-dto, 04-batch

src/test/kotlin/...              → smoke context test (ignored)
```

## Quick links

- Root [README.md](../README.md) — Postgres sample queries, `./build.sh`
- ER diagram — `src/main/resources/images/database.png`
