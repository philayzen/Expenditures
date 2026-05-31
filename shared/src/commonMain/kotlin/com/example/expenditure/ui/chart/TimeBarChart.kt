package com.example.expenditure.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.expenditure.ui.money
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.multiplatform.cartesian.CartesianMeasuringContext
import com.patrykandpatrick.vico.multiplatform.cartesian.VicoZoomState
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.Axis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.multiplatform.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.multiplatform.cartesian.data.columnSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.data.lineSeries
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.multiplatform.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.multiplatform.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.multiplatform.common.Fill
import com.patrykandpatrick.vico.multiplatform.common.component.rememberLineComponent
import kotlin.math.round

/**
 * "Spending Over Time" bar chart. Vico column layer over the pre-bucketed [BarPoint]s from
 * [barsFor]; the bottom axis maps each column's x-index back to its bucket label.
 */
@Composable
fun TimeBarChart(bars: List<BarPoint>, modifier: Modifier = Modifier) {
    if (bars.isEmpty()) {
        Text("No spending in range", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    androidx.compose.runtime.LaunchedEffect(bars) {
        modelProducer.runTransaction {
            columnSeries { series(bars.map { it.value }) }
        }
    }

    val bottomAxisFormatter = remember(bars) {
        object : CartesianValueFormatter {
            override fun format(
                context: CartesianMeasuringContext,
                value: Double,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence {
                // Never return "" — Vico crashes on empty formatter output (CartesianValueFormatter.kt:114).
                // A space is invisible but valid; it only appears in the one-frame race between the
                // async modelProducer.runTransaction and the sync recomposition of this formatter.
                return bars.getOrNull(value.toInt())?.label?.ifEmpty { " " } ?: " "
            }
        }
    }

    val column = rememberLineComponent(fill = Fill(MaterialTheme.colorScheme.primary), thickness = 12.dp)
    CartesianChartHost(
        chart = rememberCartesianChart(
            rememberColumnCartesianLayer(
                columnProvider = ColumnCartesianLayer.ColumnProvider.series(column),
            ),
            startAxis = VerticalAxis.rememberStart(),
            bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
        ),
        modelProducer = modelProducer,
        modifier = modifier.fillMaxWidth().height(220.dp),
        zoomState = rememberVicoZoomState(zoomEnabled = false),
    )
}

/**
 * Fixed segment palette ported from `palette` in `js.js`. Indexed by the position-sorted order of the
 * expense types present, so a given type keeps the same colour across reloads.
 */
private val BarPalette = listOf(
    Color(0xFF3D5A3E), Color(0xFF5A7F5B), Color(0xFFC9B99A), Color(0xFF1E2420),
    Color(0xFF8AAB8B), Color(0xFFA07850), Color(0xFFE2D5C0), Color(0xFF2A3E2B),
)

/** Dashed average-line colour, ported from `CHART.avgLine` in `js.js`. */
private val AvgLineColor = Color(0xFFA07850)

/** Web parity (`buildBarChart`): the average overlay only appears once there are more than 5 bars. */
private const val AVG_LINE_MIN_BARS = 5

/**
 * Stacked "Spending Over Time" bar chart: one column layer per expense type, merged with
 * [ColumnCartesianLayer.MergeMode.Stacked]. Series order and colours follow [StackedBarData.series]
 * (already position-sorted). A swatch legend above the chart names each segment.
 */
@Composable
fun StackedTimeBarChart(data: StackedBarData, modifier: Modifier = Modifier) {
    if (data.isEmpty) {
        Text("No spending in range", style = MaterialTheme.typography.bodyMedium, modifier = modifier)
        return
    }

    // Per-bar totals across all stacked segments → the flat average the dashed overlay draws.
    val showAvg = data.labels.size > AVG_LINE_MIN_BARS
    val avg = remember(data) {
        if (!showAvg) 0.0 else {
            val totals = data.labels.indices.map { bar ->
                data.series.sumOf { it.values.getOrElse(bar) { 0.0 } }
            }
            round(totals.average() * 100) / 100
        }
    }

    val modelProducer = remember { CartesianChartModelProducer() }
    LaunchedEffect(data) {
        modelProducer.runTransaction {
            columnSeries { data.series.forEach { s -> series(s.values) } }
            if (showAvg) lineSeries { series(List(data.labels.size) { avg }) }
        }
    }

    val bottomAxisFormatter = remember(data) {
        object : CartesianValueFormatter {
            override fun format(
                context: CartesianMeasuringContext,
                value: Double,
                verticalAxisPosition: Axis.Position.Vertical?
            ): CharSequence {
                // Never return "" — Vico crashes on empty formatter output (see TimeBarChart above).
                return data.labels.getOrNull(value.toInt())?.ifEmpty { " " } ?: " "
            }
        }
    }

    val columns = data.series.mapIndexed { i, _ ->
        rememberLineComponent(fill = Fill(BarPalette[i % BarPalette.size]), thickness = 12.dp)
    }
    val columnLayer = rememberColumnCartesianLayer(
        columnProvider = ColumnCartesianLayer.ColumnProvider.series(columns),
        mergeMode = { ColumnCartesianLayer.MergeMode.Stacked },
    )
    val avgLayer = if (showAvg) {
        rememberLineCartesianLayer(
            lineProvider = LineCartesianLayer.LineProvider.series(
                LineCartesianLayer.rememberLine(
                    fill = LineCartesianLayer.LineFill.single(Fill(AvgLineColor)),
                    stroke = LineCartesianLayer.LineStroke.Dashed(
                        thickness = 2.dp,
                        dashLength = 6.dp,
                        gapLength = 4.dp,
                    ),
                ),
            ),
        )
    } else null

    Column(modifier.fillMaxWidth()) {
        StackedBarLegend(data.series.map { it.type }, avg = if (showAvg) avg else null)
        CartesianChartHost(
            chart = rememberCartesianChart(
                *listOfNotNull(columnLayer, avgLayer).toTypedArray(),
                startAxis = VerticalAxis.rememberStart(),
                bottomAxis = HorizontalAxis.rememberBottom(valueFormatter = bottomAxisFormatter),
            ),
            modelProducer = modelProducer,
            modifier = Modifier.fillMaxWidth().height(220.dp),
            zoomState = rememberVicoZoomState(zoomEnabled = false),
        )
    }
}

/** Swatch + label legend mirroring the web's `bar-legend-item`; colours match [BarPalette] by index. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StackedBarLegend(types: List<String>, avg: Double? = null) {
    FlowRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        types.forEachIndexed { i, type ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BarPalette[i % BarPalette.size]),
                )
                Text(
                    text = type,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        if (avg != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Dashed swatch mirroring the average overlay line.
                Box(
                    Modifier.size(width = 16.dp, height = 2.dp)
                        .background(AvgLineColor),
                )
                Text(
                    text = "Avg ${money(avg)}",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}
