## Netflix DB

Sample database based on [Netflix Global Top 10](https://www.netflix.com/tudum/top10) weekly list with movies and TV shows.

### Supported Database Servers

* MySQL
* Oracle
* PostgreSQL
* SQL Server

### Download
Download the SQL scripts from the [latest release](../../releases) assets. One or more SQL script files are provided for each database vendor supported. You can run these SQL scripts with your preferred database tool.


### Data Source

* https://about.netflix.com/en/news/what-we-watched-the-first-half-of-2024
* https://about.netflix.com/en/news/what-we-watched-the-second-half-of-2023
* https://www.netflix.com/tudum/top10


### Data Model

![database.png](src/main/resources/images/database.png)

### Building and generating the SQL Scripts

Start the database containers:
```bash
docker compose up
```

Build the application:
```bash
./gradlew clean build
```

Generate the SQL Scripts:
```bash
for p in postgres mysql oracle; do java -jar -Dspring.batch.job.enabled=false -Dspring.profiles.active=$p build/libs/netflixdb-0.0.1-SNAPSHOT.jar; done
```

The generated SQL scripts will be in the `build/artifacts` folder:
```bash
open ./build/artifacts
```
