package com.example.expenditure.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

fun createSqlDriver(context: Context, databaseName: String = "expenditures.db"): SqlDriver =
    AndroidSqliteDriver(ExpenditureDatabase.Schema, context, databaseName)
