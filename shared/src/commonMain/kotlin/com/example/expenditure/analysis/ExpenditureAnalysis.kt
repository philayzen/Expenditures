package com.example.expenditure.analysis

import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.model.BankCategoryEntry
import com.example.expenditure.model.BankDbEntry
import com.example.expenditure.model.BankExpenditureEntry
import com.example.expenditure.model.CategoryTarget
import com.example.expenditure.model.CategoryTypeEntry
import com.example.expenditure.model.CategoryUpdate
import com.example.expenditure.model.Item
import com.example.expenditure.model.ReweCategoryEntry
import com.example.expenditure.model.ReweExpenditureEntry
import com.example.expenditure.model.ReweExpenditureOutput
import kotlin.math.round
import kotlinx.datetime.LocalDate

private fun bankNameRef(entry: BankDbEntry): String =
    entry.displayName?.takeIf { it.isNotEmpty() } ?: entry.recipient

private fun reweNameRef(entry: ReweExpenditureOutput): String =
    entry.displayName?.takeIf { it.isNotEmpty() } ?: entry.name

private class BankAcc(
    val keyHelper: Long?,
    val date: LocalDate?,
    val source: String?,
    val purpose: String?,
    val category: String?,
    val displayName: String,
    var price: Double,
    val altNames: MutableSet<String>,
) {
    fun toEntry() = BankExpenditureEntry(
        keyHelper = keyHelper, date = date, source = source, recipient = null,
        price = price, purpose = purpose, category = category,
        displayName = displayName, altNames = altNames,
    )
}

private class ReweAcc(
    val category: String?,
    val displayName: String,
    var amount: Int,
    var price: Double,
    val altNames: MutableSet<String>,
) {
    fun toItem() = Item(
        amount = amount, name = null, price = price, category = category,
        displayName = displayName, altNames = altNames,
    )
}

/** Per-row view with the display-name fallback resolved. Mirrors `app.py`'s `*_individually` routes. */
internal fun resolveReweIndividually(rows: List<ReweExpenditureOutput>): List<ReweExpenditureEntry> =
    rows.map { e ->
        ReweExpenditureEntry(
            date = e.date, name = e.name, amount = e.amount.toInt(), price = e.price,
            category = e.category, displayName = reweNameRef(e),
        )
    }

internal fun resolveGeneralIndividually(rows: List<BankDbEntry>): List<BankExpenditureEntry> =
    rows.map { e ->
        BankExpenditureEntry(
            keyHelper = e.keyHelper, date = e.date, source = e.source, recipient = e.recipient,
            price = e.price, purpose = e.purpose, category = e.category, displayName = bankNameRef(e),
        )
    }

internal fun groupGeneralByName(expenditures: List<BankDbEntry>): List<BankExpenditureEntry> {
    val acc = LinkedHashMap<String, BankAcc>()
    for (e in expenditures) {
        val nameRef = bankNameRef(e)
        val existing = acc[nameRef]
        if (existing == null) {
            acc[nameRef] = BankAcc(
                keyHelper = null, date = null, source = null, purpose = e.purpose,
                category = e.category, displayName = nameRef, price = e.price,
                altNames = mutableSetOf(e.recipient),
            )
        } else {
            existing.price += e.price
            existing.altNames.add(e.recipient)
        }
    }
    return acc.values.map { it.toEntry() }.sortedByDescending { it.price }
}

internal fun groupGeneralByNameAndCat(expenditures: List<BankDbEntry>): List<BankExpenditureEntry> {
    val acc = LinkedHashMap<Pair<String, String?>, BankAcc>()
    for (e in expenditures) {
        val nameRef = bankNameRef(e)
        val key = nameRef to e.category
        val existing = acc[key]
        if (existing == null) {
            acc[key] = BankAcc(
                keyHelper = e.keyHelper, date = e.date, source = e.source, purpose = e.purpose,
                category = e.category, displayName = nameRef, price = e.price,
                altNames = mutableSetOf(e.recipient),
            )
        } else {
            existing.price += e.price
            existing.altNames.add(e.recipient)
        }
    }
    return acc.values.map { it.toEntry() }.sortedByDescending { it.price }
}

internal fun groupGeneralByCategory(expenditures: List<BankDbEntry>): List<BankCategoryEntry> {
    val acc = LinkedHashMap<String?, Double>()
    for (e in expenditures) {
        acc[e.category] = (acc[e.category] ?: 0.0) + e.price
    }
    return acc.map { BankCategoryEntry(category = it.key, price = it.value) }
        .sortedByDescending { it.price }
}

internal fun groupReweByName(expenditures: List<ReweExpenditureOutput>): List<Item> {
    val acc = LinkedHashMap<String, ReweAcc>()
    for (e in expenditures) {
        val nameRef = reweNameRef(e)
        val existing = acc[nameRef]
        if (existing == null) {
            acc[nameRef] = ReweAcc(
                category = e.category, displayName = nameRef, amount = e.amount.toInt(),
                price = e.price, altNames = mutableSetOf(e.name),
            )
        } else {
            existing.amount += e.amount.toInt()
            existing.price += e.price
            existing.altNames.add(e.name)
        }
    }
    return acc.values.map { it.toItem() }.sortedByDescending { it.price }
}

internal fun groupReweByNameAndCat(expenditures: List<ReweExpenditureOutput>): List<Item> {
    val acc = LinkedHashMap<Pair<String, String?>, ReweAcc>()
    for (e in expenditures) {
        val nameRef = reweNameRef(e)
        val key = nameRef to e.category
        val existing = acc[key]
        if (existing == null) {
            acc[key] = ReweAcc(
                category = e.category, displayName = nameRef, amount = e.amount.toInt(),
                price = e.price, altNames = mutableSetOf(e.name),
            )
        } else {
            existing.amount += e.amount.toInt()
            existing.price += e.price
            existing.altNames.add(e.name)
        }
    }
    return acc.values.map { it.toItem() }.sortedByDescending { it.price }
}

internal fun groupReweByCategory(expenditures: List<ReweExpenditureOutput>): List<ReweCategoryEntry> {
    val acc = LinkedHashMap<String?, ReweCategoryEntry>()
    for (e in expenditures) {
        val existing = acc[e.category]
        acc[e.category] = if (existing == null) {
            ReweCategoryEntry(category = e.category, amount = e.amount.toInt(), price = e.price)
        } else {
            existing.copy(amount = existing.amount + e.amount.toInt(), price = existing.price + e.price)
        }
    }
    return acc.values.sortedByDescending { round(it.price * 100.0) / 100.0 }
}

/** Mirrors the module-level grouping API of `script.py`, fetching via the repository then grouping. */
class ExpenditureAnalysis(private val repository: ExpenditureRepository) {
    fun reweIndividually(since: LocalDate? = null, until: LocalDate? = null): List<ReweExpenditureEntry> =
        resolveReweIndividually(repository.getReweExpenditures(since, until))

    fun generalIndividually(since: LocalDate? = null, until: LocalDate? = null): List<BankExpenditureEntry> =
        resolveGeneralIndividually(repository.getGeneralExpenditures(since, until))

    fun generalGroupedByName(since: LocalDate? = null, until: LocalDate? = null): List<BankExpenditureEntry> =
        groupGeneralByName(repository.getGeneralExpenditures(since, until))

    fun generalGroupedByNameAndCat(since: LocalDate? = null, until: LocalDate? = null): List<BankExpenditureEntry> =
        groupGeneralByNameAndCat(repository.getGeneralExpenditures(since, until))

    fun generalGroupedByCategory(since: LocalDate? = null, until: LocalDate? = null): List<BankCategoryEntry> =
        groupGeneralByCategory(repository.getGeneralExpenditures(since, until))

    fun reweGroupedByName(since: LocalDate? = null, until: LocalDate? = null): List<Item> =
        groupReweByName(repository.getReweExpenditures(since, until))

    fun reweGroupedByNameAndCat(since: LocalDate? = null, until: LocalDate? = null): List<Item> =
        groupReweByNameAndCat(repository.getReweExpenditures(since, until))

    fun reweGroupedByCategory(since: LocalDate? = null, until: LocalDate? = null): List<ReweCategoryEntry> =
        groupReweByCategory(repository.getReweExpenditures(since, until))

    fun updateReweCategories(data: List<CategoryUpdate>) =
        repository.updateCategories(CategoryTarget.REWE, data)

    fun updateBankCategories(data: List<CategoryUpdate>) =
        repository.updateCategories(CategoryTarget.BANK, data)

    /** Updates the display name for every REWE/bank row whose original DB name matches [name]. */
    fun updateDisplayNameByName(name: String, newDisplayName: String) =
        repository.updateDisplayNameByName(name, newDisplayName)

    /**
     * Category→expense-type assignments for the type manager. Syncs first so every distinct bank
     * category has a row (defaulting to Unassigned), mirroring the web's `sync_bank_category_types`.
     */
    fun categoryTypes(): List<CategoryTypeEntry> {
        repository.syncBankCategoryTypes()
        return repository.getCategoryTypes()
    }

    /** Persists category→expense-type assignments (mirrors `update_category_types`). */
    fun updateCategoryTypes(data: List<Pair<String, String>>) =
        repository.updateCategoryTypes(data)
}
