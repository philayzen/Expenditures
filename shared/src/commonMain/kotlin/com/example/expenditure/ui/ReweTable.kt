package com.example.expenditure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.example.expenditure.ui.TypeManagerDialog
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expenditure.model.CategoryUpdate
import com.example.expenditure.ui.chart.CategoryDoughnut
import com.example.expenditure.ui.chart.TimeBarChart

@Composable
fun ReweTable(viewModel: ExpenditureViewModel, modifier: Modifier = Modifier) {
    ChartTableScaffold(
        modifier = modifier,
        tableBlock = { blockModifier, minH, maxH -> ReweTableBlock(viewModel, blockModifier, minH, maxH) },
        pieChart = { CategoryDoughnut(viewModel.reweSlices, monthsInRange = viewModel.reweMonths) },
        barChart = { TimeBarChart(viewModel.reweBars) },
    )
}

/** Controls (toggle + search + edit button) above the height-capped, internally scrolling table. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReweTableBlock(
    viewModel: ExpenditureViewModel,
    modifier: Modifier,
    minHeight: Dp,
    maxHeight: Dp,
) {
    val showDate = viewModel.showReweDate

    // ── rename popup state ─────────────────────────────────────────────────────
    var renameTarget by remember { mutableStateOf<RenameTarget?>(null) }
    renameTarget?.let { target ->
        RenameDialog(
            target = target,
            onDismiss = { renameTarget = null },
            onSave = { newName ->
                renameTarget = null
                viewModel.rename(target.altNames, newName)
            },
        )
    }

    // ── edit-mode state ────────────────────────────────────────────────────────
    var editMode by remember { mutableStateOf(false) }
    // Rows are frozen when edit mode begins so search/mode changes don't shift indices mid-edit.
    var frozenRows by remember { mutableStateOf(emptyList<ReweTableRow>()) }
    val editedCategories = remember { mutableStateListOf<String>() }

    val displayRows = if (editMode) frozenRows else viewModel.reweRows

    val enterEditMode: () -> Unit = {
        frozenRows = viewModel.reweRows
        editedCategories.clear()
        editedCategories.addAll(frozenRows.map { it.category })
        editMode = true
    }
    val cancelEdit: () -> Unit = {
        editMode = false
        frozenRows = emptyList()
        editedCategories.clear()
    }
    val saveEdit: () -> Unit = {
        val changes = frozenRows.zip(editedCategories).mapNotNull { (row, editedCat) ->
            if (editedCat != row.category) CategoryUpdate(row.name, editedCat) else null
        }
        // Exit edit mode before reloading so the table immediately shows live data.
        editMode = false
        frozenRows = emptyList()
        editedCategories.clear()
        viewModel.saveReweCategories(changes)
    }

    Column(modifier) {
        // ── top controls row ───────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!editMode) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    GroupMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = viewModel.reweMode == mode,
                            onClick = { viewModel.reweMode = mode },
                            shape = SegmentedButtonDefaults.itemShape(index, GroupMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }

                OutlinedButton(onClick = enterEditMode) { Text("Edit Categories") }
            } else {
                Spacer(Modifier.weight(1f))
                TextButton(onClick = cancelEdit) { Text("Cancel") }
                Button(onClick = saveEdit) { Text("Save") }
            }
        }

        OutlinedTextField(
            value = viewModel.reweQuery,
            onValueChange = { viewModel.reweQuery = it },
            label = { Text("Search") },
            singleLine = true,
            enabled = !editMode,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        )

        LazyColumn(Modifier.fillMaxWidth().heightIn(min = minHeight, max = maxHeight)) {
            item {
                ReweHeaderRow(showDate)
                HorizontalDivider()
            }
            itemsIndexed(displayRows) { index, row ->
                ReweRow(
                    row = row,
                    showDate = showDate,
                    editedCategory = if (editMode) editedCategories.getOrNull(index) else null,
                    onCategoryChange = {
                        if (editMode && index < editedCategories.size) editedCategories[index] = it
                    },
                    // Rename is blocked during category-edit mode so indices stay stable.
                    onNameClick = if (!editMode) {
                        { renameTarget = RenameTarget(row.name, row.altNames, null) }
                    } else null,
                )
                HorizontalDivider()
            }
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
private fun ReweRow(
    row: ReweTableRow,
    showDate: Boolean,
    editedCategory: String? = null,
    onCategoryChange: (String) -> Unit = {},
    onNameClick: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showDate) BodyCell(row.date.orEmpty(), DateWeight)
        if (onNameClick != null) {
            ClickableBodyCell(row.name, NameWeight, onNameClick)
        } else {
            BodyCell(row.name, NameWeight)
        }
        BodyCell(row.amount.toString(), AmountWeight, TextAlign.Center)
        BodyCell(money(row.price), PriceWeight, TextAlign.End)
        if (editedCategory != null) {
            EditCategoryCell(editedCategory, onCategoryChange, CategoryWeight)
        } else {
            BodyCell(row.category, CategoryWeight)
        }
    }
}

private const val DateWeight = 1.4f
private const val NameWeight = 2.5f
private const val AmountWeight = 1f
private const val PriceWeight = 1.2f
private const val CategoryWeight = 2f
