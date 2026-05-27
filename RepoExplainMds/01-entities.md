# Entity layer (`entity/`)

JPA entities define the schema Hibernate generates at startup. All concrete entities extend `AbstractEntity` (audited `id`, `createdDate`, `modifiedDate`).

## Entity reference

| Class | Table | Populated by batch? | Notes |
|-------|-------|---------------------|-------|
| `Movie` | `movie` | Yes | `viewSummaries` cascade ALL |
| `TvShow` | `tv_show` | Yes | Unique `title` index |
| `Season` | `season` | Yes | FK `tv_show_id`; TV rows from reports |
| `ViewSummary` | `view_summary` | Yes | FK to `movie` **or** `season` (mutually exclusive rows) |
| `Episode` | `episode` | No | Schema only today |
| `SummaryDuration` | (enum) | — | `WEEKLY`, `SEMI_ANNUALLY` on `ViewSummary.duration` |

## Relationships

```
TvShow 1 ── * Season 1 ── * ViewSummary
Movie  1 ── * ViewSummary
Season (no Episode children in import)
```

- **Movie ↔ ViewSummary:** `movie.viewSummaries` / `viewSummary.movie`
- **Season ↔ ViewSummary:** same pattern for TV metrics
- **TvShow ↔ Season:** `season.tvShow`; processor saves `TvShow` before `Season`

## Notable mappings

- `@Nationalized` on titles — Unicode-safe columns
- `Locale` on Movie/TvShow — exported as language code under Postgres strategy
- `Movie.availableGlobally` / `TvShow.availableGlobally` — engagement “Yes” flag
- Physical names: `CamelCaseToUnderscoresNamingStrategy` → `release_date`, `tv_show_id`, etc.

## Hibernate `@Comment`

Column comments are embedded for documentation in generated DDL (where the dialect supports it).

For annotation patterns (`@ManyToOne`, `FetchType.LAZY`, indexes), read the entity source files directly—they mirror standard JPA practice without extra framework magic.
