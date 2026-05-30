package com.example.expenditure.importer

import com.example.expenditure.model.BankDbEntry
import kotlinx.datetime.LocalDate

/** Per-bank CSV layout. Mirrors the column/skip arguments threaded through `data_import.read_csv`. */
data class BankCsvConfig(
    val source: String,
    val betragColumn: String,
    val recipientColumn: String,
    val dateColumn: String,
    val purposeColumn: String,
    /** Rows to drop before the header row (DKB=4, ING=11, VB=0). */
    val skipBeforeHeader: Int,
)

/**
 * Pure port of `data_import.read_csv` + `transform_data`. File reading and charset handling are
 * platform I/O and stay in the JVM importer; this takes already-decoded text.
 *
 * Transformations preserved: "Betrag" string -> Double (drop `.` thousands separators, `,` -> `.`),
 * `dd.MM.yyyy`/`dd.MM.yy` -> [LocalDate], and the running `key_helper` that increments for
 * consecutive rows sharing the same recipient and date. Category is left null (assigned at insert).
 */
object BankCsvParser {
    fun parse(text: String, config: BankCsvConfig): List<BankDbEntry> {
        val lines = text.split("\r\n", "\n").drop(config.skipBeforeHeader)
        if (lines.isEmpty()) return emptyList()
        val header = splitCsvLine(lines.first())
        val rows = lines.drop(1)
            .filter { it.isNotBlank() }
            .map { splitCsvLine(it) }

        val betragIdx = header.indexOf(config.betragColumn)
        val recipientIdx = header.indexOf(config.recipientColumn)
        val dateIdx = header.indexOf(config.dateColumn)
        val purposeIdx = header.indexOf(config.purposeColumn)
        require(betragIdx >= 0 && recipientIdx >= 0 && dateIdx >= 0) {
            "CSV header missing required columns for source ${config.source}"
        }

        val result = ArrayList<BankDbEntry>(rows.size)
        var prevRecipient: String? = null
        var prevDate: LocalDate? = null
        var keyHelper = 1L
        for (cols in rows) {
            val recipient = cols.getOrElse(recipientIdx) { "" }
            val date = parseGermanDate(cols[dateIdx])
            keyHelper = if (recipient == prevRecipient && date == prevDate) keyHelper + 1 else 1L
            result.add(
                BankDbEntry(
                    keyHelper = keyHelper,
                    date = date,
                    source = config.source,
                    recipient = recipient,
                    price = parseGermanAmount(cols[betragIdx]),
                    purpose = purposeIdx.takeIf { it >= 0 }?.let { cols.getOrNull(it) },
                )
            )
            prevRecipient = recipient
            prevDate = date
        }
        return result
    }
}

internal fun parseGermanAmount(raw: String): Double =
    raw.trim().replace(".", "").replace(",", ".").toDouble()

/** Accepts `dd.MM.yyyy` and `dd.MM.yy` (2-digit year: 00–68 -> 2000s, 69–99 -> 1900s, per Python `%y`). */
internal fun parseGermanDate(raw: String): LocalDate {
    val (d, m, y) = raw.trim().split(".").also { require(it.size == 3) { "Unexpected date: $raw" } }
    val year = when (y.length) {
        4 -> y.toInt()
        2 -> y.toInt().let { if (it <= 68) 2000 + it else 1900 + it }
        else -> error("Unexpected year in date: $raw")
    }
    return LocalDate(year, m.toInt(), d.toInt())
}

/** Minimal `;`-delimited CSV field splitter honoring double-quoted fields and `""` escapes. */
private fun splitCsvLine(line: String): List<String> {
    val fields = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            c == ';' && !inQuotes -> { fields.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    fields.add(sb.toString())
    return fields
}
