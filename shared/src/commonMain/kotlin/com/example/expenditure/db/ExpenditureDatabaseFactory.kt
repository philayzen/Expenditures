package com.example.expenditure.db

import app.cash.sqldelight.ColumnAdapter
import app.cash.sqldelight.db.SqlDriver
import kotlinx.datetime.LocalDate

val localDateAdapter = object : ColumnAdapter<LocalDate, String> {
    override fun decode(databaseValue: String): LocalDate = LocalDate.parse(databaseValue)
    override fun encode(value: LocalDate): String = value.toString()
}

fun createExpenditureDatabase(driver: SqlDriver): ExpenditureDatabase =
    ExpenditureDatabase(
        driver = driver,
        general_expendituresAdapter = General_expenditures.Adapter(dateAdapter = localDateAdapter),
        rewe_expendituresAdapter = Rewe_expenditures.Adapter(dateAdapter = localDateAdapter),
    )
