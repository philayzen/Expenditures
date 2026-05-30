package com.example.expenditure.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class ExpenseType(val value: String, val position: Int) {
    ESSENTIAL("Essential", 0),
    SUBSCRIPTION("Subscription", 1),
    ROUTINE("Routine", 2),
    PERIODIC("Periodic", 3),
    DURABLE("Durable", 4),
    ONE_TIME("One-time", 5),
    SAVINGS("Savings", 6),
    UNASSIGNED("Unassigned", 7),
}

/** Entries that [com.example.expenditure.db.ExpenditureRepository.updateDisplayNames] can dispatch on. */
sealed interface DisplayNameTarget

class BankDbEntry(
    val keyHelper: Long,
    val date: LocalDate,
    val source: String,
    recipient: String,
    val price: Double,
    val purpose: String?,
    val category: String? = null,
    val displayName: String? = null,
) {
    val recipient: String = if (recipient.isNotEmpty()) recipient else source

    fun withCategory(category: String?): BankDbEntry =
        BankDbEntry(keyHelper, date, source, recipient, price, purpose, category, displayName)
}

data class ReweExpenditureOutput(
    val date: LocalDateTime,
    val name: String,
    val amount: Long,
    val price: Double,
    val category: String?,
    val displayName: String? = null,
)

data class NameReferenceOutput(
    val name: String,
    val displayName: String,
)

data class CategoryTypeEntry(
    val category: String,
    val expenseType: String,
)

data class Item(
    val amount: Int,
    val name: String?,
    val price: Double,
    val category: String? = null,
    val displayName: String? = null,
    val altNames: Set<String>? = null,
) : DisplayNameTarget

data class BankExpenditureEntry(
    val keyHelper: Long?,
    val date: LocalDate?,
    val source: String?,
    val recipient: String?,
    val price: Double,
    val purpose: String?,
    val category: String?,
    val displayName: String?,
    val altNames: Set<String>? = null,
) : DisplayNameTarget

data class ReweExpenditureEntry(
    val date: LocalDateTime?,
    val name: String?,
    val amount: Int,
    val price: Double,
    val category: String?,
    val displayName: String?,
    val altNames: Set<String>? = null,
) : DisplayNameTarget

/** Mirrors the `{"name": ..., "category": ...}` dicts passed to the category-update functions. */
data class CategoryUpdate(
    val name: String,
    val category: String,
)

/** Selects which table [com.example.expenditure.db.ExpenditureRepository.updateCategories] writes to. */
enum class CategoryTarget { REWE, BANK }

data class BankCategoryEntry(
    val category: String?,
    val price: Double,
)

data class ReweCategoryEntry(
    val category: String?,
    val amount: Int,
    val price: Double,
)
