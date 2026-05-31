package com.example.expenditure.db

import com.example.expenditure.model.BankDbEntry
import com.example.expenditure.model.BankExpenditureEntry
import com.example.expenditure.model.CategoryTarget
import com.example.expenditure.model.CategoryTypeEntry
import com.example.expenditure.model.CategoryUpdate
import com.example.expenditure.model.DisplayNameTarget
import com.example.expenditure.model.ExpenseType
import com.example.expenditure.model.Item
import com.example.expenditure.model.NameReferenceOutput
import com.example.expenditure.model.ReweExpenditureEntry
import com.example.expenditure.model.ReweExpenditureOutput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime

class ExpenditureRepository(database: ExpenditureDatabase) {
    private val queries = database.expendituresQueries

    fun getGeneralExpenditures(
        since: LocalDate? = null,
        until: LocalDate? = null,
        bankIgnore: List<String> = emptyList(),
    ): List<BankDbEntry> =
        queries.selectGeneralExpenditures(since, until) {
                keyHelper, date, source, recipient, price, purpose, category, displayName ->
            BankDbEntry(keyHelper, date, source, recipient, -price / 100, purpose, category, displayName)
        }.executeAsList()
            .filter { it.recipient !in bankIgnore }

    fun getReweExpenditures(
        since: LocalDate? = null,
        until: LocalDate? = null,
    ): List<ReweExpenditureOutput> =
        // REWE dates carry a time-of-day; widen the calendar-day filter to cover the whole day.
        queries.selectReweExpenditures(since?.atTime(0, 0), until?.atTime(23, 59, 59)) {
                date, name, amount, price, category, displayName ->
            ReweExpenditureOutput(date, name, amount, price / 100, category, displayName)
        }.executeAsList()

    fun insertGeneralExpenditure(data: List<BankDbEntry>) {
        queries.transaction {
            data.forEach { row ->
                queries.insertGeneralExpenditure(
                    key_helper = row.keyHelper,
                    date = row.date,
                    source = row.source,
                    recipient = row.recipient,
                    purpose = row.purpose,
                    price = row.price * 100,
                    category = row.category,
                )
            }
        }
    }

    /** Convenience for callers that only have a calendar day; stored at midnight. */
    fun insertReweExpenditure(date: LocalDate, items: List<Item>) =
        insertReweExpenditure(date.atTime(0, 0), items)

    fun insertReweExpenditure(dateTime: LocalDateTime, items: List<Item>) {
        // Each receipt has a unique timestamp, so this skips re-importing that exact receipt only.
        if (queries.countReweByDate(dateTime).executeAsOne() != 0L) return
        queries.transaction {
            items.forEach { item ->
                queries.insertReweExpenditure(
                    date = dateTime,
                    amount = item.amount.toLong(),
                    name = requireNotNull(item.name) { "Item.name is required to insert" },
                    price = item.price * 100,
                    category = item.category,
                )
            }
        }
    }

    fun updateDisplayNames(entries: List<DisplayNameTarget>, newName: String) {
        entries.forEach { entry ->
            when (entry) {
                is BankExpenditureEntry -> updateBankEntryDisplayName(entry, newName)
                is ReweExpenditureEntry -> updateReweDisplayName(entry.name, entry.altNames, newName)
                is Item -> updateReweDisplayName(entry.name, entry.altNames, newName)
            }
        }
    }

    private fun updateBankEntryDisplayName(entry: BankExpenditureEntry, newName: String) {
        val recipient = entry.recipient
        if (recipient != null) {
            queries.updateBankDisplayNameByPk(
                displayName = newName,
                keyHelper = requireNotNull(entry.keyHelper),
                date = requireNotNull(entry.date),
                source = requireNotNull(entry.source),
                recipient = recipient,
            )
        } else if (!entry.altNames.isNullOrEmpty()) {
            queries.updateBankDisplayNameByAltNames(newName, entry.altNames, entry.category)
        }
    }

    private fun updateReweDisplayName(name: String?, altNames: Set<String>?, newName: String) {
        if (name != null) {
            queries.updateReweDisplayNameByName(newName, name)
        } else if (!altNames.isNullOrEmpty()) {
            queries.updateReweDisplayNameByAltNames(newName, altNames)
        }
    }

    fun updateDisplayNameByName(name: String, newDisplayName: String) {
        queries.updateBankDisplayNameByRecipient(newDisplayName, name)
        queries.updateReweDisplayNameByName(newDisplayName, name)
    }

    fun getNameReferences(names: List<String>): List<NameReferenceOutput> {
        if (names.isEmpty()) return emptyList()
        val rewe = queries.selectReweNameReferences(names) { name, displayName ->
            NameReferenceOutput(name, displayName.orEmpty())
        }.executeAsList()
        val bank = queries.selectBankNameReferences(names) { recipient, displayName ->
            NameReferenceOutput(recipient, displayName.orEmpty())
        }.executeAsList()
        return rewe + bank
    }

    fun getOriginalNames(reference: String): List<String> {
        val results = queries.selectReweOriginalNames(reference).executeAsList() +
            queries.selectBankOriginalNames(reference).executeAsList()
        return results.ifEmpty { listOf(reference) }
    }

    /**
     * Expands each entry's display name back to its original DB names, then applies the category to
     * all of them. Mirrors `app.py`'s `/update_categories`: the bank path additionally syncs
     * category types so newly introduced categories get an [ExpenseType] row.
     */
    fun updateCategories(target: CategoryTarget, data: List<CategoryUpdate>) {
        val transformed = data.flatMap { entry ->
            getOriginalNames(entry.name).map { CategoryUpdate(it, entry.category) }
        }
        when (target) {
            CategoryTarget.REWE -> updateReweCategoriesInDb(transformed)
            CategoryTarget.BANK -> {
                updateBankCategoriesInDb(transformed)
                syncBankCategoryTypes()
            }
        }
    }

    fun updateReweCategoriesInDb(data: List<CategoryUpdate>) {
        queries.transaction {
            data.forEach { queries.updateReweCategory(it.category, it.name) }
        }
    }

    fun updateBankCategoriesInDb(data: List<CategoryUpdate>) {
        queries.transaction {
            data.forEach { queries.updateBankCategory(it.category, it.name) }
        }
    }

    fun getReweMostCommonCategories(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        return mostCommon(
            queries.selectReweMostCommonCategories(names) { name, category -> name to category.orEmpty() }
                .executeAsList()
        )
    }

    fun getBankMostCommonCategories(names: List<String>): Map<String, String> {
        if (names.isEmpty()) return emptyMap()
        return mostCommon(
            queries.selectBankMostCommonCategories(names) { recipient, category -> recipient to category.orEmpty() }
                .executeAsList()
        )
    }

    /** First occurrence wins; rows are pre-ordered by frequency desc, mirroring core.py. */
    private fun mostCommon(rows: List<Pair<String, String>>): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((name, category) in rows) {
            if (name !in result) result[name] = category
        }
        return result
    }

    fun getCategoryTypes(): List<CategoryTypeEntry> =
        queries.selectCategoryTypes { category, expenseType -> CategoryTypeEntry(category, expenseType) }
            .executeAsList()

    fun updateCategoryTypes(data: List<Pair<String, String>>) {
        queries.transaction {
            data.forEach { (category, expenseType) -> queries.upsertCategoryType(category, expenseType) }
        }
    }

    fun getDistinctBankCategories(): List<String> =
        queries.selectDistinctBankCategories().executeAsList().mapNotNull { it.category }

    fun syncBankCategoryTypes() {
        val current = getDistinctBankCategories().toSet()
        val existing = queries.selectCategoryTypeCategories().executeAsList().toSet()
        queries.transaction {
            (existing - current).forEach { queries.deleteCategoryType(it) }
            (current - existing).forEach { queries.insertCategoryType(it, ExpenseType.UNASSIGNED.value) }
        }
    }
}
