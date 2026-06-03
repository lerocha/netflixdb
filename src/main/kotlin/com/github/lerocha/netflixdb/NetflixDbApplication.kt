package com.github.lerocha.netflixdb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** Boots Spring Batch; the import job runs on startup when batch jobs are enabled. */
@SpringBootApplication
class NetflixDbApplication

fun main(args: Array<String>) {
    // Batch job runs on startup when spring.batch.job.enabled=true (see application.yml).
    runApplication<NetflixDbApplication>(*args)
}
