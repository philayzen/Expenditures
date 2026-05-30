package com.example.expenditure.importer

import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.model.BankDbEntry
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.nio.charset.Charset

/**
 * JVM-side ingestion: the file/PDF I/O half of `data_import.py`. Parsing lives in the pure
 * [BankCsvParser]/[ReweReceiptParser]; this reads files, looks up most-common categories, and
 * inserts via the repository. Mirrors `save_*_data` / `retrieve_rewe_pdf_data` / `insert_banking_data`.
 */
class DataImport(private val repository: ExpenditureRepository) {

    fun importReweFolder(folder: File) {
        folder.pdfFiles().forEach { file ->
            val receipt = ReweReceiptParser.parse(readPdfText(file)) ?: return@forEach
            insertReweReceipt(receipt)
        }
    }

    fun importBankFolder(folder: File, config: BankCsvConfig, charset: Charset = Charsets.UTF_8) {
        folder.csvFiles().forEach { file ->
            insertBanking(BankCsvParser.parse(file.readText(charset), config))
        }
    }

    fun importDkbFolder(folder: File) = importBankFolder(folder, DKB)
    fun importIngFolder(folder: File) = importBankFolder(folder, ING, charset = WINDOWS_1252)
    fun importVbFolder(folder: File) = importBankFolder(folder, VB)

    fun insertReweReceipt(receipt: ParsedReceipt) {
        val cats = repository.getReweMostCommonCategories(receipt.items.mapNotNull { it.name })
        val items = receipt.items.map { if (it.name in cats) it.copy(category = cats[it.name]) else it }
        repository.insertReweExpenditure(parseReceiptDateTime(receipt.date), items)
    }

    fun insertBanking(entries: List<BankDbEntry>) {
        val cats = repository.getBankMostCommonCategories(entries.map { it.recipient })
        val withCats = entries.map { if (it.recipient in cats) it.withCategory(cats[it.recipient]) else it }
        repository.insertGeneralExpenditure(withCats)
    }

    fun readPdfText(file: File): String =
        Loader.loadPDF(file).use { doc -> PDFTextStripper().getText(doc) }

    companion object {
        val WINDOWS_1252: Charset = Charset.forName("windows-1252")

        val DKB = BankCsvConfig(
            source = "DKB", betragColumn = "Betrag (€)", recipientColumn = "Zahlungsempfänger*in",
            dateColumn = "Buchungsdatum", purposeColumn = "Verwendungszweck", skipBeforeHeader = 4,
        )
        val ING = BankCsvConfig(
            source = "ING", betragColumn = "Betrag", recipientColumn = "Auftraggeber/Empfänger",
            dateColumn = "Buchung", purposeColumn = "Verwendungszweck", skipBeforeHeader = 11,
        )
        val VB = BankCsvConfig(
            source = "VB", betragColumn = "Betrag", recipientColumn = "Name Zahlungsbeteiligter",
            dateColumn = "Buchungstag", purposeColumn = "Verwendungszweck", skipBeforeHeader = 0,
        )
    }
}

private fun File.pdfFiles(): List<File> =
    listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }.orEmpty().sortedBy { it.name }

private fun File.csvFiles(): List<File> =
    listFiles { f -> f.isFile && f.extension.equals("csv", ignoreCase = true) }.orEmpty().sortedBy { it.name }
