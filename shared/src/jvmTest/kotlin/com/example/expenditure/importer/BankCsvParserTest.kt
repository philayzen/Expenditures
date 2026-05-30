package com.example.expenditure.importer

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BankCsvParserTest {

    private val config = BankCsvConfig(
        source = "DKB", betragColumn = "Betrag (€)", recipientColumn = "Zahlungsempfänger*in",
        dateColumn = "Buchungsdatum", purposeColumn = "Verwendungszweck", skipBeforeHeader = 4,
    )

    private fun csv(vararg dataRows: String): String {
        val preamble = (1..4).joinToString("\n") { "junk line $it" }
        val header = "Buchungsdatum;Zahlungsempfänger*in;Verwendungszweck;Betrag (€)"
        return (listOf(preamble, header) + dataRows).joinToString("\n")
    }

    @Test
    fun parsesColumnsAmountAndDate() {
        val entries = BankCsvParser.parse(csv("01.01.2024;Amazon;books;50,00"), config)
        assertEquals(1, entries.size)
        val e = entries[0]
        assertEquals(LocalDate(2024, 1, 1), e.date)
        assertEquals("DKB", e.source)
        assertEquals("Amazon", e.recipient)
        assertEquals("books", e.purpose)
        assertEquals(50.0, e.price, 1e-9)
        assertEquals(1L, e.keyHelper)
    }

    @Test
    fun parsesThousandsSeparatorAndNegativeAmount() {
        val entries = BankCsvParser.parse(
            csv("01.01.2024;Landlord;rent;-1.234,56"), config,
        )
        assertEquals(-1234.56, entries[0].price, 1e-9)
    }

    @Test
    fun keyHelperIncrementsForSameRecipientAndDate() {
        val entries = BankCsvParser.parse(
            csv(
                "01.01.2024;Amazon;books;50,00",
                "01.01.2024;Amazon;more;30,00",
                "02.01.2024;Amazon;later;10,00",
                "02.01.2024;Rewe;food;20,00",
            ),
            config,
        )
        assertEquals(listOf(1L, 2L, 1L, 1L), entries.map { it.keyHelper })
    }

    @Test
    fun honorsQuotedFieldsContainingSemicolons() {
        val entries = BankCsvParser.parse(
            csv("01.01.2024;Amazon;\"books; pens; ink\";50,00"), config,
        )
        assertEquals("books; pens; ink", entries[0].purpose)
        assertEquals(50.0, entries[0].price, 1e-9)
    }

    @Test
    fun parsesTwoDigitYear() {
        assertEquals(LocalDate(2024, 1, 1), parseGermanDate("01.01.24"))
        assertEquals(LocalDate(1999, 12, 31), parseGermanDate("31.12.99"))
    }

    @Test
    fun parsesGermanAmount() {
        assertEquals(1234.56, parseGermanAmount("1.234,56"), 1e-9)
        assertEquals(-7.0, parseGermanAmount("-7,00"), 1e-9)
    }
}
