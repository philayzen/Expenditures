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
fun ReweTable(viewModel: ExpenditureViewModel, modifier: Modifier = Modifier) {
    val showDate = viewModel.showReweDate
    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item { SectionTitle("Spending Over Time") }
        item { TimeBarChart(viewModel.reweBars, Modifier.padding(bottom = 16.dp)) }

        item { SectionTitle("By Category") }
        item { CategoryDoughnut(viewModel.reweSlices, Modifier.padding(bottom = 16.dp)) }

        item {
            SingleChoiceSegmentedButtonRow(Modifier.padding(bottom = 12.dp)) {
                GroupMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = viewModel.reweMode == mode,
                        onClick = { viewModel.reweMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(index, GroupMode.entries.size),
                    ) { Text(mode.label) }
                }
            }
        }

        item {
            OutlinedTextField(
                value = viewModel.reweQuery,
                onValueChange = { viewModel.reweQuery = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }

        item {
            ReweHeaderRow(showDate)
            HorizontalDivider()
        }

        items(viewModel.reweRows) { row ->
            ReweRow(row, showDate)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ReweHeaderRow(showDate: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDate) HeaderCell("Date", DateWeight)
        HeaderCell("Name", NameWeight)
        HeaderCell("Amount", AmountWeight, TextAlign.Center)
        HeaderCell("Price", PriceWeight, TextAlign.End)
        HeaderCell("Category", CategoryWeight)
    }
}

@Composable
private fun ReweRow(row: ReweTableRow, showDate: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDate) BodyCell(row.date.orEmpty(), DateWeight)
        BodyCell(row.name, NameWeight)
        BodyCell(row.amount.toString(), AmountWeight, TextAlign.Center)
        BodyCell(money(row.price), PriceWeight, TextAlign.End)
        BodyCell(row.category, CategoryWeight)
    }
}

private const val DateWeight = 1.4f
private const val NameWeight = 2.5f
private const val AmountWeight = 1f
private const val PriceWeight = 1.2f
private const val CategoryWeight = 2f
