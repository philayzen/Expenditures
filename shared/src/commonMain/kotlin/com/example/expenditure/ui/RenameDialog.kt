package com.example.expenditure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Data held while the rename dialog is open. [displayName] pre-fills the text field; [altNames]
 * are the raw DB names (original receipient/product strings) shown for context; [purpose] is
 * the bank transaction's purpose field (individual rows only).
 */
internal data class RenameTarget(
    val displayName: String,
    val altNames: Set<String>,
    val purpose: String?,
)

/**
 * Rename dialog mirroring the web app's `rename_popup`. Shows the original DB name(s) and the
 * bank purpose (where present), lets the user enter a new display name, and calls [onSave] with
 * the trimmed value. Mirrors `submit_rename` → `update_refresh` by delegating reload to [onSave].
 */
@Composable
internal fun RenameDialog(
    target: RenameTarget,
    onDismiss: () -> Unit,
    onSave: (newName: String) -> Unit,
) {
    var newName by remember(target) { mutableStateOf(target.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Original name(s) ─────────────────────────────────────────
                Text(
                    text = if (target.altNames.size == 1) "Original name:" else "Original names:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                target.altNames.forEach { name ->
                    Text(
                        text = "  • $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Purpose (individual bank rows only) ──────────────────────
                if (!target.purpose.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Purpose:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = target.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ── New display name input ────────────────────────────────────
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New display name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(newName.trim()) },
                enabled = newName.trim().isNotEmpty(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
