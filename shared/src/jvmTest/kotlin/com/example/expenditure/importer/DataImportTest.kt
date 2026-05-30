package com.example.expenditure.importer

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.expenditure.db.ExpenditureDatabase
import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.db.createExpenditureDatabase
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class DataImportTest {

    private fun repo(): ExpenditureRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpenditureDatabase.Schema.create(driver)
        return ExpenditureRepository(createExpenditureDatabase(driver))
    }

    private fun fixtureFolder(): File =
        File(javaClass.classLoader.getResource("rewe_pdfs/receipt.pdf")!!.toURI()).parentFile

    @Test
    fun importsReweFixturePdfEndToEnd() {
        val repository = repo()
        DataImport(repository).importReweFolder(fixtureFolder())

        val rows = repository.getReweExpenditures()
        assertEquals(13, rows.size)
        assertEquals(setOf(LocalDate(2026, 2, 2)), rows.map { it.date.date }.toSet())

        val byName = rows.associate { it.name to (it.amount to it.price) }
        val expected = mapOf(
            "BIO BERGKAESE ST" to (1L to 2.69),
            "CHOC & CHOC" to (1L to 2.89),
            "GNOCCHI" to (1L to 3.69),
            "GEMUESEMAULTASCH" to (1L to 2.29),
            "BIO CASHEWKERNE" to (1L to 3.79),
            "PIENENKERNE" to (1L to 3.99),
            "PAKCHOI MINI" to (1L to 1.99),
            "KAROTTE REWE PP" to (1L to 1.15),
            "PAPRIKA ROT" to (1L to 1.57),
            "KRAEUTER" to (1L to 1.69),
            "BIO MILCH 3,6%" to (2L to 2.29),
            "BIO SCHLAGSAHNE" to (1L to 1.19),
            "DINKEL FLAKES" to (1L to 2.49),
        )
        for ((name, expectedPair) in expected) {
            val (amount, price) = byName.getValue(name)
            assertEquals(expectedPair.first, amount, "amount for $name")
            assertEquals(expectedPair.second, price, 1e-9, "price for $name")
        }
    }
}
