package com.github.lerocha.netflixdb.service

import org.springframework.stereotype.Component

/** Resolves a [DatabaseStrategy] implementation from the active Spring datasource profile name. */
@Component
class DatabaseStrategyFactory(
    private val oracleStrategy: OracleStrategy,
    private val mysqlStrategy: MySqlStrategy,
    private val postgresStrategy: PostgresStrategy,
    private val sqlServerStrategy: SqlServerStrategy,
) {
    fun getInstance(databaseName: String): DatabaseStrategy =
        when (databaseName) {
            "oracle" -> oracleStrategy
            "mysql" -> mysqlStrategy
            "postgres" -> postgresStrategy
            "sqlserver" -> sqlServerStrategy
            "h2" -> postgresStrategy // H2 export uses Postgres-style literals in artifacts
            else -> throw IllegalArgumentException("Not supported database $databaseName")
        }
}
