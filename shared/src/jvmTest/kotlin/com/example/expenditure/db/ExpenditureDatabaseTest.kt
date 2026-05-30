package com.example.expenditure.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.example.expenditure.model.BankDbEntry
import com.example.expenditure.model.BankExpenditureEntry
import com.example.expenditure.model.CategoryTarget
import com.example.expenditure.model.CategoryUpdate
import com.example.expenditure.model.ExpenseType
import com.example.expenditure.model.Item
import com.example.expenditure.model.ReweExpenditureEntry
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExpenditureDatabaseTest {

    private class Env(
        val driver: SqlDriver,
        val db: ExpenditureDatabase,
        val repo: ExpenditureRepository,
    ) : AutoCloseable {
        val q get() = db.expendituresQueries
        override fun close() = driver.close()
    }

    private fun env(): Env {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ExpenditureDatabase.Schema.create(driver)
        val db = createExpenditureDatabase(driver)
        return Env(driver, db, ExpenditureRepository(db))
    }

    private fun date(value: String) = LocalDate.parse(value)

    // == schema ==

    @Test
    fun initDbCreatesQueryableEmptyTables() = env().use { e ->
        assertEquals(emptyList(), e.repo.getGeneralExpenditures())
        assertEquals(emptyList(), e.repo.getReweExpenditures())
        assertEquals(emptyList(), e.repo.getCategoryTypes())
    }

    // == insert_general_expenditure ==

    @Test
    fun insertGeneralExpenditure() {
        data class Case(val entries: List<BankDbEntry>, val expectedCount: Int)
        val cases = listOf(
            Case(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "books")), 1),
            Case(
                listOf(
                    BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "books"),
                    BankDbEntry(2, date("2024-01-02"), "Me", "Rewe", 30.0, "food"),
                ),
                2,
            ),
            Case(
                listOf(
                    BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "books"),
                    BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 99.0, "same_pk_ignored"),
                ),
                1,
            ),
        )
        for (case in cases) env().use { e ->
            e.repo.insertGeneralExpenditure(case.entries)
            val rows = e.q.selectGeneralExpenditures(null, null).executeAsList()
                .sortedWith(compareBy({ it.key_helper }, { it.date }))
            assertEquals(case.expectedCount, rows.size)
            val first = case.entries[0]
            assertEquals(first.keyHelper, rows[0].key_helper)
            assertEquals(first.date, rows[0].date)
            assertEquals(first.source, rows[0].source)
            assertEquals(first.recipient, rows[0].recipient)
            assertEquals(first.purpose, rows[0].purpose)
            assertEquals(first.price, rows[0].price)
        }
    }

    // == insert_rewe_expenditure ==

    @Test
    fun insertReweExpenditure() {
        data class Case(val items: List<Item>, val expectedCount: Int)
        val cases = listOf(
            Case(listOf(Item(2, "Milk", 1.5, "", "Milk")), 1),
            Case(listOf(Item(2, "Milk", 1.5, "", "Milk"), Item(1, "Bread", 2.0, "", "Bread")), 2),
            Case(listOf(Item(2, "Milk", 1.5, "", "Milk"), Item(1, "Milk", 1.5, "", "Milk")), 1),
        )
        for (case in cases) env().use { e ->
            val d = date("2024-01-01")
            e.repo.insertReweExpenditure(d, case.items)
            val rows = e.q.selectReweExpenditures(null, null).executeAsList()
            assertEquals(case.expectedCount, rows.size)
            val seen = mutableSetOf<String>()
            for (item in case.items) {
                val name = item.name!!
                val present = rows.any {
                    it.date.date == d && it.name == name && it.amount == item.amount.toLong() && it.price == item.price
                }
                // a duplicate name within the batch is ignored by the PK; the original stays
                if (name !in seen) assertTrue(present, "expected $name to be present")
                seen += name
            }
        }
    }

    @Test
    fun insertReweExpenditureSameDateSkipsAllItems() = env().use { e ->
        e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(2, "Milk", 1.5, "", "Milk")))
        e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(1, "Bread", 2.0, "", "Bread")))
        val names = e.q.selectReweExpenditures(null, null).executeAsList().map { it.name }
        assertEquals(listOf("Milk"), names)
    }

    // == update_display_names (bank) ==

    @Test
    fun updateDisplayNamesBankByPk() = env().use { e ->
        seedPayPalEntries(e)
        val entry = BankExpenditureEntry(
            keyHelper = 1, date = date("2024-01-01"), source = "Me", recipient = "PayPal",
            price = 80.0, purpose = "sub", category = "TV", displayName = "PayPal",
        )
        e.repo.updateDisplayNames(listOf(entry), "PayPal Premium")
        val rows = e.q.selectGeneralExpenditures(null, null).executeAsList().sortedBy { it.key_helper }
        assertEquals("PayPal Premium", rows[0].display_name)
    }

    @Test
    fun updateDisplayNamesBankByAltNamesRespectsCategory() = env().use { e ->
        seedPayPalEntries(e)
        val entry = BankExpenditureEntry(
            keyHelper = null, date = null, source = null, recipient = null,
            price = 160.0, purpose = null, category = "TV", displayName = "PayPal",
            altNames = setOf("PayPal"),
        )
        e.repo.updateDisplayNames(listOf(entry), "PayPal Premium")
        val rows = e.q.selectGeneralExpenditures(null, null).executeAsList().sortedBy { it.key_helper }
        assertEquals("PayPal Premium", rows[0].display_name)
        assertEquals("", rows[1].display_name)
    }

    private fun seedPayPalEntries(e: Env) {
        e.repo.insertGeneralExpenditure(
            listOf(
                BankDbEntry(1, date("2024-01-01"), "Me", "PayPal", 80.0, "sub", "TV"),
                BankDbEntry(2, date("2024-01-02"), "Me", "PayPal", 80.0, "sub", ""),
            )
        )
    }

    // == update_display_names (rewe) ==

    @Test
    fun updateDisplayNamesReweByName() = env().use { e ->
        seedBioMilch(e)
        val entry = ReweExpenditureEntry(
            date = date("2024-01-01").atTime(0, 0), name = "BIO MILCH A", amount = 2, price = 1.5,
            category = "Dairy", displayName = "BIO MILCH A",
        )
        e.repo.updateDisplayNames(listOf(entry), "Milk")
        val displayNames = e.q.selectReweExpenditures(null, null).executeAsList().map { it.display_name }.toSet()
        assertTrue("Milk" in displayNames)
        assertTrue("" in displayNames)
    }

    @Test
    fun updateDisplayNamesReweByAltNames() = env().use { e ->
        seedBioMilch(e)
        val entry = Item(
            amount = 3, name = null, price = 3.5, category = "Dairy",
            displayName = "Milk", altNames = setOf("BIO MILCH A", "BIO MILCH B"),
        )
        e.repo.updateDisplayNames(listOf(entry), "Milk")
        val displayNames = e.q.selectReweExpenditures(null, null).executeAsList().map { it.display_name }.toSet()
        assertTrue("Milk" in displayNames)
        assertTrue("" !in displayNames)
    }

    private fun seedBioMilch(e: Env) {
        e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(2, "BIO MILCH A", 1.5, "Dairy")))
        e.repo.insertReweExpenditure(date("2024-01-02"), listOf(Item(1, "BIO MILCH B", 2.0, "Dairy")))
    }

    // == get_name_references ==

    @Test
    fun getNameReferences() {
        data class Case(val inserted: List<Pair<String, String>>, val lookup: List<String>, val expected: Set<Pair<String, String>>)
        val cases = listOf(
            Case(listOf("AMAZON" to "Amazon"), listOf("AMAZON"), setOf("AMAZON" to "Amazon")),
            Case(listOf("AMAZON" to "Amazon", "REWE" to "Rewe"), listOf("AMAZON", "REWE"), setOf("AMAZON" to "Amazon", "REWE" to "Rewe")),
            Case(listOf("AMAZON" to "Amazon", "AMAZON PRIME" to "Amazon"), listOf("AMAZON", "AMAZON PRIME"), setOf("AMAZON" to "Amazon", "AMAZON PRIME" to "Amazon")),
            Case(listOf("AMAZON" to "Amazon"), listOf("UNKNOWN"), emptySet()),
        )
        for (case in cases) env().use { e ->
            case.lookup.forEachIndexed { i, name ->
                e.q.insertGeneralExpenditure(i.toLong(), date("2024-01-01"), "Me", name, null, 1.0, null)
            }
            case.inserted.forEach { (name, display) -> e.q.updateBankDisplayNameByRecipient(display, name) }
            val results = e.repo.getNameReferences(case.lookup)
            assertEquals(case.expected, results.map { it.name to it.displayName }.toSet())
        }
    }

    // == get_original_names ==

    @Test
    fun getOriginalNames() {
        data class Case(val inserted: List<Pair<String, String>>, val reference: String, val expected: List<String>)
        val cases = listOf(
            Case(listOf("AMAZON" to "Amazon"), "Amazon", listOf("AMAZON")),
            Case(listOf("AMAZON1" to "Amazon", "AMAZON2" to "Amazon"), "Amazon", listOf("AMAZON1", "AMAZON2")),
            Case(emptyList(), "Original name", listOf("Original name")),
        )
        for (case in cases) env().use { e ->
            case.inserted.forEachIndexed { i, (name, display) ->
                e.q.insertGeneralExpenditure(i.toLong(), date("2024-01-01"), "Me", name, null, 1.0, null)
                e.q.updateBankDisplayNameByRecipient(display, name)
            }
            val results = e.repo.getOriginalNames(case.reference)
            assertEquals(case.expected.sorted(), results.sorted())
        }
    }

    // == get_general_expenditures_from_db ==

    private val bankEntries = listOf(
        BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "books"),
        BankDbEntry(2, date("2024-02-01"), "Me", "Rewe", 30.0, "food"),
        BankDbEntry(3, date("2024-03-01"), "Me", "ignored1", 100.0, "transfer"),
        BankDbEntry(4, date("2024-03-01"), "Me", "ignored2", 100.0, "transfer"),
    )

    @Test
    fun getGeneralExpendituresFiltersAndIgnore() {
        data class Case(val since: String?, val until: String?, val ignore: List<String>, val expected: Set<String>)
        val cases = listOf(
            Case(null, null, emptyList(), setOf("Amazon", "Rewe", "ignored1", "ignored2")),
            Case("2024-02-01", null, emptyList(), setOf("Rewe", "ignored1", "ignored2")),
            Case(null, "2024-01-31", emptyList(), setOf("Amazon")),
            Case("2024-01-01", "2024-02-28", emptyList(), setOf("Amazon", "Rewe")),
            Case(null, null, listOf("ignored1"), setOf("Amazon", "Rewe", "ignored2")),
            Case(null, null, listOf("ignored1", "ignored2"), setOf("Amazon", "Rewe")),
        )
        for (case in cases) env().use { e ->
            e.repo.insertGeneralExpenditure(bankEntries)
            val results = e.repo.getGeneralExpenditures(
                since = case.since?.let { date(it) },
                until = case.until?.let { date(it) },
                bankIgnore = case.ignore,
            )
            assertEquals(case.expected, results.map { it.recipient }.toSet())
        }
    }

    @Test
    fun getGeneralExpendituresPriceNegated() = env().use { e ->
        e.repo.insertGeneralExpenditure(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "books")))
        val results = e.repo.getGeneralExpenditures()
        assertEquals(-50.0, results[0].price)
    }

    // == get_rewe_expenditures_from_db ==

    @Test
    fun getReweExpendituresFilters() {
        val reweEntries = listOf(
            date("2024-01-01") to listOf(Item(2, "Milk", 1.5, "", "Milk")),
            date("2024-02-01") to listOf(Item(1, "Bread", 2.0, "", "Bread")),
            date("2024-03-01") to listOf(Item(3, "Eggs", 3.5, "", "Eggs")),
        )
        data class Case(val since: String?, val until: String?, val expected: Set<String>)
        val cases = listOf(
            Case(null, null, setOf("Milk", "Bread", "Eggs")),
            Case("2024-02-01", null, setOf("Bread", "Eggs")),
            Case(null, "2024-01-31", setOf("Milk")),
            Case("2024-01-01", "2024-02-28", setOf("Milk", "Bread")),
        )
        for (case in cases) env().use { e ->
            reweEntries.forEach { (d, items) -> e.repo.insertReweExpenditure(d, items) }
            val results = e.repo.getReweExpenditures(case.since?.let { date(it) }, case.until?.let { date(it) })
            assertEquals(case.expected, results.map { it.name }.toSet())
        }
    }

    // == update_rewe_categories_in_db ==

    @Test
    fun updateReweCategories() {
        for ((name, category) in listOf("Milk" to "Dairy", "Bread" to "Bakery")) env().use { e ->
            e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(2, name, 1.5, "", name)))
            e.repo.updateReweCategoriesInDb(listOf(CategoryUpdate(name, category)))
            val stored = e.q.selectReweExpenditures(null, null).executeAsList().first { it.name == name }.category
            assertEquals(category, stored)
        }
    }

    // == update_bank_categories_in_db ==

    @Test
    fun updateBankCategories() {
        for ((recipient, category) in listOf("Amazon" to "Shopping", "Rewe" to "Groceries")) env().use { e ->
            e.repo.insertGeneralExpenditure(listOf(BankDbEntry(1, date("2024-01-01"), "Me", recipient, 50.0, "test")))
            e.repo.updateBankCategoriesInDb(listOf(CategoryUpdate(recipient, category)))
            val stored = e.q.selectGeneralExpenditures(null, null).executeAsList().first { it.recipient == recipient }.category
            assertEquals(category, stored)
        }
    }

    // == get_rewe_most_common_categories ==

    @Test
    fun getReweMostCommonCategories() {
        data class Case(val inserted: List<Pair<LocalDate, List<Item>>>, val lookup: List<String>, val expected: Map<String, String>)
        val cases = listOf(
            Case(listOf(date("2024-01-01") to listOf(Item(1, "Milk", 1.5, "Milk"))), listOf("Milk"), mapOf("Milk" to "Milk")),
            Case(listOf(date("2024-01-01") to listOf(Item(1, "Milk", 1.5, "Milk"), Item(1, "Bread", 2.0, "Bread"))), listOf("Milk", "Bread"), mapOf("Milk" to "Milk", "Bread" to "Bread")),
            Case(listOf(date("2024-01-01") to listOf(Item(1, "Milk", 1.5, "Milk"))), listOf("Bread"), emptyMap()),
            Case(
                listOf(
                    date("2024-01-01") to listOf(Item(4, "Milk", 1.5, ""), Item(3, "Bread", 2.0, "")),
                    date("2024-01-02") to listOf(Item(1, "Milk", 1.5, "Milk"), Item(4, "Bread", 2.0, "")),
                    date("2024-01-03") to listOf(Item(4, "Milk", 1.5, "Milk"), Item(5, "Bread", 2.0, "Bread")),
                ),
                listOf("Milk", "Bread"),
                mapOf("Milk" to "Milk", "Bread" to ""),
            ),
        )
        for (case in cases) env().use { e ->
            case.inserted.forEach { (d, items) -> e.repo.insertReweExpenditure(d, items) }
            assertEquals(case.expected, e.repo.getReweMostCommonCategories(case.lookup))
        }
    }

    @Test
    fun getReweMostCommonCategoriesReturnsMostCommon() = env().use { e ->
        e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(1, "Milk", 1.5, "Dairy")))
        e.repo.insertReweExpenditure(date("2024-01-02"), listOf(Item(1, "Milk", 1.5, "Dairy")))
        e.repo.insertReweExpenditure(date("2024-01-03"), listOf(Item(1, "Milk", 1.5, "Snacks")))
        assertEquals(mapOf("Milk" to "Dairy"), e.repo.getReweMostCommonCategories(listOf("Milk")))
    }

    // == get_bank_most_common_categories ==

    @Test
    fun getBankMostCommonCategories() {
        data class Case(val inserted: List<BankDbEntry>, val lookup: List<String>, val expected: Map<String, String>)
        val cases = listOf(
            Case(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping")), listOf("Amazon"), mapOf("Amazon" to "Shopping")),
            Case(
                listOf(
                    BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping"),
                    BankDbEntry(2, date("2024-01-01"), "Me", "Rewe", 30.0, "", "food"),
                ),
                listOf("Amazon", "Rewe"),
                mapOf("Amazon" to "Shopping", "Rewe" to "food"),
            ),
            Case(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping")), listOf("Rewe"), emptyMap()),
            Case(
                listOf(
                    BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping"),
                    BankDbEntry(2, date("2024-01-01"), "Me", "Rewe", 30.0, "", "food"),
                ),
                listOf("Amazon"),
                mapOf("Amazon" to "Shopping"),
            ),
        )
        for (case in cases) env().use { e ->
            e.repo.insertGeneralExpenditure(case.inserted)
            assertEquals(case.expected, e.repo.getBankMostCommonCategories(case.lookup))
        }
    }

    @Test
    fun getBankMostCommonCategoriesReturnsMostCommon() = env().use { e ->
        e.q.insertGeneralExpenditure(1, date("2024-01-01"), "Me", "Amazon", "books", 50.0, "Shopping")
        e.q.insertGeneralExpenditure(2, date("2024-01-02"), "Me", "Amazon", "books", 50.0, "Shopping")
        e.q.insertGeneralExpenditure(3, date("2024-01-03"), "Me", "Amazon", "books", 50.0, "Online")
        assertEquals(mapOf("Amazon" to "Shopping"), e.repo.getBankMostCommonCategories(listOf("Amazon")))
    }

    @Test
    fun getBankMostCommonCategoriesMultipleEntries() = env().use { e ->
        e.q.insertGeneralExpenditure(1, date("2024-01-01"), "Me", "Amazon", "books", 50.0, "")
        e.q.insertGeneralExpenditure(1, date("2024-01-02"), "Me", "Amazon", "books", 50.0, "books")
        e.q.insertGeneralExpenditure(2, date("2024-01-02"), "Me", "Amazon", "books", 50.0, "Shopping")
        e.q.insertGeneralExpenditure(1, date("2024-01-03"), "Me", "Amazon", "books", 50.0, "Shopping")
        e.q.insertGeneralExpenditure(1, date("2024-01-01"), "Me", "Rewe", "food", 30.0, "")
        e.q.insertGeneralExpenditure(1, date("2024-01-02"), "Me", "Rewe", "food", 30.0, "")
        e.q.insertGeneralExpenditure(2, date("2024-01-02"), "Me", "Rewe", "food", 30.0, "Groceries")
        assertEquals(mapOf("Amazon" to "Shopping", "Rewe" to ""), e.repo.getBankMostCommonCategories(listOf("Amazon", "Rewe")))
    }

    // == get_category_types ==

    @Test
    fun getCategoryTypesEmpty() = env().use { e ->
        assertEquals(emptyList(), e.repo.getCategoryTypes())
    }

    @Test
    fun getCategoryTypesReturnsAll() = env().use { e ->
        e.q.insertCategoryType("Food", "Essential")
        e.q.insertCategoryType("Shopping", "Routine")
        val results = e.repo.getCategoryTypes()
        assertEquals(2, results.size)
        assertEquals(
            setOf("Food" to "Essential", "Shopping" to "Routine"),
            results.map { it.category to it.expenseType }.toSet(),
        )
    }

    // == update_category_types ==

    @Test
    fun updateCategoryTypesInserts() = env().use { e ->
        e.repo.updateCategoryTypes(listOf("Food" to "Essential"))
        assertEquals(setOf("Food" to "Essential"), e.repo.getCategoryTypes().map { it.category to it.expenseType }.toSet())
    }

    @Test
    fun updateCategoryTypesReplacesOnDuplicate() = env().use { e ->
        e.repo.updateCategoryTypes(listOf("Food" to "Essential"))
        e.repo.updateCategoryTypes(listOf("Food" to "Routine"))
        assertEquals(listOf("Food" to "Routine"), e.repo.getCategoryTypes().map { it.category to it.expenseType })
    }

    @Test
    fun updateCategoryTypesMultiple() = env().use { e ->
        e.repo.updateCategoryTypes(listOf("Food" to "Essential", "Netflix" to "Subscription"))
        assertEquals(
            setOf("Food" to "Essential", "Netflix" to "Subscription"),
            e.repo.getCategoryTypes().map { it.category to it.expenseType }.toSet(),
        )
    }

    // == get_distinct_bank_categories ==

    @Test
    fun getDistinctBankCategoriesEmpty() = env().use { e ->
        assertEquals(emptyList(), e.repo.getDistinctBankCategories())
    }

    @Test
    fun getDistinctBankCategoriesExcludesEmptyString() = env().use { e ->
        e.repo.insertGeneralExpenditure(
            listOf(
                BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping"),
                BankDbEntry(2, date("2024-01-01"), "Me", "Rewe", 30.0, "", ""),
            )
        )
        assertEquals(setOf("Shopping"), e.repo.getDistinctBankCategories().toSet())
    }

    @Test
    fun getDistinctBankCategoriesDeduplicates() = env().use { e ->
        e.repo.insertGeneralExpenditure(
            listOf(
                BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping"),
                BankDbEntry(2, date("2024-01-01"), "Me", "Netflix", 10.0, "", "Shopping"),
            )
        )
        assertEquals(listOf("Shopping"), e.repo.getDistinctBankCategories())
    }

    @Test
    fun getDistinctBankCategoriesMultiple() = env().use { e ->
        e.repo.insertGeneralExpenditure(
            listOf(
                BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping"),
                BankDbEntry(2, date("2024-01-01"), "Me", "Netflix", 10.0, "", "Subscription"),
                BankDbEntry(3, date("2024-01-01"), "Me", "Rewe", 30.0, "", "Groceries"),
            )
        )
        assertEquals(setOf("Shopping", "Subscription", "Groceries"), e.repo.getDistinctBankCategories().toSet())
    }

    // == sync_bank_category_types ==

    @Test
    fun syncBankCategoryTypesInsertsNewWithUnassigned() = env().use { e ->
        e.repo.insertGeneralExpenditure(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping")))
        e.repo.syncBankCategoryTypes()
        val results = e.repo.getCategoryTypes()
        assertEquals(1, results.size)
        assertEquals("Shopping", results[0].category)
        assertEquals(ExpenseType.UNASSIGNED.value, results[0].expenseType)
    }

    @Test
    fun syncBankCategoryTypesRemovesOrphans() = env().use { e ->
        e.q.insertCategoryType("OldCat", "Essential")
        e.repo.syncBankCategoryTypes()
        assertEquals(emptyList(), e.repo.getCategoryTypes())
    }

    @Test
    fun syncBankCategoryTypesPreservesExistingAssignment() = env().use { e ->
        e.repo.insertGeneralExpenditure(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "", "Shopping")))
        e.q.insertCategoryType("Shopping", "Essential")
        e.repo.syncBankCategoryTypes()
        val results = e.repo.getCategoryTypes()
        assertEquals(1, results.size)
        assertEquals("Essential", results[0].expenseType)
    }

    @Test
    fun syncBankCategoryTypesHandlesEmptyDb() = env().use { e ->
        e.repo.syncBankCategoryTypes()
        assertEquals(emptyList(), e.repo.getCategoryTypes())
    }

    // == update_categories (app.py orchestration) ==

    @Test
    fun updateCategoriesReweUpdatesByName() = env().use { e ->
        e.repo.insertReweExpenditure(date("2024-01-01"), listOf(Item(2, "Milk", 1.5, "", "Milk")))
        e.repo.updateCategories(CategoryTarget.REWE, listOf(CategoryUpdate("Milk", "Dairy")))
        val stored = e.q.selectReweExpenditures(null, null).executeAsList().first { it.name == "Milk" }.category
        assertEquals("Dairy", stored)
    }

    @Test
    fun updateCategoriesReweExpandsViaDisplayName() = env().use { e ->
        e.repo.insertReweExpenditure(
            date("2024-01-01"),
            listOf(Item(1, "AMAZON.DE", 10.0, ""), Item(1, "AMAZON GMBH", 20.0, "")),
        )
        e.q.updateReweDisplayNameByName("Amazon", "AMAZON.DE")
        e.q.updateReweDisplayNameByName("Amazon", "AMAZON GMBH")
        e.repo.updateCategories(CategoryTarget.REWE, listOf(CategoryUpdate("Amazon", "Shopping")))
        val stored = e.q.selectReweExpenditures(null, null).executeAsList()
            .filter { it.name in setOf("AMAZON.DE", "AMAZON GMBH") }.map { it.category }.toSet()
        assertEquals(setOf("Shopping"), stored)
    }

    @Test
    fun updateCategoriesBankUpdatesAndSyncsTypes() = env().use { e ->
        e.repo.insertGeneralExpenditure(listOf(BankDbEntry(1, date("2024-01-01"), "Me", "Amazon", 50.0, "test")))
        e.repo.updateCategories(CategoryTarget.BANK, listOf(CategoryUpdate("Amazon", "Shopping")))
        val stored = e.q.selectGeneralExpenditures(null, null).executeAsList().first { it.recipient == "Amazon" }.category
        assertEquals("Shopping", stored)
        assertEquals(
            setOf("Shopping" to ExpenseType.UNASSIGNED.value),
            e.repo.getCategoryTypes().map { it.category to it.expenseType }.toSet(),
        )
    }
}
