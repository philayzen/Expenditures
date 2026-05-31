package com.example.expenditure.ui.chart

import com.example.expenditure.model.ExpenseType
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus
import kotlin.math.ceil
import kotlin.math.round

/**
 * Pure port of the time-bucketing in `utils.js` (`buildEmptyBuckets`/`groupBuckets`). Turns a set of
 * dated values into the labelled bars the "Spending Over Time" chart draws. Monthly buckets when the
 * range exceeds 61 days, daily otherwise; buckets are then grouped down to at most `maxBars` bars.
 */

private val MONTHS = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** `month` is 1-based; `day` is null in monthly mode. */
data class TimeBucket(val year: Int, val month: Int, val day: Int?, var value: Double)

data class BarPoint(val label: String, val value: Double)

fun buildEmptyBuckets(since: LocalDate, until: LocalDate): Pair<List<TimeBucket>, Boolean> {
    val useMonths = since.daysUntil(until) > 61
    val buckets = mutableListOf<TimeBucket>()
    if (useMonths) {
        var d = LocalDate(since.year, since.monthNumber, 1)
        val end = LocalDate(until.year, until.monthNumber, 1)
        while (d <= end) {
            buckets.add(TimeBucket(d.year, d.monthNumber, null, 0.0))
            d = d.plus(1, DateTimeUnit.MONTH)
        }
    } else {
        var d = since
        while (d <= until) {
            buckets.add(TimeBucket(d.year, d.monthNumber, d.dayOfMonth, 0.0))
            d = d.plus(1, DateTimeUnit.DAY)
        }
    }
    return buckets to useMonths
}

private fun assign(buckets: List<TimeBucket>, points: List<Pair<LocalDate, Double>>, useMonths: Boolean) {
    for ((date, value) in points) {
        val idx = if (useMonths) {
            buckets.indexOfFirst { it.year == date.year && it.month == date.monthNumber }
        } else {
            buckets.indexOfFirst {
                it.year == date.year && it.month == date.monthNumber && it.day == date.dayOfMonth
            }
        }
        if (idx >= 0) buckets[idx].value += value
    }
}

fun groupBuckets(buckets: List<TimeBucket>, useMonths: Boolean, maxBars: Int): List<BarPoint> {
    if (buckets.isEmpty()) return emptyList()
    val groupSize = maxOf(1, ceil(buckets.size.toDouble() / maxBars).toInt())
    val grouped = mutableListOf<BarPoint>()
    var i = 0
    while (i < buckets.size) {
        val group = buckets.subList(i, minOf(i + groupSize, buckets.size))
        val value = round(group.sumOf { it.value } * 100) / 100
        val f = group.first()
        val l = group.last()
        val label = if (useMonths) {
            when {
                groupSize == 1 -> "${month(f)} '${yy(f.year)}"
                f.year == l.year -> "${month(f)}–${month(l)} '${yy(f.year)}"
                else -> "${month(f)} '${yy(f.year)}–${month(l)} '${yy(l.year)}"
            }
        } else {
            when {
                groupSize == 1 -> "${month(f)} ${f.day}"
                f.month == l.month -> "${month(f)} ${f.day}–${l.day}"
                else -> "${month(f)} ${f.day}–${month(l)} ${l.day}"
            }
        }
        grouped.add(BarPoint(label, value))
        i += groupSize
    }
    return grouped
}

/** High-level entry point: dated values + range → bars (max 12 monthly / 10 daily). */
fun barsFor(points: List<Pair<LocalDate, Double>>, since: LocalDate, until: LocalDate): List<BarPoint> {
    val (buckets, useMonths) = buildEmptyBuckets(since, until)
    assign(buckets, points, useMonths)
    return groupBuckets(buckets, useMonths, if (useMonths) 12 else 10)
}

// ── Stacked-by-expense-type variant (bank bar chart) ────────────────────────────

/** A dated value tagged with its expense type, for the stacked bar chart. */
data class TypedPoint(val date: LocalDate, val value: Double, val type: String)

/** One stacked segment: an expense type and its per-bar totals (aligned with [StackedBarData.labels]). */
data class BarSeries(val type: String, val values: List<Double>)

/** Per-bar labels plus one [BarSeries] per expense type present, sorted by [ExpenseType.position]. */
data class StackedBarData(val labels: List<String>, val series: List<BarSeries>) {
    val isEmpty: Boolean get() = labels.isEmpty() || series.isEmpty()

    companion object {
        val EMPTY = StackedBarData(emptyList(), emptyList())
    }
}

private fun typePosition(type: String): Int =
    ExpenseType.entries.firstOrNull { it.value == type }?.position ?: Int.MAX_VALUE

/**
 * Stacked-bar port of `buildBarChart`'s `typeMap` branch in `js.js`. Buckets [points] by time the
 * same way [barsFor] does, but keeps a per-expense-type running total per bucket, then groups each
 * type's array down to the same bar count. Types with no positive total are dropped; the rest are
 * ordered by [ExpenseType.position] so segment colours stay stable across reloads.
 */
fun stackedBarsFor(points: List<TypedPoint>, since: LocalDate, until: LocalDate): StackedBarData {
    val (buckets, useMonths) = buildEmptyBuckets(since, until)
    val maxBars = if (useMonths) 12 else 10

    val typeTotals = LinkedHashMap<String, DoubleArray>()
    for (p in points) {
        val idx = if (useMonths) {
            buckets.indexOfFirst { it.year == p.date.year && it.month == p.date.monthNumber }
        } else {
            buckets.indexOfFirst {
                it.year == p.date.year && it.month == p.date.monthNumber && it.day == p.date.dayOfMonth
            }
        }
        if (idx >= 0) {
            buckets[idx].value += p.value
            typeTotals.getOrPut(p.type) { DoubleArray(buckets.size) }[idx] += p.value
        }
    }

    val labels = groupBuckets(buckets, useMonths, maxBars).map { it.label }
    val groupSize = maxOf(1, ceil(buckets.size.toDouble() / maxBars).toInt())

    val series = typeTotals.entries
        .filter { entry -> entry.value.any { it > 0 } }
        .sortedBy { typePosition(it.key) }
        .map { (type, arr) ->
            val grouped = mutableListOf<Double>()
            var i = 0
            while (i < arr.size) {
                val end = minOf(i + groupSize, arr.size)
                var sum = 0.0
                for (k in i until end) sum += arr[k]
                grouped.add(round(sum * 100) / 100)
                i += groupSize
            }
            BarSeries(type, grouped)
        }
    return StackedBarData(labels, series)
}

private fun month(b: TimeBucket) = MONTHS[b.month - 1]

private fun yy(year: Int) = (year % 100).toString().padStart(2, '0')
