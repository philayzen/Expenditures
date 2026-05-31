package com.example.expenditure.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.round

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
internal fun RowScope.HeaderCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = align,
    )
}

@Composable
internal fun RowScope.BodyCell(text: String, weight: Float, align: TextAlign = TextAlign.Start) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = align,
    )
}

/**
 * A name cell that the user can click to open the rename popup.
 * Rendered in the primary colour so it reads as an interactive element.
 */
@Composable
internal fun RowScope.ClickableBodyCell(text: String, weight: Float, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier.weight(weight).clickable(onClick = onClick),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * An in-table text editor for the category column while the table is in edit mode.
 * Uses [BasicTextField] with a slim primary-coloured underline so it sits flush in the row
 * without the height overhead of [androidx.compose.material3.OutlinedTextField].
 */
@Composable
internal fun RowScope.EditCategoryCell(value: String, onValueChange: (String) -> Unit, weight: Float) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.weight(weight).padding(vertical = 4.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Column {
                inner()
                HorizontalDivider(color = MaterialTheme.colorScheme.primary, thickness = 1.dp)
            }
        },
    )
}

/** Two-decimal money format without platform String.format (commonMain-safe). */
internal fun money(value: Double): String {
    val cents = round(value * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = abs(cents)
    val whole = absCents / 100
    val frac = (absCents % 100).toString().padStart(2, '0')
    return "$sign$whole.$frac"
}
