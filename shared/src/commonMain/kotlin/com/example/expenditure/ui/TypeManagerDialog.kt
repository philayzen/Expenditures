package com.example.expenditure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.expenditure.model.CategoryTypeEntry
import com.example.expenditure.model.ExpenseType

/**
 * Mirrors the web app's "Manage Types" popup (`openTypeManager`/`saveTypeAssignments`): lists every
 * defined category with a dropdown of [ExpenseType] values pre-set to its current assignment. Save
 * emits only the changed `(category, type-value)` pairs and closes the popup.
 */
@Composable
internal fun TypeManagerDialog(
    categoryTypes: List<CategoryTypeEntry>,
    onDismiss: () -> Unit,
    onSave: (edits: List<Pair<String, String>>) -> Unit,
) {
    // Local editable copy keyed by category; reset whenever the source list changes.
    val edited = remember(categoryTypes) {
        mutableStateMapOf<String, String>().apply {
            categoryTypes.forEach { put(it.category, it.expenseType) }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(20.dp)) {
                // ── header: title + close (X) ────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Category Types",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("✕") }
                }

                HorizontalDivider(Modifier.padding(vertical = 8.dp))

                // ── column headings ──────────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Type",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                }

                // ── one row per category ─────────────────────────────────────────
                LazyColumn(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(categoryTypes, key = { it.category }) { ct ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = ct.category,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TypeDropdown(
                                selected = edited[ct.category] ?: ct.expenseType,
                                onSelect = { edited[ct.category] = it },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── save (bottom-right) ──────────────────────────────────────────
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = {
                        val changes = categoryTypes.mapNotNull { ct ->
                            val now = edited[ct.category]
                            if (now != null && now != ct.expenseType) ct.category to now else null
                        }
                        onSave(changes)
                    }) { Text("Save") }
                }
            }
        }
    }
}

/** Read-only [ExposedDropdownMenuBox] over the [ExpenseType] value strings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ExpenseType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.value) },
                    onClick = {
                        onSelect(type.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
