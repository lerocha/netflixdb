# Configuration & Deployment Explained

## application.yml - The Central Configuration

```yaml
spring:
  application:
    name: netflixdb
  
  # DATABASE CONNECTION
  datasource:
    url: jdbc:h2:file:./data/netflixdb
    username: sa
    password: (empty)
    driver-class-name: org.h2.Driver
  
  # HIBERNATE / JPA
  jpa:
    hibernate:
      ddl-auto: create-drop  # Drop and recreate schema on startup
    show-sql: false          # Don't log SQL
    properties:
      hibernate:
        format_sql: true     # Pretty-print SQL
        jdbc:
          batch_size: 20     # Batch 20 items per insert
        order_inserts: true  # Optimize insert order
  
  # SPRING BATCH
  batch:
    job:
      enabled: true                  # Auto-run jobs on startup
      names: importNetflixDataJob   # Which jobs to run
    jdbc:
      initialize-database: always    # Create batch tables
  
  # LOGGING
logging:
  level:
    root: INFO
    com.github.lerocha: DEBUG
```

## Environment-Specific Profiles

### Development (application-dev.yml)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb  # In-memory (fast, no persistence)
  jpa:
    show-sql: true           # Log SQL for debugging
  batch:
    job:
      enabled: true          # Auto-run on startup
  h2:
    console:
      enabled: true          # Enable H2 web console
```

**Usage:** `./gradlew bootRun`

### Testing (application-test.yml)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  batch:
    job:
      enabled: false         # Don't auto-run in tests
```

### Production (application-prod.yml)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/netflix
    username: ${DB_USER}     # From environment variable
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate     # Only check schema (never modify)
      database-platform: org.hibernate.dialect.PostgreSQL13Dialect
  batch:
    job:
      enabled: false         # Run manually, not on startup
```

**Usage:** `export DB_USER=netflix && export DB_PASSWORD=secret && java -jar app.jar --spring.profiles.active=prod`

## build.gradle.kts - The Build Configuration

```kotlin
// Project metadata
group = "com.github.lerocha"
version = "1.0.49"

// Gradle plugins
plugins {
    id("org.springframework.boot") version "3.2.0"
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.spring") version "1.9.0"
}

// Java version
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

// Dependencies (imported libraries)
dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.batch:spring-batch-core")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // Databases
    runtimeOnly("com.h2database:h2")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("mysql:mysql-connector-java")
    
    // CSV parsing
    implementation("org.apache.commons:commons-csv:1.10.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// Kotlin compiler options
tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "21"
    }
}

// Spring Boot JAR configuration
tasks.named<BootJar>("bootJar") {
    mainClass.set("com.github.lerocha.netflixdb.NetflixdbApplicationKt")
    baseName = "netflixdb"
}
```

## Docker Compose - Local Database Setup

```yaml
version: '3.8'

services:
  # PostgreSQL database
  postgres:
    image: postgres:15-alpine
    container_name: netflixdb-postgres
    environment:
      POSTGRES_DB: netflix
      POSTGRES_USER: netflix
      POSTGRES_PASSWORD: netflix
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
  
  # MySQL database
  mysql:
    image: mysql:8.0
    container_name: netflixdb-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: netflix
      MYSQL_USER: netflix
      MYSQL_PASSWORD: netflix
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
  
  # Oracle database (optional)
  oracle:
    image: gvenzl/oracle-xe:latest
    environment:
      ORACLE_PASSWORD: oracle
    ports:
      - "1521:1521"

volumes:
  postgres_data:
  mysql_data:
```

**Usage:**
```bash
# Start all databases
docker-compose up -d

# Start only PostgreSQL
docker-compose up -d postgres

# Stop all
docker-compose down

# View logs
docker-compose logs -f postgres
```

## Environment Variables

```bash
# For production deployment
export DB_USER="netflix_user"
export DB_PASSWORD="secure_password"
export SPRING_PROFILES_ACTIVE="prod"
export DATASOURCE_URL="jdbc:postgresql://prod-db:5432/netflix"

# Docker usage
docker run -e DB_USER=netflix \
           -e DB_PASSWORD=password \
           -e SPRING_PROFILES_ACTIVE=prod \
           netflixdb:latest

# Kubernetes usage
env:
  - name: DB_USER
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: username
```

## Dockerfile

```dockerfile
# Multi-stage build

FROM eclipse-temurin:21-jdk as builder
WORKDIR /workspace

# Copy gradle files
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .

# Copy source
COPY src ./src

# Build
RUN ./gradlew bootJar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy JAR
COPY --from=builder /workspace/build/libs/netflixdb*.jar app.jar

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build and run:**
```bash
docker build -t netflixdb:latest .
docker run -e DB_USER=netflix -e DB_PASSWORD=password -p 8080:8080 netflixdb:latest
```

## Build & Run Commands

```bash
# Local development
./gradlew bootRun

# With development profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Build JAR
./gradlew build
java -jar build/libs/netflixdb-1.0.49.jar

# Run tests
./gradlew test

# Generate SQL scripts
./gradlew bootRun
# Runs batch job automatically, creates build/artifacts/*.sql
```

## Configuration Priority

1. **Command line arguments** (highest)
   ```bash
   java -Dspring.datasource.url=... -jar app.jar
   ```

2. **Environment variables**
   ```bash
   export SPRING_DATASOURCE_URL=...
   ```

3. **application-{profile}.yml**
   ```yaml
   # application-prod.yml
   ```

4. **application.yml**
   ```yaml
   # Default config
   ```

5. **Gradle defaults** (lowest)

## Multi-Database Support

### Switch to PostgreSQL

```yaml
# application-postgres.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/netflix
    driver-class-name: org.postgresql.Driver
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQL13Dialect
```

### Switch to MySQL

```yaml
# application-mysql.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/netflix
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
```

### Switch to Oracle

```yaml
# application-oracle.yml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE
    driver-class-name: oracle.jdbc.driver.OracleDriver
  jpa:
    database-platform: org.hibernate.dialect.OracleDialect
```

## Monitoring & Health Checks

```yaml
spring:
  actuator:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,loggers
    endpoint:
      health:
        show-details: when-authorized

# Access:
# http://localhost:8080/actuator/health
# http://localhost:8080/actuator/metrics
```

## Best Practices

✅ Use environment-specific profiles (dev/test/prod)
✅ Store secrets in environment variables, not in code
✅ Use Docker Compose for local databases
✅ Configure logging appropriately per environment
✅ Validate configuration on startup
✅ Use YAML format (not properties)

❌ Don't commit secrets to version control
❌ Don't use same config for all environments
❌ Don't leave default passwords in production
❌ Don't log sensitive data
❌ Don't ignore configuration validation errors
