package com.example.expenditure.importer

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReweReceiptParserTest {

    private val simpleReceipt =
        "TSE-Start: 01.01.2024 10:00:00\n" +
            "EUR\n" +
            "Milch 1,50 B\n" +
            "Brot 2,00 B\n" +
            "------\n"

    private val quantityReceipt =
        "TSE-Start: 05.03.2024 09:30:00\n" +
            "EUR\n" +
            "Eier 3,49 B\n" +
            "6 Stk x 0,58\n" +
            "------\n"

    private val discountReceipt =
        "TSE-Start: 01.01.2024 10:00:00\n" +
            "EUR\n" +
            "Joghurt 0,89 B\n" +
            "Rabatt -0,10 B\n" +
            "------\n"

    @Test
    fun parsesSimpleItems() {
        val result = ReweReceiptParser.parse(simpleReceipt)!!
        assertEquals("01.01.2024 10:00:00", result.date)
        assertEquals(2, result.items.size)
        assertItem(result.items[0], "Milch", 1, 1.50)
        assertItem(result.items[1], "Brot", 1, 2.00)
    }

    @Test
    fun quantityLineUpdatesAmountAndPrice() {
        val result = ReweReceiptParser.parse(quantityReceipt)!!
        assertEquals("05.03.2024 09:30:00", result.date)
        assertEquals(1, result.items.size)
        assertItem(result.items[0], "Eier", 6, 0.58)
    }

    @Test
    fun negativePriceSubtractedFromPreviousItem() {
        val result = ReweReceiptParser.parse(discountReceipt)!!
        assertEquals("01.01.2024 10:00:00", result.date)
        assertEquals(1, result.items.size)
        assertItem(result.items[0], "Joghurt", 1, 0.79)
    }

    @Test
    fun returnsNullWhenNoTseStartLine() {
        assertNull(ReweReceiptParser.parse("EUR\nMilch 1,50 B\n------\n"))
    }

    @Test
    fun returnsNullWhenNoEndLine() {
        assertNull(ReweReceiptParser.parse("TSE-Start: 01.01.2024 10:00:00\nEUR\nMilch 1,50 B\n"))
    }

    @Test
    fun parsesReceiptDateFromGermanFormat() {
        assertEquals(LocalDate(2024, 1, 1).atTime(0, 0), parseReceiptDateTime("01.01.2024"))
    }

    @Test
    fun parsesReceiptDateTimeFromGermanFormatWithClock() {
        assertEquals(LocalDate(2024, 1, 1).atTime(10, 0, 0), parseReceiptDateTime("01.01.2024 10:00:00"))
    }

    @Test
    fun parsesReceiptDateFromIsoTimestamp() {
        assertEquals(LocalDateTime.parse("2026-02-02T14:29:13.000"), parseReceiptDateTime("2026-02-02T14:29:13.000"))
    }

    private fun assertItem(item: com.example.expenditure.model.Item, name: String, amount: Int, price: Double) {
        assertEquals(name, item.name)
        assertEquals(amount, item.amount)
        assertEquals(price, item.price, 1e-9)
    }
}
