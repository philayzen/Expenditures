package com.example.expenditure.importer

import com.example.expenditure.model.Item
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/** Date is kept as the raw string extracted from the receipt (format varies by source). */
data class ParsedReceipt(val date: String, val items: List<Item>)

/**
 * Normalizes a raw receipt date to [LocalDate]. Receipts carry either `dd.MM.yyyy` (TSE-Start line)
 * or an ISO timestamp like `2026-02-02T14:29:13.000`; both are reduced to a calendar date.
 */
fun parseReceiptDate(raw: String): LocalDate {
    val value = raw.trim()
    return if (value.contains('.') && !value.contains('-')) {
        parseGermanDate(value)
    } else {
        LocalDate.parse(value.take(10))
    }
}

/**
 * Pure port of the text-parsing core of `data_import.retrieve_rewe_pdf_data`. PDF extraction and
 * file listing are platform I/O and live outside this parser; callers feed in already-extracted text.
 * Returns null when the receipt format can't be located (mirrors the Python `continue`/skip).
 */
object ReweReceiptParser {
    private val spaces = Regex(" +")
    private val tseStart = Regex(" *TSE-Start: *")
    private val eurLine = Regex("^ *EUR *")
    private val altStart = Regex("""^.*\d*,\d* [AB] *""")
    private val endLine = Regex("^ *-+ *")
    private val stkSplit = Regex(" *Stk x *")

    fun parse(text: String): ParsedReceipt? {
        // PDF text extractors keep visual indentation/trailing padding that the parser's suffix and
        // split rules don't expect; trimming per line normalizes to clean receipt lines.
        val lines = text.split("\n").map { it.trim() }

        val dateLine = lines.firstOrNull { it.contains(tseStart) } ?: return null
        val date = spaces.split(dateLine).getOrNull(1) ?: return null

        val eurIndex = lines.indexOfFirst { eurLine.containsMatchIn(it) }
        val start = if (eurIndex >= 0) {
            eurIndex + 1
        } else {
            val altIndex = lines.indexOfFirst { altStart.containsMatchIn(it) }
            if (altIndex < 0) return null
            altIndex
        }
        val end = lines.indexOfFirst { endLine.containsMatchIn(it) }
        if (end < 0) return null

        val items = mutableListOf<ItemBuilder>()
        for (line in lines.subList(start, end)) {
            if (line.endsWith(" B")) {
                val parts = spaces.split(line.dropLast(2))
                val totalPrice = parts.last().replace(",", ".").toDouble()
                if (totalPrice < 0) {
                    items.lastOrNull()?.let { it.price -= abs(totalPrice) }
                } else {
                    items.add(ItemBuilder(amount = 1, name = parts.dropLast(1).joinToString(" "), price = totalPrice))
                }
            } else if (stkSplit.containsMatchIn(line)) {
                val parts = stkSplit.split(line).map { it.trim() }
                items.lastOrNull()?.let {
                    it.amount = parts[0].toInt()
                    it.price = parts[1].replace(",", ".").toDouble()
                }
            }
        }
        return ParsedReceipt(date, items.map { it.toItem() })
    }

    private class ItemBuilder(var amount: Int, val name: String, var price: Double) {
        fun toItem() = Item(amount = amount, name = name, price = price)
    }
}
