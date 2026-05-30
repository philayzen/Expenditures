package com.example.expenditure.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.expenditure.ui.theme.ChartPalette
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

data class CategorySlice(val label: String, val value: Double)

private val Palette = ChartPalette

/**
 * Hand-drawn doughnut (no chart lib): one `drawArc` per slice over a ~62% cutout, with a legend.
 * Replaces the Chart.js doughnut in `fillPieChart`.
 */
@Composable
fun CategoryDoughnut(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    val positive = slices.filter { it.value > 0.0 }
    val total = positive.sumOf { it.value }

    Column(modifier) {
        if (total <= 0.0) {
            Text("No spending in range", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        Canvas(
            Modifier.fillMaxWidth().aspectRatio(1.6f).padding(8.dp),
        ) {
            val diameterBase = min(size.width, size.height)
            val thickness = diameterBase * 0.19f
            val d = diameterBase - thickness
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)

            var startAngle = -90f
            positive.forEachIndexed { index, slice ->
                val sweep = (slice.value / total * 360.0).toFloat()
                drawArc(
                    color = Palette[index % Palette.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = thickness),
                )
                startAngle += sweep
            }
        }

        Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            positive.forEachIndexed { index, slice ->
                LegendRow(Palette[index % Palette.size], slice.label, money(slice.value))
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(12.dp).background(color))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("€$value", style = MaterialTheme.typography.bodyMedium)
    }
}

private fun money(value: Double): String {
    val cents = round(value * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = abs(cents)
    return "$sign${absCents / 100}.${(absCents % 100).toString().padStart(2, '0')}"
}
