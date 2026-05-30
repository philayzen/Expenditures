package com.example.expenditure.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

val localDateAdapter = object : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

/** REWE rows keep the full receipt timestamp so two trips on the same day stay distinct in the PK. */
val localDateTimeAdapter = object : ColumnAdapter<LocalDateTime, String> {
    override fun decode(databaseValue: String): LocalDateTime = LocalDateTime.parse(databaseValue)
    override fun encode(value: LocalDateTime): String = value.toString()
}

fun createExpenditureDatabase(driver: SqlDriver): ExpenditureDatabase =
    ExpenditureDatabase(
        driver = driver,
        general_expendituresAdapter = General_expenditures.Adapter(dateAdapter = localDateAdapter),
        rewe_expendituresAdapter = Rewe_expenditures.Adapter(dateAdapter = localDateTimeAdapter),
    )
