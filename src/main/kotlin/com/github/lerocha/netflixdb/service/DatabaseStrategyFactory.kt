package com.github.lerocha.netflixdb.service

import org.springframework.stereotype.Component

@Component
class DatabaseStrategyFactory(
    private val oracleStrategy: OracleStrategy,
    private val mysqlStrategy: MySqlStrategy,
    private val postgresStrategy: PostgresStrategy,
    private val sqlServerStrategy: SqlServerStrategy,
) {
    fun getInstance(databaseName: String): DatabaseStrategy {
        return when (databaseName) {
            "oracle" -> oracleStrategy
            "mysql" -> mysqlStrategy
            "postgres" -> postgresStrategy
            "sqlserver" -> sqlServerStrategy
            "h2" -> postgresStrategy
            else -> throw IllegalArgumentException("Not supported database $databaseName")
        }
    }
}
