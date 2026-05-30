package com.example.expenditure.analysis

import com.example.expenditure.model.BankDbEntry
import com.example.expenditure.model.ReweExpenditureOutput
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ExpenditureAnalysisTest {

    private fun date(value: String) = LocalDate.parse(value)

    private fun bank(
        keyHelper: Long = 1,
        recipient: String,
        price: Double,
        purpose: String? = null,
        category: String? = null,
        displayName: String? = null,
    ) = BankDbEntry(
        keyHelper = keyHelper, date = date("2024-01-01"), source = "Me",
        recipient = recipient, price = price, purpose = purpose,
        category = category, displayName = displayName,
    )

    private fun rewe(
        name: String,
        amount: Long,
        price: Double,
        category: String? = null,
        displayName: String? = null,
    ) = ReweExpenditureOutput(
        date = date("2024-01-01").atTime(0, 0), name = name, amount = amount,
        price = price, category = category, displayName = displayName,
    )

    // == groupGeneralByName ==

    @Test
    fun generalByNameSingleNoRef() {
        val result = groupGeneralByName(listOf(bank(recipient = "AMAZON", price = 50.0, category = "Shopping")))
        assertEquals(1, result.size)
        assertEquals("AMAZON", result[0].displayName)
        assertEquals(50.0, result[0].price, 1e-9)
        assertEquals(setOf("AMAZON"), result[0].altNames)
    }

    @Test
    fun generalByNameSameNameAccumulatesPrice() {
        val result = groupGeneralByName(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "AMAZON", price = 30.0, category = "Shopping"),
        ))
        assertEquals(1, result.size)
        assertEquals("AMAZON", result[0].displayName)
        assertEquals(80.0, result[0].price, 1e-9)
        assertEquals(setOf("AMAZON"), result[0].altNames)
    }

    @Test
    fun generalByNameDisplayNameResolvesGroupName() {
        val result = groupGeneralByName(listOf(
            bank(recipient = "AMAZON.DE", price = 50.0, category = "Shopping", displayName = "Amazon"),
        ))
        assertEquals(1, result.size)
        assertEquals("Amazon", result[0].displayName)
        assertEquals(setOf("AMAZON.DE"), result[0].altNames)
    }

    @Test
    fun generalByNameTwoOriginalsMergedViaSharedDisplayName() {
        val result = groupGeneralByName(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping", displayName = "Amazon"),
            bank(recipient = "AMAZON.DE", price = 30.0, category = "Shopping", displayName = "Amazon"),
        ))
        assertEquals(1, result.size)
        assertEquals("Amazon", result[0].displayName)
        assertEquals(80.0, result[0].price, 1e-9)
        assertEquals(setOf("AMAZON", "AMAZON.DE"), result[0].altNames)
    }

    @Test
    fun generalByNameSortedDesc() {
        val result = groupGeneralByName(listOf(
            bank(recipient = "REWE", price = 30.0, category = ""),
            bank(recipient = "AMAZON", price = 80.0, category = ""),
        ))
        assertEquals("AMAZON", result[0].displayName)
        assertEquals("REWE", result[1].displayName)
    }

    @Test
    fun generalByNameDifferentNamesStaySeparate() {
        val result = groupGeneralByName(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = ""),
            bank(recipient = "REWE", price = 30.0, category = ""),
        ))
        assertEquals(2, result.size)
    }

    // == groupGeneralByNameAndCat ==

    @Test
    fun generalByNameAndCatSameNameSameCatMerges() {
        val result = groupGeneralByNameAndCat(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "AMAZON", price = 30.0, category = "Shopping"),
        ))
        assertEquals(1, result.size)
    }

    @Test
    fun generalByNameAndCatSameNameDifferentCatStaysSeparate() {
        val result = groupGeneralByNameAndCat(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "AMAZON", price = 30.0, category = "Online"),
        ))
        assertEquals(2, result.size)
    }

    @Test
    fun generalByNameAndCatDifferentNamesSameCatStaySeparate() {
        val result = groupGeneralByNameAndCat(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "REWE", price = 30.0, category = "Shopping"),
        ))
        assertEquals(2, result.size)
    }

    @Test
    fun generalByNameAndCatAccumulatesPrice() {
        val result = groupGeneralByNameAndCat(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "AMAZON", price = 30.0, category = "Shopping"),
        ))
        assertEquals(80.0, result[0].price, 1e-9)
        assertEquals("Shopping", result[0].category)
    }

    // == groupGeneralByCategory ==

    @Test
    fun generalByCategorySameCategoryAccumulates() {
        val result = groupGeneralByCategory(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "REWE", price = 30.0, category = "Shopping"),
        ))
        assertEquals(listOf("Shopping"), result.map { it.category })
        assertEquals(80.0, result[0].price, 1e-9)
    }

    @Test
    fun generalByCategoryDifferentCategoriesSortedDesc() {
        val result = groupGeneralByCategory(listOf(
            bank(recipient = "AMAZON", price = 50.0, category = "Shopping"),
            bank(recipient = "REWE", price = 30.0, category = "Groceries"),
        ))
        assertEquals(listOf("Shopping", "Groceries"), result.map { it.category })
        assertEquals(50.0, result[0].price, 1e-9)
        assertEquals(30.0, result[1].price, 1e-9)
    }

    // == groupReweByName ==

    @Test
    fun reweByNameSingleItem() {
        val result = groupReweByName(listOf(rewe(name = "Milk", amount = 2, price = 1.5, category = "Dairy")))
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].displayName)
        assertEquals(2, result[0].amount)
        assertEquals(1.5, result[0].price, 1e-9)
    }

    @Test
    fun reweByNameSameNameAccumulatesAmountAndPrice() {
        val result = groupReweByName(listOf(
            rewe(name = "Milk", amount = 2, price = 1.5, category = "Dairy"),
            rewe(name = "Milk", amount = 1, price = 1.5, category = "Dairy"),
        ))
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].displayName)
        assertEquals(3, result[0].amount)
        assertEquals(3.0, result[0].price, 1e-9)
    }

    @Test
    fun reweByNameDisplayNameResolvesGroupName() {
        val result = groupReweByName(listOf(
            rewe(name = "BIO MILCH", amount = 1, price = 1.5, category = "Dairy", displayName = "Milk"),
        ))
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].displayName)
        assertEquals(1, result[0].amount)
        assertEquals(1.5, result[0].price, 1e-9)
    }

    @Test
    fun reweByNameTwoOriginalsShareDisplayName() {
        val result = groupReweByName(listOf(
            rewe(name = "BIO MILCH A", amount = 1, price = 1.5, category = "", displayName = "Milk"),
            rewe(name = "BIO MILCH B", amount = 2, price = 2.0, category = "", displayName = "Milk"),
        ))
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].displayName)
        assertEquals(3, result[0].amount)
        assertEquals(3.5, result[0].price, 1e-9)
        assertEquals(setOf("BIO MILCH A", "BIO MILCH B"), result[0].altNames)
    }

    @Test
    fun reweByNameSortedDesc() {
        val result = groupReweByName(listOf(
            rewe(name = "Bread", amount = 1, price = 1.5, category = ""),
            rewe(name = "Milk", amount = 1, price = 3.0, category = ""),
        ))
        assertEquals("Milk", result[0].displayName)
        assertEquals("Bread", result[1].displayName)
    }

    // == groupReweByNameAndCat ==

    @Test
    fun reweByNameAndCatSameNameSameCatMerges() {
        val result = groupReweByNameAndCat(listOf(
            rewe(name = "Milk", amount = 1, price = 1.5, category = "Dairy"),
            rewe(name = "Milk", amount = 1, price = 1.5, category = "Dairy"),
        ))
        assertEquals(1, result.size)
    }

    @Test
    fun reweByNameAndCatSameNameDifferentCatStaysSeparate() {
        val result = groupReweByNameAndCat(listOf(
            rewe(name = "Milk", amount = 1, price = 1.5, category = "Dairy"),
            rewe(name = "Milk", amount = 1, price = 1.5, category = "Snacks"),
        ))
        assertEquals(2, result.size)
    }

    @Test
    fun reweByNameAndCatAccumulates() {
        val result = groupReweByNameAndCat(listOf(
            rewe(name = "Milk", amount = 2, price = 1.5, category = "Dairy"),
            rewe(name = "Milk", amount = 3, price = 1.5, category = "Dairy"),
        ))
        assertEquals(5, result[0].amount)
        assertEquals(3.0, result[0].price, 1e-9)
    }

    // == groupReweByCategory ==

    @Test
    fun reweByCategorySameCategoryAccumulatesAmountAndPrice() {
        val result = groupReweByCategory(listOf(
            rewe(name = "Milk", amount = 2, price = 1.5, category = "Dairy"),
            rewe(name = "Yoghurt", amount = 3, price = 0.9, category = "Dairy"),
        ))
        assertEquals(listOf("Dairy"), result.map { it.category })
        assertEquals(5, result[0].amount)
        assertEquals(2.4, result[0].price, 1e-9)
    }

    @Test
    fun reweByCategoryDifferentCategoriesSortedDescByPrice() {
        val result = groupReweByCategory(listOf(
            rewe(name = "Milk", amount = 1, price = 2.0, category = "Dairy"),
            rewe(name = "Bread", amount = 1, price = 1.5, category = "Bakery"),
        ))
        assertEquals(listOf("Dairy", "Bakery"), result.map { it.category })
        assertEquals(listOf(1, 1), result.map { it.amount })
        assertEquals(2.0, result[0].price, 1e-9)
        assertEquals(1.5, result[1].price, 1e-9)
    }

    // == resolveReweIndividually ==

    @Test
    fun reweIndividuallyReturnsResolvedRows() {
        val result = resolveReweIndividually(listOf(rewe(name = "Milk", amount = 2, price = 1.5, category = "Dairy")))
        assertEquals(1, result.size)
        assertEquals("Milk", result[0].name)
        assertEquals(2, result[0].amount)
        assertEquals(1.5, result[0].price, 1e-9)
        assertEquals("Dairy", result[0].category)
        assertEquals("Milk", result[0].displayName)
    }

    @Test
    fun reweIndividuallyAppliesDisplayName() {
        val result = resolveReweIndividually(listOf(
            rewe(name = "BIO MILCH", amount = 1, price = 1.5, category = "", displayName = "Milk"),
        ))
        assertEquals("Milk", result[0].displayName)
    }

    @Test
    fun reweIndividuallyEmptyReturnsEmpty() {
        assertEquals(emptyList(), resolveReweIndividually(emptyList()))
    }

    // == resolveGeneralIndividually ==

    @Test
    fun generalIndividuallyReturnsResolvedRows() {
        val result = resolveGeneralIndividually(listOf(
            bank(recipient = "Amazon", price = 50.0, purpose = "Cheap iPhone", category = "Shopping"),
        ))
        assertEquals(1, result.size)
        assertEquals("Me", result[0].source)
        assertEquals(50.0, result[0].price, 1e-9)
        assertEquals("Amazon", result[0].displayName)
        assertEquals("Cheap iPhone", result[0].purpose)
    }

    @Test
    fun generalIndividuallyAppliesDisplayName() {
        val result = resolveGeneralIndividually(listOf(
            bank(recipient = "AMAZON.DE GMBH", price = 50.0, category = "", displayName = "Amazon"),
        ))
        assertEquals("Amazon", result[0].displayName)
    }
}
