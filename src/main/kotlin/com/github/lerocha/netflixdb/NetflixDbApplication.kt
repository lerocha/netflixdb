package com.github.lerocha.netflixdb

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class NetflixDbApplication

fun main(args: Array<String>) {
    runApplication<NetflixDbApplication>(*args)
}
