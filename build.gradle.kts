plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "1.9.25"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
    id("de.undercouch.download") version "5.5.0"
}

group = "com.github.lerocha"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.batch.extensions:spring-batch-excel:0.1.1")
    implementation("org.apache.poi:poi:5.4.1")
    implementation("org.apache.poi:poi-ooxml:5.4.1")
    implementation("org.hibernate.tool:hibernate-tools-orm:6.6.15.Final")
    implementation("org.hibernate.orm:hibernate-community-dialects")
    runtimeOnly("com.h2database:h2")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("com.oracle.database.jdbc:ojdbc11")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("org.xerial:sqlite-jdbc")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.batch:spring-batch-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register<de.undercouch.gradle.tasks.download.Download>("downloadReport") {
    src("https://www.netflix.com/tudum/top10/data/all-weeks-global.xlsx")
    dest("src/main/resources/reports/all-weeks-global.xlsx")
    onlyIfNewer(true)
}

tasks.named("processResources") {
    dependsOn("downloadReport")
}
