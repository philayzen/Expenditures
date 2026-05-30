package com.example.expenditure

import androidx.compose.runtime.remember
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.db.createExpenditureDatabase
import com.example.expenditure.db.createSqlDriver

fun main() = application {
    val repository = remember {
        ExpenditureRepository(createExpenditureDatabase(createSqlDriver()))
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Expenditure",
    ) {
        App(repository)
    }
}
