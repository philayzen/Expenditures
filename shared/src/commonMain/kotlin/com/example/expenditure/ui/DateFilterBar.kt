package com.example.expenditure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Collapsible Since/Until date filter with quick-range presets, ported from the web app's `.date-row`.
 * Applies to every tab, so it lives above the tab bar. Collapsed by default to save space (mobile);
 * the header doubles as a toggle and shows the active range at a glance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DateFilterBar(viewModel: ExpenditureViewModel, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }

    Surface(modifier = modifier, tonalElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Time filter · ${rangeSummary(viewModel.sinceText, viewModel.untilText)}")
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide ▴" else "Show ▾")
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = viewModel.sinceText,
                            onValueChange = { viewModel.sinceText = it },
                            label = { Text("Since") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = viewModel.untilText,
                            onValueChange = { viewModel.untilText = it },
                            label = { Text("Until") },
                            placeholder = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        DateRangePreset.entries.forEach { preset ->
                            OutlinedButton(onClick = { viewModel.applyPreset(preset) }) {
                                Text(preset.label)
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(onClick = { viewModel.applyDateRange() }, modifier = Modifier.weight(1f)) {
                            Text("Fetch Expenditures")
                        }
                        TextButton(onClick = {
                            viewModel.sinceText = ""
                            viewModel.untilText = ""
                            viewModel.applyDateRange()
                        }) { Text("Clear") }
                    }
                }
            }
        }
    }
}

/** Human-readable summary of the active bounds for the collapsed header. */
private fun rangeSummary(since: String, until: String): String {
    val s = since.trim()
    val u = until.trim()
    return when {
        s.isEmpty() && u.isEmpty() -> "All time"
        s.isEmpty() -> "Until $u"
        u.isEmpty() -> "From $s"
        else -> "$s → $u"
    }
}
