package com.github.lerocha.netflixdb.strategy

import org.springframework.stereotype.Component

@Component
class DatabaseStrategyFactory(
    private val oracleStrategy: OracleStrategy,
    private val mysqlStrategy: MySqlStrategy,
    private val postgresStrategy: PostgresStrategy,
) {
    fun getInstance(databaseName: String): DatabaseStrategy {
        return when (databaseName) {
            "oracle" -> oracleStrategy
            "mysql" -> mysqlStrategy
            "postgresql" -> postgresStrategy
            "h2" -> postgresStrategy
            else -> throw IllegalArgumentException("Not supported database $databaseName")
        }
    }
}
