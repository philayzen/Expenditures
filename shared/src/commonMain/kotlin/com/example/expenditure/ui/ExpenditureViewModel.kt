package com.example.expenditure.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.expenditure.analysis.ExpenditureAnalysis
import com.example.expenditure.model.BankExpenditureEntry
import com.example.expenditure.model.CategoryTypeEntry
import com.example.expenditure.model.CategoryUpdate
import com.example.expenditure.model.ExpenseType
import com.example.expenditure.ui.chart.BarPoint
import com.example.expenditure.ui.chart.CategorySlice
import com.example.expenditure.ui.chart.StackedBarData
import com.example.expenditure.ui.chart.TypedPoint
import com.example.expenditure.ui.chart.barsFor
import com.example.expenditure.ui.chart.stackedBarsFor
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.monthsUntil

enum class GroupMode(val label: String) { INDIVIDUAL("Individual"), BY_NAME("By Name") }

/** Quick-range presets mirrored from the web app's `computeDateRange` (utils.js). */
enum class DateRangePreset(val label: String) {
    PREV_MONTH("Previous Month"),
    THIS_YEAR("This Year"),
    PREV_YEAR("Previous Year"),
    LAST_365("Last 365 Days"),
}

/**
 * Flat view-row the REWE table renders. `date` is null when grouped (By-Name).
 * [altNames] holds the raw DB name(s) underlying [name]; single-element for individual rows,
 * multi-element when grouped By-Name. Used by the rename popup to show what is being renamed.
 */
data class ReweTableRow(
    val date: String?,
    val name: String,
    val amount: Int,
    val price: Double,
    val category: String,
    val altNames: Set<String> = emptySet(),
)

/**
 * Flat view-row the bank table renders. `date`/`source` are null when grouped (By-Name).
 * [altNames] holds the original recipient name(s). [purpose] is populated only for individual rows.
 */
data class BankTableRow(
    val date: String?,
    val source: String?,
    val name: String,
    val price: Double,
    val category: String,
    val altNames: Set<String> = emptySet(),
    val purpose: String? = null,
)

/**
 * Compose-state-backed holder over [ExpenditureAnalysis]. Replaces the JS module-level globals
 * (per-tab individual/by-name datasets, active toggle, search query) with observable state so the
 * UI re-renders on change.
 */
class ExpenditureViewModel(private val analysis: ExpenditureAnalysis) {
    // ── DATE FILTER ──────────────────────────────────────────────────────────────
    // Raw YYYY-MM-DD input text mirroring the web's date inputs; blank means unbounded.
    var sinceText by mutableStateOf("")
    var untilText by mutableStateOf("")

    private val since: LocalDate? get() = parseDate(sinceText)
    private val until: LocalDate? get() = parseDate(untilText)

    /** Reloads both tabs against the current [sinceText]/[untilText] bounds. */
    fun applyDateRange() {
        loadRewe()
        loadBank()
    }

    /** Fills the date inputs from a quick-range preset and reloads, mirroring `setDateRange`. */
    fun applyPreset(preset: DateRangePreset) {
        val (s, u) = computeDateRange(preset)
        sinceText = s?.toString().orEmpty()
        untilText = u?.toString().orEmpty()
        applyDateRange()
    }

    // ── REWE ───────────────────────────────────────────────────────────────────
    private var reweIndividual by mutableStateOf<List<ReweTableRow>>(emptyList())
    private var reweByName by mutableStateOf<List<ReweTableRow>>(emptyList())

    var reweMode by mutableStateOf(GroupMode.INDIVIDUAL)
    var reweQuery by mutableStateOf("")

    var reweSlices by mutableStateOf<List<CategorySlice>>(emptyList())
        private set
    var reweBars by mutableStateOf<List<BarPoint>>(emptyList())
        private set

    /** Calendar months spanned by the REWE data; used to derive the per-month average in the doughnut tooltip. */
    var reweMonths by mutableStateOf(1)
        private set

    /** Date column only applies to the per-receipt individual view (mirrors JS `activate`). */
    val showReweDate: Boolean get() = reweMode == GroupMode.INDIVIDUAL

    val reweRows: List<ReweTableRow>
        get() = filterRows(
            if (reweMode == GroupMode.INDIVIDUAL) reweIndividual else reweByName,
            reweQuery,
        ) { it.name to it.category }

    fun loadRewe() {
        val individual = analysis.reweIndividually(since, until)
        reweIndividual = individual.map { e ->
            ReweTableRow(
                date = e.date?.date?.toString(),
                name = e.displayName.orEmpty(),
                amount = e.amount,
                price = e.price,
                category = e.category.orEmpty(),
                altNames = setOfNotNull(e.name?.takeIf { it.isNotEmpty() }),
            )
        }
        reweByName = analysis.reweGroupedByNameAndCat(since, until).map { item ->
            ReweTableRow(
                date = null,
                name = item.displayName.orEmpty(),
                amount = item.amount,
                price = item.price,
                category = item.category.orEmpty(),
                altNames = item.altNames?.takeIf { it.isNotEmpty() }
                    ?: setOf(item.displayName.orEmpty()),
            )
        }
        reweSlices = analysis.reweGroupedByCategory(since, until)
            .map { CategorySlice(it.category ?: UNCATEGORISED, it.price) }
        // REWE bar value mirrors JS: price × quantity. Bucket by calendar day (drop time-of-day).
        val datedPoints = individual.mapNotNull { e -> e.date?.let { it.date to e.price * e.amount } }
        reweBars = barsFromDated(datedPoints)
        reweMonths = monthsSpanned(datedPoints.map { it.first })
    }

    // ── BANK ───────────────────────────────────────────────────────────────────
    private var bankIndividual by mutableStateOf<List<BankTableRow>>(emptyList())
    private var bankByName by mutableStateOf<List<BankTableRow>>(emptyList())

    var bankMode by mutableStateOf(GroupMode.INDIVIDUAL)
    var bankQuery by mutableStateOf("")

    var bankSlices by mutableStateOf<List<CategorySlice>>(emptyList())
        private set

    /** Bank "Spending Over Time" data, stacked by expense type (mirrors the web's `bank_type_map` branch). */
    var bankStackedBars by mutableStateOf(StackedBarData.EMPTY)
        private set

    /** Calendar months spanned by the bank data; used to derive the per-month average in the doughnut tooltip. */
    var bankMonths by mutableStateOf(1)
        private set

    /** Date + Bank (source) columns only apply to the individual view. */
    val showBankExtraColumns: Boolean get() = bankMode == GroupMode.INDIVIDUAL

    val bankRows: List<BankTableRow>
        get() = filterRows(
            if (bankMode == GroupMode.INDIVIDUAL) bankIndividual else bankByName,
            bankQuery,
        ) { it.name to it.category }

    /**
     * Persists category edits for the REWE table. Only calls through if there are actual changes;
     * always reloads so By-Name rows with the same (name, new-category) are merged automatically.
     */
    fun saveReweCategories(changes: List<CategoryUpdate>) {
        if (changes.isEmpty()) return
        analysis.updateReweCategories(changes)
        loadRewe()
    }

    /**
     * Renames all DB rows whose original name is in [altNames] to [newName] as the display name,
     * then reloads both tables (mirrors JS `submit_rename` → `update_refresh`).
     * [updateDisplayNameByName] writes to both REWE and bank tables, so both need reloading.
     */
    fun rename(altNames: Set<String>, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        altNames.filter { it.isNotEmpty() }.forEach { analysis.updateDisplayNameByName(it, trimmed) }
        loadRewe()
        loadBank()
    }

    /** Same as [saveReweCategories] but for the bank table. */
    fun saveBankCategories(changes: List<CategoryUpdate>) {
        if (changes.isEmpty()) return
        analysis.updateBankCategories(changes)
        loadBank()
    }

    // ── CATEGORY TYPES (Manage Types) ───────────────────────────────────────────
    /** Current category→expense-type assignments shown in the type-manager popup. */
    var categoryTypes by mutableStateOf<List<CategoryTypeEntry>>(emptyList())
        private set

    /**
     * Whether the type-manager popup is open. Hoisted here (rather than into a single composable)
     * so the trigger button and the dialog can live in different composables/files.
     */
    var showTypeManager by mutableStateOf(false)
        private set

    /** Loads the current assignments and opens the popup. */
    fun openTypeManager() {
        loadCategoryTypes()
        showTypeManager = true
    }

    /** Closes the popup. */
    fun dismissTypeManager() {
        showTypeManager = false
    }

    /** Loads category→type assignments (syncs first so every category has a row). */
    fun loadCategoryTypes() {
        categoryTypes = analysis.categoryTypes()
    }

    /**
     * Persists category→type edits as (category, expenseType-value) pairs, then reloads so the
     * popup reflects the stored state (mirrors `saveTypeAssignments` → `/update_category_types`).
     */
    fun saveCategoryTypes(edits: List<Pair<String, String>>) {
        if (edits.isEmpty()) return
        analysis.updateCategoryTypes(edits)
        loadCategoryTypes()
    }

    fun loadBank() {
        // Refresh the category→type map first so the stacked bar chart reflects current assignments
        // (and so newly-seen bank categories get a row, mirroring the web's `sync_bank_category_types`).
        loadCategoryTypes()
        val typeMap = categoryTypes.associate { it.category to it.expenseType }

        val individual = analysis.generalIndividually(since, until)
        bankIndividual = individual.map { e ->
            BankTableRow(
                date = e.date?.toString(),
                source = e.source,
                name = e.displayName.orEmpty(),
                price = e.price,
                category = e.category.orEmpty(),
                altNames = setOfNotNull(e.recipient?.takeIf { it.isNotEmpty() }),
                purpose = e.purpose,
            )
        }
        bankByName = analysis.generalGroupedByNameAndCat(since, until).map { e ->
            BankTableRow(
                date = null,
                source = null,
                name = e.displayName.orEmpty(),
                price = e.price,
                category = e.category.orEmpty(),
                altNames = e.altNames?.takeIf { it.isNotEmpty() }
                    ?: setOf(e.displayName.orEmpty()),
                purpose = null, // purpose is per-transaction; not meaningful for aggregated rows
            )
        }
        bankSlices = analysis.generalGroupedByCategory(since, until)
            .map { CategorySlice(it.category ?: UNCATEGORISED, it.price) }
        bankStackedBars = stackedBarsFromBank(individual, typeMap)
        val datedPoints = individual.mapNotNull { e -> e.date?.let { it to e.price } }
        bankMonths = monthsSpanned(datedPoints.map { it.first })
    }

    /** Tags each dated bank entry with its expense type (default Unassigned) and buckets into stacked bars. */
    private fun stackedBarsFromBank(
        entries: List<BankExpenditureEntry>,
        typeMap: Map<String, String>,
    ): StackedBarData {
        val points = entries.mapNotNull { e ->
            e.date?.let { d ->
                val type = e.category?.let { typeMap[it] } ?: ExpenseType.UNASSIGNED.value
                TypedPoint(d, e.price, type)
            }
        }
        if (points.isEmpty()) return StackedBarData.EMPTY
        val dates = points.map { it.date }
        return stackedBarsFor(points, dates.min(), dates.max())
    }
}

/** Number of calendar months the data covers (inclusive), floored at 1 so per-month math never divides by zero. */
private fun monthsSpanned(dates: List<LocalDate>): Int {
    if (dates.isEmpty()) return 1
    return (dates.min().monthsUntil(dates.max()) + 1).coerceAtLeast(1)
}

private const val UNCATEGORISED = "Uncategorised"

/** Parses a YYYY-MM-DD input to a [LocalDate]; blank or malformed text yields null (unbounded). */
private fun parseDate(text: String): LocalDate? =
    text.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/**
 * Current calendar date in the system zone. Implemented per-platform (`java.time`) because
 * kotlinx-datetime 0.7.1's `kotlin.time.Clock` bridge is binary-incompatible with this Kotlin version.
 */
internal expect fun currentLocalDate(): LocalDate

/** Mirrors `computeDateRange` in utils.js: maps a preset to inclusive since/until calendar bounds. */
private fun computeDateRange(preset: DateRangePreset): Pair<LocalDate?, LocalDate?> {
    val today = currentLocalDate()
    return when (preset) {
        DateRangePreset.PREV_MONTH -> {
            val firstThisMonth = LocalDate(today.year, today.month, 1)
            val lastPrevMonth = firstThisMonth.minus(1, DateTimeUnit.DAY)
            val firstPrevMonth = LocalDate(lastPrevMonth.year, lastPrevMonth.month, 1)
            firstPrevMonth to lastPrevMonth
        }
        DateRangePreset.THIS_YEAR -> LocalDate(today.year, 1, 1) to today
        DateRangePreset.PREV_YEAR ->
            LocalDate(today.year - 1, 1, 1) to LocalDate(today.year - 1, 12, 31)
        DateRangePreset.LAST_365 -> today.minus(365, DateTimeUnit.DAY) to today
    }
}

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
