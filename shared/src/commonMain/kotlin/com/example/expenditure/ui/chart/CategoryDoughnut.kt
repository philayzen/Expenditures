package com.example.expenditure.ui.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.example.expenditure.ui.theme.ChartPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class CategorySlice(val label: String, val value: Double)

private val Palette = ChartPalette
private const val RING_FRACTION = 0.19f

/**
 * Hand-drawn doughnut (no chart lib): one `drawArc` per slice over a ~62% cutout, with a wrapped
 * legend grid. Replaces the Chart.js doughnut in `fillPieChart`. Hovering a slice (desktop) raises a
 * tooltip with the category total and its per-month average (total ÷ [monthsInRange]).
 */
@Composable
fun CategoryDoughnut(slices: List<CategorySlice>, modifier: Modifier = Modifier, monthsInRange: Int = 1) {
    val positive = slices.filter { it.value > 0.0 }
    val total = positive.sumOf { it.value }

    Column(modifier) {
        if (total <= 0.0) {
            Text("No spending in range", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        var hovered by remember { mutableStateOf<Int?>(null) }
        var hoverPos by remember { mutableStateOf(Offset.Zero) }

        Box(Modifier.fillMaxWidth().aspectRatio(1.6f).padding(8.dp)) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.6f).pointerInput(positive, total) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            when (event.type) {
                                PointerEventType.Move, PointerEventType.Enter -> {
                                    val pos = event.changes.last().position
                                    val idx = sliceIndexAt(pos, size.toSize(), positive, total)
                                    hovered = idx
                                    if (idx != null) hoverPos = pos
                                }
                                PointerEventType.Exit -> hovered = null
                            }
                        }
                    }
                },
            ) {
                val diameterBase = min(size.width, size.height)
                val thickness = diameterBase * RING_FRACTION
                val d = diameterBase - thickness
                val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                val arcSize = Size(d, d)

                var startAngle = -90f
                positive.forEachIndexed { index, slice ->
                    val sweep = (slice.value / total * 360.0).toFloat()
                    val isHovered = index == hovered
                    drawArc(
                        color = Palette[index % Palette.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = if (isHovered) thickness * 1.18f else thickness),
                    )
                    startAngle += sweep
                }
            }

            hovered?.let { index ->
                val slice = positive[index]
                val perMonth = slice.value / monthsInRange.coerceAtLeast(1)
                Surface(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 4.dp,
                    modifier = Modifier.offset {
                        IntOffset((hoverPos.x + 12f).roundToInt(), (hoverPos.y + 12f).roundToInt())
                    },
                ) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(slice.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("Total: €${money(slice.value)}", style = MaterialTheme.typography.bodySmall)
                        Text("Ø / month: €${money(perMonth)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        LegendGrid(positive, Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

/**
 * Responsive legend: packs as many `swatch · label · value` cells per row as the available width
 * allows (each cell ≥ [MinCellWidth]), so wide panes show 2–4 across while a phone stacks to one.
 */
@Composable
private fun LegendGrid(slices: List<CategorySlice>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val columns = (maxWidth / MinCellWidth).toInt().coerceAtLeast(1)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            slices.chunked(columns).forEachIndexed { rowIndex, rowSlices ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowSlices.forEachIndexed { colIndex, slice ->
                        val globalIndex = rowIndex * columns + colIndex
                        LegendItem(
                            color = Palette[globalIndex % Palette.size],
                            label = slice.label,
                            value = money(slice.value),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Pad the final row so cells stay column-aligned with the rows above.
                    repeat(columns - rowSlices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).background(color))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text("€$value", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

private val MinCellWidth = 200.dp

/**
 * Maps a pointer position to the slice under it, or null when the cursor is off the ring. Mirrors the
 * draw geometry: angles measured clockwise from 12 o'clock (`drawArc` starts each slice at -90°).
 */
private fun sliceIndexAt(pos: Offset, canvas: Size, slices: List<CategorySlice>, total: Double): Int? {
    val diameterBase = min(canvas.width, canvas.height)
    if (diameterBase <= 0f || total <= 0.0) return null
    val thickness = diameterBase * RING_FRACTION
    val ringRadius = (diameterBase - thickness) / 2f

    val dx = pos.x - canvas.width / 2f
    val dy = pos.y - canvas.height / 2f
    val dist = sqrt(dx * dx + dy * dy)
    if (dist < ringRadius - thickness / 2f || dist > ringRadius + thickness / 2f) return null

    var angle = atan2(dy, dx) * 180f / PI.toFloat()
    if (angle < -90f) angle += 360f

    var start = -90f
    slices.forEachIndexed { index, slice ->
        val sweep = (slice.value / total * 360.0).toFloat()
        if (angle >= start && angle < start + sweep) return index
        start += sweep
    }
    return null
}

private fun money(value: Double): String {
    val cents = round(value * 100).toLong()
    val sign = if (cents < 0) "-" else ""
    val absCents = abs(cents)
    return "$sign${absCents / 100}.${(absCents % 100).toString().padStart(2, '0')}"
}
