# Service layer (`service/`)

Generates **portable SQL** after data is in the database. The batch job calls this layer only from `exportDatabaseSchemaStep` and `exportDataStep` writers—not from controllers.

## Component diagram

```text
CreateNetflixDatabaseJobConfig
        │
        ├─ exportDatabaseSchemaStep ──► DatabaseExportService.exportSchema()
        │                                      │
        │                                      ├─ Hibernate SchemaExport (DDL)
        │                                      └─ DatabaseStrategyFactory → getInitDatabase()
        │
        └─ exportDataStep writer ──────► DatabaseExportService.getInsertStatement()
                                               │
                                               └─ DatabaseStrategyFactory → DatabaseStrategy
```

## `DatabaseStrategy` (interface)

| Method | Purpose |
|--------|---------|
| `getPhysicalNamingStrategy()` | Snake_case table/column names matching JPA export |
| `getInitDatabase()` | Optional preamble (Oracle PDB/schema) |
| `getSqlValues(*properties)` | Format one row of VALUES literals per dialect |
| `getInsertStatement(entity)` | Single-row `INSERT` |
| `getInsertStatement(entities)` | Multi-row `INSERT` (bulk except Oracle) |
| `getProperties(entity)` | Reflection: fields + getters → column map |

### Reflection rules (`getProperties`)

- Skips `@OneToMany`, `@ManyToMany`, `@GeneratedValue` (collections and ids handled separately).
- `@ManyToOne` / `@OneToOne` → column `{field}_id` via getter value (`movie` → `movie_id` with naming strategy).
- Invokes JavaBean `get*` methods on entity + `AbstractEntity` superclass.

**Why reflection?** Same entities drive H2/Postgres/MySQL/Oracle/SQL Server exports without hand-written INSERT templates per table.

## `DatabaseStrategyFactory`

Maps `spring.datasource.name` (profile) to implementation:

| `name` | Strategy |
|--------|----------|
| `postgres` | `PostgresStrategy` |
| `mysql` | `MySqlStrategy` |
| `oracle` | `OracleStrategy` |
| `sqlserver` | `SqlServerStrategy` |
| `h2` | `PostgresStrategy` (compatible literal style) |

Unknown name → `IllegalArgumentException`.

## `DatabaseExportService`

| Method | Behavior |
|--------|----------|
| `exportSchema(...)` | Builds standalone Hibernate `MetadataSources` from live `DataSourceProperties`, writes license header + `getInitDatabase()`, runs `SchemaExport` CREATE script to target file |
| `getInsertStatement(databaseName, entities)` | Delegates to strategy for chunked append in batch writer |

Export Hibernate settings mirror runtime naming (`SpringImplicitNamingStrategy`, `CamelCaseToUnderscoresNamingStrategy`) so **DDL in the artifact matches INSERT column names**.

## Dialect implementations

| Class | Distinct formatting |
|-------|---------------------|
| `PostgresStrategy` | Standard quoted strings; `Locale` → language code |
| `MySqlStrategy` | UUID as `0xHEX`; quoted instants |
| `OracleStrategy` | `ALTER SESSION` preamble; `timestamp`/`date` literals; `&` escaped in strings; **one INSERT per row** (overrides bulk list method) |
| `SqlServerStrategy` | `N'...'` Unicode strings; booleans as `1`/`0` |

Shared: `CamelCaseToUnderscoresNamingStrategy`, UTC instant pattern `YYYY-MM-dd HH:mm:ss.SSS`.

## Interaction summary

1. Batch populates DB through JPA repositories.
2. `exportDatabaseSchemaStep` creates empty-file header + DDL.
3. Each `exportDataStep` pages `findAll()` and **appends** INSERT text (same file grows monotonically).
4. `fileCompressionStep` zips the `.sql` file.

No service beans are used during Excel import—only during export phase.
