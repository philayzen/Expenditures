package com.example.expenditure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Arranges a tab's table + pie + bar inside one outer vertical scroll. The [tableBlock] owns its own
 * controls (toggle + search) and a height-capped inner scroll, so it caps the page height; the outer
 * scroll then carries the charts past it.
 *
 * - **Wide** (≥ [breakpoint]): table and pie sit side by side, bar spans full width beneath them.
 * - **Narrow**: table (at 90% width, leaving a strip to grab the outer scroll past the inner one),
 *   then pie, then bar — all stacked.
 *
 * [tableBlock] receives the width modifier to apply to its root (a Row weight when wide, a 0.9 width
 * fraction when narrow) plus the min/max height its inner scroll should honour. When wide, the table
 * keeps the viewport cap as a floor but stretches up to the measured pie + legend height so the two
 * side-by-side columns align; when narrow it just uses the cap as before.
 */
@Composable
internal fun ChartTableScaffold(
    tableBlock: @Composable (modifier: Modifier, minHeight: Dp, maxHeight: Dp) -> Unit,
    pieChart: @Composable () -> Unit,
    barChart: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    breakpoint: Dp = 1000.dp,
) {
    BoxWithConstraints(modifier) {
        val wide = maxWidth >= breakpoint
        val cap = rememberTableHeightCap()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            if (wide) {
                val density = LocalDensity.current
                // Measured height of the pie + legend column; the table grows to match it (but never
                // below the viewport cap). Starts at the cap so the first frame isn't a tiny table.
                var pieHeight by remember { mutableStateOf(cap) }
                val tableMax = if (pieHeight > cap) pieHeight else cap
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    tableBlock(Modifier.weight(1f), cap, tableMax)
                    Column(
                        Modifier.weight(1f).onSizeChanged {
                            pieHeight = with(density) { it.height.toDp() }
                        },
                    ) {
                        SectionTitle("By Category")
                        pieChart()
                    }
                }
            } else {
                // 90% width keeps a 10% gutter where drags reach the outer scroll, not the inner table.
                tableBlock(Modifier.fillMaxWidth(0.9f), 0.dp, cap)
                SectionTitle("By Category")
                pieChart()
            }
            SectionTitle("Spending Over Time")
            barChart()
        }
    }
}

/**
 * A height for the inner (scrollable) table as a [fraction] of the viewport, so it caps the page
 * without swallowing it whole. Falls back to [fallback] before the window size is known (first frame).
 */
@Composable
internal fun rememberTableHeightCap(fraction: Float = 0.6f, fallback: Dp = 600.dp): Dp {
    val heightPx = LocalWindowInfo.current.containerSize.height
    if (heightPx <= 0) return fallback
    return with(LocalDensity.current) { heightPx.toDp() } * fraction
}
