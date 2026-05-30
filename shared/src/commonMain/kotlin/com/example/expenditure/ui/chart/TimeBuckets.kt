package com.example.expenditure.ui.chart

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

private fun month(b: TimeBucket) = MONTHS[b.month - 1]

private fun yy(year: Int) = (year % 100).toString().padStart(2, '0')
