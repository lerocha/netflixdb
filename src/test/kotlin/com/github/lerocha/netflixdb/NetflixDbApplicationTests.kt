package com.github.lerocha.netflixdb

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Ignore

@Ignore // Full context load runs the batch job; enable only for local integration checks.
@SpringBootTest
class NetflixDbApplicationTests {
    @Test
    fun contextLoads() {
    }
}
