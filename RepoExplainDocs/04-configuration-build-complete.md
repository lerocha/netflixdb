# NetflixDB Configuration & Build - Complete Documentation

**Spring Boot Properties, Gradle Build, Database Configuration, and Deployment**

---

## Overview

NetflixDB configuration is driven by:
1. **application.yml** - Spring Boot and application-specific properties
2. **build.gradle.kts** - Gradle build configuration
3. **docker-compose.yml** - Local database setup for development
4. **application-*.yml** - Environment-specific configs (dev, test, prod)

---

## 1. Application Properties (application.yml)

### Basic Spring Boot Configuration

```yaml
spring:
  application:
    name: netflixdb                    # Application name (used in logs, metrics)
  
  # ============================================
  # DATASOURCE CONFIGURATION
  # ============================================
  datasource:
    url: jdbc:h2:file:./data/netflixdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
    # url explanation:
    # - jdbc:h2:file: = Use H2 database in file mode
    # - ./data/netflixdb = Database file location
    # - DB_CLOSE_DELAY=-1 = Don't close connection immediately
    # - DB_CLOSE_ON_EXIT=FALSE = Don't close when VM exits
    
    username: sa                        # H2 default user
    password:                           # H2 default (empty)
    driver-class-name: org.h2.Driver   # H2 JDBC driver
  
  # ============================================
  # JPA / HIBERNATE CONFIGURATION
  # ============================================
  jpa:
    # DDL (Data Definition Language) strategy
    hibernate:
      ddl-auto: create-drop
      # Options:
      # - validate: Check schema matches entities (safest for production)
      # - update: Add new columns/tables (non-destructive)
      # - create: Create fresh schema (drops existing)
      # - create-drop: Create on startup, drop on shutdown (development)
      # - none: Don't touch schema
    
    show-sql: false                     # Don't log all SQL (too verbose)
    
    # Database dialect (SQL flavor)
    database-platform: org.hibernate.dialect.H2Dialect
    
    # Property-specific Hibernate settings
    properties:
      hibernate:
        format_sql: true                # Pretty-print SQL in logs
        use_sql_comments: true          # Add comments to generated SQL
        
        # Batch configuration (for bulk operations)
        jdbc:
          batch_size: 20                # Process 20 items per batch
          fetch_size: 20                # Fetch 20 rows at a time
          use_scrollable_resultset: false  # Performance optimization
        
        # Query optimization
        order_inserts: true             # Sort INSERT statements by table
        order_updates: true             # Sort UPDATE statements by table
        generate_statistics: false      # Hibernate statistics (performance impact)

# ============================================
# SPRING BATCH CONFIGURATION
# ============================================
  batch:
    job:
      enabled: true                     # Enable batch jobs
      names: importNetflixDataJob      # Which job(s) to run on startup
    
    # Metadata database configuration
    jdbc:
      initialize-database: always       # Create batch tables if not exist
      table-prefix: BATCH_              # Prefix for batch tables (BATCH_JOB_INSTANCE, etc.)
    
    # Async job execution (if using async launcher)
    threads:
      corePoolSize: 2                   # Minimum threads in pool
      maxPoolSize: 4                    # Maximum threads in pool
      queueCapacity: 100                # Queue for pending jobs

# ============================================
# LOGGING CONFIGURATION
# ============================================
logging:
  level:
    root: INFO                          # Root logger level
    org.springframework: INFO            # Spring logs
    org.springframework.batch: INFO      # Spring Batch logs
    org.hibernate: INFO                 # Hibernate logs
    com.github.lerocha.netflixdb: DEBUG # Application-specific logs (more verbose)
  
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"  # Console format
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

# ============================================
# CUSTOM NETFLIX DB PROPERTIES
# ============================================
netflix:
  batch:
    chunk-size: 10                      # Items per chunk in Spring Batch
  
  export:
    output-directory: build/artifacts   # Where to write SQL files
    generate-all-vendors: true          # Generate for all DB vendors

# ============================================
# SERVER CONFIGURATION (if REST API added)
# ============================================
server:
  port: 8080                            # HTTP port
  servlet:
    context-path: /                     # Base path for all endpoints
```

---

## 2. Multi-Environment Configuration

### application-dev.yml (Development)
```yaml
# Sensitive settings for local development

spring:
  datasource:
    url: jdbc:h2:mem:testdb            # In-memory H2 (fast, doesn't persist)
    # This is the development default
  
  jpa:
    hibernate:
      ddl-auto: create-drop            # Fresh schema every startup (safe)
    show-sql: true                      # Log all SQL for debugging
  
  batch:
    job:
      enabled: true                     # Auto-run import on startup
  
  h2:
    console:
      enabled: true                     # Enable H2 web console
      path: /h2-console                 # Access at http://localhost:8080/h2-console

logging:
  level:
    root: DEBUG                         # Very verbose for development
    com.github.lerocha: DEBUG
```

### application-test.yml (Testing)
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
  
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: false
  
  batch:
    job:
      enabled: false                    # Don't auto-run in tests

logging:
  level:
    root: WARN                          # Quiet for tests
    com.github.lerocha: INFO
```

### application-prod.yml (Production)
```yaml
spring:
  datasource:
    url: jdbc:postgresql://db.example.com:5432/netflix
    username: ${DB_USER}                # From environment variables
    password: ${DB_PASSWORD}            # From secrets management
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate                # Only check schema, never modify
    show-sql: false                     # No SQL logging (performance)
    database-platform: org.hibernate.dialect.PostgreSQL13Dialect
  
  batch:
    job:
      enabled: false                    # Run manually, not on startup

logging:
  level:
    root: WARN                          # Only warnings/errors
    com.github.lerocha: INFO
  
  file:
    name: /var/log/netflixdb/app.log   # Log to file
    max-size: 100MB                     # Rotate logs
    max-history: 30                     # Keep 30 days
```

---

## 3. Gradle Build Configuration (build.gradle.kts)

```kotlin
// ============================================
// PROJECT METADATA
// ============================================
group = "com.github.lerocha"
version = "1.0.49"                      // Semantic versioning

// ============================================
// GRADLE PLUGINS
// ============================================
plugins {
    id("org.springframework.boot") version "3.2.0"    // Spring Boot
    id("io.spring.dependency-management") version "1.1.0"  // Dependency management
    kotlin("jvm") version "1.9.0"       // Kotlin compiler
    kotlin("plugin.spring") version "1.9.0"  // Spring annotations support
}

// ============================================
// JAVA/KOTLIN CONFIGURATION
// ============================================
java {
    sourceCompatibility = JavaVersion.VERSION_21  // JDK 21 required
    targetCompatibility = JavaVersion.VERSION_21
}

// ============================================
// DEPENDENCIES
// ============================================
dependencies {
    // Spring Boot Starter Web (REST APIs, HTTP server)
    implementation("org.springframework.boot:spring-boot-starter-web")
    
    // Spring Data JPA (Database access)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    
    // Spring Batch (Batch processing framework)
    implementation("org.springframework.batch:spring-batch-core")
    
    // Kotlin stdlib (Kotlin standard library)
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    
    // Kotlin reflection (needed for Spring)
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    
    // H2 Database (embedded database for testing)
    runtimeOnly("com.h2database:h2")
    
    // PostgreSQL JDBC (for production)
    runtimeOnly("org.postgresql:postgresql")
    
    // MySQL JDBC (for production)
    runtimeOnly("mysql:mysql-connector-java")
    
    // Oracle JDBC (for production)
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    
    // Apache Commons CSV (CSV parsing)
    implementation("org.apache.commons:commons-csv:1.10.0")
    
    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.batch:spring-batch-test")
}

// ============================================
// KOTLIN COMPILER OPTIONS
// ============================================
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"    // Strict null checking
        jvmTarget = "21"                         // Target JDK 21
    }
}

// ============================================
// SPRING BOOT CONFIGURATION
// ============================================
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    mainClass.set("com.github.lerocha.netflixdb.NetflixdbApplicationKt")  // Main class
    baseName = "netflixdb"
    archiveVersion.set("")                      // Don't append version
}

// ============================================
// CUSTOM TASKS
// ============================================
tasks.register("generateSql") {
    dependsOn("bootRun")                        // Depends on Spring Boot running
    description = "Generate SQL scripts from batch job"
}
```

---

## 4. Docker Compose (database-compose.yml)

```yaml
version: '3.8'

services:
  # ============================================
  # MYSQL DATABASE
  # ============================================
  mysql:
    image: mysql:8.0
    container_name: netflixdb-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root         # Root password
      MYSQL_DATABASE: netflix           # Create database
      MYSQL_USER: netflix
      MYSQL_PASSWORD: netflix
    ports:
      - "3306:3306"                     # Port 3306 (MySQL default)
    volumes:
      - mysql_data:/var/lib/mysql       # Persistent storage
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  # ============================================
  # POSTGRESQL DATABASE
  # ============================================
  postgres:
    image: postgres:15-alpine
    container_name: netflixdb-postgres
    environment:
      POSTGRES_DB: netflix             # Create database
      POSTGRES_USER: netflix
      POSTGRES_PASSWORD: netflix
    ports:
      - "5432:5432"                    # Port 5432 (PostgreSQL default)
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U netflix"]
      interval: 10s
      timeout: 5s
      retries: 5
  
  # ============================================
  # ORACLE DATABASE (optional, requires license)
  # ============================================
  oracle:
    image: gvenzl/oracle-xe:latest
    container_name: netflixdb-oracle
    environment:
      ORACLE_PASSWORD: oracle
    ports:
      - "1521:1521"                    # Port 1521 (Oracle default)
    volumes:
      - oracle_data:/opt/oracle/oradata
  
  # ============================================
  # SQL SERVER DATABASE
  # ============================================
  sqlserver:
    image: mcr.microsoft.com/mssql/server:2019-latest
    container_name: netflixdb-sqlserver
    environment:
      SA_PASSWORD: "Netflix123!"
      ACCEPT_EULA: "Y"
    ports:
      - "1433:1433"                    # Port 1433 (SQL Server default)
    volumes:
      - sqlserver_data:/var/opt/mssql

# ============================================
# NAMED VOLUMES (persistent storage)
# ============================================
volumes:
  mysql_data:
  postgres_data:
  oracle_data:
  sqlserver_data:

# ============================================
# USAGE
# ============================================
# docker-compose up -d          # Start all services
# docker-compose down           # Stop and remove containers
# docker-compose logs -f mysql  # View MySQL logs
# docker-compose ps             # Show running containers
```

**Start databases:**
```bash
# Start only PostgreSQL
docker-compose up -d postgres

# Start all databases
docker-compose up -d

# Stop all
docker-compose down

# View logs
docker-compose logs -f
```

---

## 5. Java System Properties Configuration

```properties
# Can be set in various ways:
# 1. application.properties file
# 2. Environment variables
# 3. Command line: java -Dproperty=value
# 4. application.yml (YAML format preferred now)

# Database settings
spring.datasource.url=jdbc:postgresql://localhost:5432/netflix
spring.datasource.username=netflix
spring.datasource.password=netflix

# Logging
logging.level.root=INFO
logging.level.com.github.lerocha=DEBUG

# Spring Batch
spring.batch.job.enabled=true
spring.batch.job.names=importNetflixDataJob
```

---

## 6. Environment Variables for Secrets

```bash
# application-prod.yml references environment variables:

# Set via:
export DB_USER="netflix_user"
export DB_PASSWORD="secure_password_here"
export DATASOURCE_URL="jdbc:postgresql://prod-db:5432/netflix"

# Or in Docker:
docker run -e DB_USER=netflix_user -e DB_PASSWORD=password ...

# Or in Kubernetes:
env:
  - name: DB_USER
    valueFrom:
      secretKeyRef:
        name: db-credentials
        key: username
```

---

## 7. Build & Run Commands

```bash
# ============================================
# LOCAL DEVELOPMENT
# ============================================

# Build
./gradlew build                    # Compile, test, package

# Run
./gradlew bootRun                  # Start application
# Runs with application.yml config

# Run with specific profile
./gradlew bootRun --args='--spring.profiles.active=dev'

# Run tests
./gradlew test                     # Run unit tests
./gradlew integrationTest          # Integration tests

# ============================================
# DOCKER BUILD
# ============================================

# Build Docker image
docker build -t netflixdb:latest .

# Run Docker container
docker run -e SPRING_PROFILES_ACTIVE=prod \
           -e DB_USER=netflix \
           -e DB_PASSWORD=password \
           -p 8080:8080 \
           netflixdb:latest

# ============================================
# GENERATE SQL SCRIPTS
# ============================================

# Run batch job to generate SQL
./build.sh                         # Generates SQL in build/artifacts/

# Or manually
./gradlew bootRun
# Application starts, batch job runs automatically
# SQL files created in build/artifacts/
```

---

## 8. Dockerfile (if included)

```dockerfile
# Multi-stage build

FROM eclipse-temurin:21-jdk as builder
WORKDIR /workspace

# Copy gradle files
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy source
COPY src ./src

# Build application
RUN ./gradlew bootJar

# Runtime stage
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy JAR from builder
COPY --from=builder /workspace/build/libs/netflixdb*.jar app.jar

# Expose port
EXPOSE 8080

# Set environment (can be overridden)
ENV SPRING_PROFILES_ACTIVE=prod

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

## 9. build.sh Script (Generate SQL)

```bash
#!/bin/bash

# Start databases
docker-compose up -d

# Wait for databases to be ready
sleep 30

# Run Spring Boot application (triggers batch job)
./gradlew bootRun

# If using -Dspring.batch.job.enabled=false, manually trigger batch
# (Batch job runs automatically in default config)

# Export directory
OUTPUT_DIR="build/artifacts"
echo "SQL files generated in: $OUTPUT_DIR"

# Optionally upload to releases
# gh release upload v1.0.0 $OUTPUT_DIR/*.sql
```

---

## 10. Configuration Priority (Highest to Lowest)

```
1. Command line arguments
   java -Dspring.datasource.url=...
   java -jar app.jar --spring.datasource.url=...

2. Environment variables
   export SPRING_DATASOURCE_URL=...
   export DB_USER=...

3. application-{profile}.yml
   (activated by spring.profiles.active)

4. application.yml
   (default configuration)

5. Gradle build.gradle.kts defaults

6. Spring Boot auto-configuration

7. Java defaults
```

---

## 11. Common Configuration Scenarios

### Scenario 1: Local Development with H2
```yaml
# application.yml (default)
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
  batch:
    job:
      enabled: true
```
**Usage:** `./gradlew bootRun` (no args needed)

### Scenario 2: PostgreSQL Production
```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/netflix
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate
      database-platform: org.hibernate.dialect.PostgreSQL13Dialect
  batch:
    job:
      enabled: false
```
**Usage:** 
```bash
export DB_USER=netflix
export DB_PASSWORD=secure_password
./gradlew bootRun --args='--spring.profiles.active=prod'
```

### Scenario 3: Multiple Databases (Testing)
```yaml
# application.yml
spring:
  datasource:
    primary:
      url: jdbc:postgresql://localhost:5432/netflix
    secondary:
      url: jdbc:mysql://localhost:3306/netflix
```

---

## 12. Configuration Validation

```kotlin
// Validate configuration at startup with @Configuration classes

@Configuration
class DataSourceValidation {
    
    @Bean
    fun validateDataSource(dataSource: DataSource): CommandLineRunner {
        return CommandLineRunner {
            try {
                val connection = dataSource.connection
                logger.info("Database connection successful: ${connection.metaData.databaseProductName}")
                connection.close()
            } catch (e: Exception) {
                throw IllegalStateException("Failed to validate database connection", e)
            }
        }
    }
}
```

---

## 13. Monitoring & Metrics (Optional)

```yaml
# Add Spring Boot Actuator for metrics

spring:
  boot:
    admin:
      client:
        url: http://localhost:8081    # Spring Boot Admin server
  
  actuator:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,loggers,jobs
    endpoint:
      health:
        show-details: when-authorized
```

**Access metrics:**
```
http://localhost:8080/actuator/health         # Health check
http://localhost:8080/actuator/metrics        # All metrics
http://localhost:8080/actuator/metrics/jvm    # JVM metrics
```

---

## 14. Best Practices Summary

✅ **DO:**
- Use environment variables for secrets (never in code)
- Different configs for dev/test/prod (profiles)
- Validate configuration on startup
- Use docker-compose for local database setup
- Document all properties with comments
- Use YAML instead of properties format

❌ **DON'T:**
- Hardcode sensitive values
- Use same config for all environments
- Leave default passwords in production
- Ignore configuration validation errors
- Commit `.env` or secrets files
- Use `ddl-auto: create` in production

---

**Used in:** Spring Boot initialization, Gradle builds, Docker deployment, Database setup
