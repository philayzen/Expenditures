package com.example.expenditure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.db.createExpenditureDatabase
import com.example.expenditure.db.createSqlDriver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val context = applicationContext
        setContent {
            val repository = remember {
                ExpenditureRepository(createExpenditureDatabase(createSqlDriver(context)))
            }
            App(repository)
        }
    }
}
