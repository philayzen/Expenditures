package com.example.expenditure.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

fun createSqlDriver(databasePath: String = "expenditures.db"): SqlDriver {
    val driver = JdbcSqliteDriver("jdbc:sqlite:$databasePath")
    try {
        ExpenditureDatabase.Schema.create(driver)
    }
    catch (e: Exception) {}
    return driver
}
