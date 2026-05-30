package com.example.expenditure.ui.chart

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeBucketsTest {

    // ── buildEmptyBuckets ───────────────────────────────────────────────────────
    @Test
    fun monthlyModeWhenRangeExceeds61Days() {
        val (buckets, useMonths) = buildEmptyBuckets(LocalDate(2024, 1, 1), LocalDate(2024, 3, 31))
        assertTrue(useMonths)
        assertEquals(3, buckets.size)
        assertEquals(TimeBucket(2024, 1, null, 0.0), buckets[0])
        assertEquals(TimeBucket(2024, 2, null, 0.0), buckets[1])
        assertEquals(TimeBucket(2024, 3, null, 0.0), buckets[2])
    }

    @Test
    fun dailyModeWhenRangeWithin61Days() {
        val (buckets, useMonths) = buildEmptyBuckets(LocalDate(2024, 1, 1), LocalDate(2024, 1, 10))
        assertTrue(!useMonths)
        assertEquals(10, buckets.size)
        assertEquals(TimeBucket(2024, 1, 1, 0.0), buckets.first())
        assertEquals(TimeBucket(2024, 1, 10, 0.0), buckets.last())
    }

    @Test
    fun dailyBucketsSpanMonthBoundary() {
        val (buckets, _) = buildEmptyBuckets(LocalDate(2024, 1, 30), LocalDate(2024, 2, 2))
        assertEquals(4, buckets.size)
        assertEquals(1 to 30, buckets.first().month to buckets.first().day)
        assertEquals(2 to 2, buckets.last().month to buckets.last().day)
    }

    @Test
    fun monthlyBucketsSpanYearBoundary() {
        val (buckets, _) = buildEmptyBuckets(LocalDate(2023, 12, 1), LocalDate(2024, 2, 1))
        assertEquals(3, buckets.size)
        assertEquals(2023 to 12, buckets.first().year to buckets.first().month)
        assertEquals(2024 to 2, buckets.last().year to buckets.last().month)
    }

    // ── groupBuckets labels ─────────────────────────────────────────────────────
    @Test
    fun monthlyNoGroupingLabels() {
        val grouped = groupBuckets(
            listOf(TimeBucket(2024, 1, null, 10.0), TimeBucket(2024, 2, null, 20.0)),
            useMonths = true, maxBars = 20,
        )
        assertEquals(BarPoint("Jan '24", 10.0), grouped[0])
        assertEquals(BarPoint("Feb '24", 20.0), grouped[1])
    }

    @Test
    fun monthlySameYearSpanLabel() {
        val grouped = groupBuckets(
            listOf(
                TimeBucket(2024, 1, null, 10.0),
                TimeBucket(2024, 2, null, 20.0),
                TimeBucket(2024, 3, null, 30.0),
            ),
            useMonths = true, maxBars = 1,
        )
        assertEquals(1, grouped.size)
        assertEquals("Jan–Mar '24", grouped[0].label)
        assertEquals(60.0, grouped[0].value, 1e-9)
    }

    @Test
    fun monthlyTwoYearSpanLabel() {
        val grouped = groupBuckets(
            listOf(TimeBucket(2023, 12, null, 5.0), TimeBucket(2024, 1, null, 7.0)),
            useMonths = true, maxBars = 1,
        )
        assertEquals("Dec '23–Jan '24", grouped[0].label)
        assertEquals(12.0, grouped[0].value, 1e-9)
    }

    @Test
    fun dailySameMonthRangeLabel() {
        val grouped = groupBuckets(
            listOf(
                TimeBucket(2024, 4, 10, 1.0),
                TimeBucket(2024, 4, 11, 2.0),
                TimeBucket(2024, 4, 12, 3.0),
            ),
            useMonths = false, maxBars = 1,
        )
        assertEquals("Apr 10–12", grouped[0].label)
        assertEquals(6.0, grouped[0].value, 1e-9)
    }

    @Test
    fun dailyCrossMonthRangeLabel() {
        val grouped = groupBuckets(
            listOf(TimeBucket(2024, 1, 30, 1.0), TimeBucket(2024, 2, 1, 1.0)),
            useMonths = false, maxBars = 1,
        )
        assertEquals("Jan 30–Feb 1", grouped[0].label)
    }

    @Test
    fun resultNeverExceedsMaxBars() {
        val buckets = (0 until 25).map { TimeBucket(2024 + it / 12, it % 12 + 1, null, 1.0) }
        assertTrue(groupBuckets(buckets, useMonths = true, maxBars = 20).size <= 20)
    }

    @Test
    fun valuesSummedAndRounded() {
        val grouped = groupBuckets(
            listOf(TimeBucket(2024, 1, null, 0.1), TimeBucket(2024, 2, null, 0.2)),
            useMonths = true, maxBars = 1,
        )
        assertEquals(0.3, grouped[0].value, 1e-9)
    }

    // ── barsFor (end-to-end) ─────────────────────────────────────────────────────
    @Test
    fun barsForAssignsValuesToMonthlyBuckets() {
        val bars = barsFor(
            points = listOf(
                LocalDate(2024, 1, 5) to 10.0,
                LocalDate(2024, 1, 20) to 5.0,
                LocalDate(2024, 3, 2) to 30.0,
            ),
            since = LocalDate(2024, 1, 1),
            until = LocalDate(2024, 3, 31),
        )
        assertEquals(listOf("Jan '24", "Feb '24", "Mar '24"), bars.map { it.label })
        assertEquals(listOf(15.0, 0.0, 30.0), bars.map { it.value })
    }
}
