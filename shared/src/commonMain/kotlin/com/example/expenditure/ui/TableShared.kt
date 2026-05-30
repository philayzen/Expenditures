package com.example.expenditure.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/** Two-decimal money format without platform String.format (commonMain-safe). */
internal fun money(value: Double): String {
    val cents = round(value * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = abs(cents)
    val whole = absCents / 100
    val frac = (absCents % 100).toString().padStart(2, '0')
    return "$sign$whole.$frac"
}
