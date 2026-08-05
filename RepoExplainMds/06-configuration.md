# Configuration & build

## Spring profiles (`src/main/resources/`)

| File | `spring.datasource.name` | Purpose |
|------|------------------------|---------|
| `application.yml` | `h2` (default) | In-memory H2, `ddlAuto: create`, batch enabled |
| `application-postgres.yml` | `postgres` | `jdbc:postgresql://localhost/netflixdb` |
| `application-mysql.yml` | `mysql` | MySQL container defaults |
| `application-oracle.yml` | `oracle` | Oracle Free PDB |
| `application-sqlserver.yml` | `sqlserver` | SQL Server |
| `application-sqlite.yml` | `sqlite` | Skips SQL file export in job |

Activate: `-Dspring.profiles.active=postgres` or `SPRING_PROFILES_ACTIVE`.

### Batch-related defaults (`application.yml`)

```yaml
spring.batch:
  jdbc:
    initialize-schema: always
  job:
    enabled: true
```

Job name follows bean method `createNetflixDatabaseJob` (no `spring.batch.job.names` filter in default file).

### Schema script target (H2 example)

```yaml
spring.jpa.properties.javax.persistence.schema-generation.scripts.create-target: build/artifacts/netflixdb-h2.sql
```

Postgres profile sets `create-target: build/artifacts/netflixdb-postgres.sql`.

## `build.gradle.kts` (essentials)

- Java **21** toolchain
- Spring Boot **3.5.x**, Kotlin **1.9.25**
- Starters: `spring-boot-starter-batch`, `spring-boot-starter-data-jpa`
- Excel: `spring-batch-excel`, Apache POI
- DDL tools: `hibernate-tools-orm`
- JDBC drivers: H2, PostgreSQL, MySQL, Oracle, SQL Server, SQLite (runtime)

## `build.sh`

```bash
./gradlew clean build
for p in h2 postgres mysql oracle sqlserver sqlite; do
  java -jar -Dspring.profiles.active=$p build/libs/netflixdb-0.0.1-SNAPSHOT.jar
done
```

Each run executes the full batch + export for that vendor (except sqlite export branch).

## `docker-compose.yml`

Optional local databases on standard ports (5432 Postgres, 3306 MySQL, etc.). Credentials match profile YAML files.

## Tests

`NetflixDbApplicationTests` — `@SpringBootTest` smoke test, currently `@Ignore` (full job run is heavy for CI).
