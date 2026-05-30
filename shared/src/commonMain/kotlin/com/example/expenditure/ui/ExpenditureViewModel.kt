package com.example.expenditure.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.expenditure.analysis.ExpenditureAnalysis
import com.example.expenditure.ui.chart.BarPoint
import com.example.expenditure.ui.chart.CategorySlice
import com.example.expenditure.ui.chart.barsFor
import kotlinx.datetime.LocalDate

enum class GroupMode(val label: String) { INDIVIDUAL("Individual"), BY_NAME("By Name") }

/** Flat view-row the REWE table renders. `date` is null when grouped (By-Name). */
data class ReweTableRow(
    val date: String?,
    val name: String,
    val amount: Int,
    val price: Double,
    val category: String,
)

/** Flat view-row the bank table renders. `date`/`source` are null when grouped (By-Name). */
data class BankTableRow(
    val date: String?,
    val source: String?,
    val name: String,
    val price: Double,
    val category: String,
)

/**
 * Compose-state-backed holder over [ExpenditureAnalysis]. Replaces the JS module-level globals
 * (per-tab individual/by-name datasets, active toggle, search query) with observable state so the
 * UI re-renders on change.
 */
class ExpenditureViewModel(private val analysis: ExpenditureAnalysis) {
    // ── REWE ───────────────────────────────────────────────────────────────────
    private var reweIndividual by mutableStateOf<List<ReweTableRow>>(emptyList())
    private var reweByName by mutableStateOf<List<ReweTableRow>>(emptyList())

    var reweMode by mutableStateOf(GroupMode.INDIVIDUAL)
    var reweQuery by mutableStateOf("")

    var reweSlices by mutableStateOf<List<CategorySlice>>(emptyList())
        private set
    var reweBars by mutableStateOf<List<BarPoint>>(emptyList())
        private set

    /** Date column only applies to the per-receipt individual view (mirrors JS `activate`). */
    val showReweDate: Boolean get() = reweMode == GroupMode.INDIVIDUAL

    val reweRows: List<ReweTableRow>
        get() = filterRows(
            if (reweMode == GroupMode.INDIVIDUAL) reweIndividual else reweByName,
            reweQuery,
        ) { it.name to it.category }

    fun loadRewe() {
        val individual = analysis.reweIndividually()
        reweIndividual = individual.map { e ->
            ReweTableRow(
                date = e.date?.date?.toString(),
                name = e.displayName.orEmpty(),
                amount = e.amount,
                price = e.price,
                category = e.category.orEmpty(),
            )
        }
        reweByName = analysis.reweGroupedByNameAndCat().map { item ->
            ReweTableRow(
                date = null,
                name = item.displayName.orEmpty(),
                amount = item.amount,
                price = item.price,
                category = item.category.orEmpty(),
            )
        }
        reweSlices = analysis.reweGroupedByCategory()
            .map { CategorySlice(it.category ?: UNCATEGORISED, it.price) }
        // REWE bar value mirrors JS: price × quantity. Bucket by calendar day (drop time-of-day).
        reweBars = barsFromDated(individual.mapNotNull { e -> e.date?.let { it.date to e.price * e.amount } })
    }

    // ── BANK ───────────────────────────────────────────────────────────────────
    private var bankIndividual by mutableStateOf<List<BankTableRow>>(emptyList())
    private var bankByName by mutableStateOf<List<BankTableRow>>(emptyList())

    var bankMode by mutableStateOf(GroupMode.INDIVIDUAL)
    var bankQuery by mutableStateOf("")

    var bankSlices by mutableStateOf<List<CategorySlice>>(emptyList())
        private set
    var bankBars by mutableStateOf<List<BarPoint>>(emptyList())
        private set

    /** Date + Bank (source) columns only apply to the individual view. */
    val showBankExtraColumns: Boolean get() = bankMode == GroupMode.INDIVIDUAL

    val bankRows: List<BankTableRow>
        get() = filterRows(
            if (bankMode == GroupMode.INDIVIDUAL) bankIndividual else bankByName,
            bankQuery,
        ) { it.name to it.category }

    fun loadBank() {
        val individual = analysis.generalIndividually()
        bankIndividual = individual.map { e ->
            BankTableRow(
                date = e.date?.toString(),
                source = e.source,
                name = e.displayName.orEmpty(),
                price = e.price,
                category = e.category.orEmpty(),
            )
        }
        bankByName = analysis.generalGroupedByNameAndCat().map { e ->
            BankTableRow(
                date = null,
                source = null,
                name = e.displayName.orEmpty(),
                price = e.price,
                category = e.category.orEmpty(),
            )
        }
        bankSlices = analysis.generalGroupedByCategory()
            .map { CategorySlice(it.category ?: UNCATEGORISED, it.price) }
        bankBars = barsFromDated(individual.mapNotNull { e -> e.date?.let { it to e.price } })
    }
}

private const val UNCATEGORISED = "Uncategorised"

/** Derives the chart range from the data itself (no date filter yet), then buckets into bars. */
private fun barsFromDated(points: List<Pair<LocalDate, Double>>): List<BarPoint> {
    if (points.isEmpty()) return emptyList()
    val dates = points.map { it.first }
    return barsFor(points, dates.min(), dates.max())
}

/** Search filter shared by both tables: match the query against name or category (case-insensitive). */
private inline fun <T> filterRows(rows: List<T>, query: String, fields: (T) -> Pair<String, String>): List<T> {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return rows
    return rows.filter {
        val (name, category) = fields(it)
        name.lowercase().contains(q) || category.lowercase().contains(q)
    }
}
