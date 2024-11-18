# Netflix DB

Sample database based on the [Netflix Engagement Report](https://about.netflix.com/en/news/what-we-watched-the-first-half-of-2024) and on the [Netflix Global Top 10](https://www.netflix.com/tudum/top10) weekly list with movies and TV shows.

## Supported Database Servers

* MySQL
* Oracle
* PostgreSQL

## Download
Download the SQL scripts from the [latest release](../../releases) assets. One or more SQL script files are provided for each database vendor supported. You can run these SQL scripts with your preferred database tool.

## Data Sources

* https://about.netflix.com/en/news/what-we-watched-the-first-half-of-2024
* https://about.netflix.com/en/news/what-we-watched-the-second-half-of-2023
* https://www.netflix.com/tudum/top10

## Data Model

![database.png](src/main/resources/images/database.png)

## Development

* The application is a [Spring Boot](https://spring.io/projects/spring-boot) application that uses [Spring Data JPA](https://spring.io/projects/spring-data-jpa) / [Hibernate](https://hibernate.org/orm/) Object/Relational Mapping framework. 
* The database schema is defined in these [entity classes](src/main/kotlin/com/github/lerocha/netflixdb/entity), and it gets auto-generated when the application starts up.
* After start-up, the application uses [Spring Batch](https://spring.io/projects/spring-batch) to run a [batch job](src/main/kotlin/com/github/lerocha/netflixdb/batch/ImportNetflixDataJobConfig.kt) to populate the database based on the [Netflix spreadsheet reports](src/main/resources/reports).

### System Requirements
* JDK 21, for example: [Amazon Corretto 21](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html), [Oracle OpenJDK 21](https://www.oracle.com/java/technologies/downloads/#java21), [MS OpenJDK 21](https://learn.microsoft.com/en-us/java/openjdk/download#openjdk-2105-lts--see-previous-releases), etc.
* [Docker Desktop](https://www.docker.com/products/docker-desktop/)

### Building and generating the SQL Scripts

Start the database containers:
```bash
docker compose up -d
```

Generate the SQL Scripts:
```bash
./build.sh
```

The generated SQL scripts will be in the `build/artifacts` folder:
```bash
open ./build/artifacts
```
