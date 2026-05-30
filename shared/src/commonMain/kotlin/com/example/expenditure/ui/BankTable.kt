package com.example.expenditure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.expenditure.ui.chart.CategoryDoughnut
import com.example.expenditure.ui.chart.TimeBarChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankTable(viewModel: ExpenditureViewModel, modifier: Modifier = Modifier) {
    val showExtra = viewModel.showBankExtraColumns
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item { SectionTitle("Spending Over Time") }
        item { TimeBarChart(viewModel.bankBars, Modifier.padding(bottom = 16.dp)) }

        item { SectionTitle("By Category") }
        item { CategoryDoughnut(viewModel.bankSlices, Modifier.padding(bottom = 16.dp)) }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 12.dp)) {
                GroupMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewModel.bankMode == mode,
                        onClick = { viewModel.bankMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, GroupMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
        }

        item {
            OutlinedTextField(
                value = viewModel.bankQuery,
                onValueChange = { viewModel.bankQuery = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        item {
            BankHeaderRow(showExtra)
            HorizontalDivider()
        }

        items(viewModel.bankRows) { row ->
            BankRow(row, showExtra)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BankHeaderRow(showExtra: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showExtra) {
            HeaderCell("Date", DateWeight)
            HeaderCell("Bank", SourceWeight)
        }
        HeaderCell("Recipient", NameWeight)
        HeaderCell("Price", PriceWeight, TextAlign.End)
        HeaderCell("Category", CategoryWeight)
    }
}

@Composable
private fun BankRow(row: BankTableRow, showExtra: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showExtra) {
            BodyCell(row.date.orEmpty(), DateWeight)
            BodyCell(row.source.orEmpty(), SourceWeight)
        }
        BodyCell(row.name, NameWeight)
        BodyCell(money(row.price), PriceWeight, TextAlign.End)
        BodyCell(row.category, CategoryWeight)
    }
}

private const val DateWeight = 1.4f
private const val SourceWeight = 1f
private const val NameWeight = 2.5f
private const val PriceWeight = 1.2f
private const val CategoryWeight = 2f
