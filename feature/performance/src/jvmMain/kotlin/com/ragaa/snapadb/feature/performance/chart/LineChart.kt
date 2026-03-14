package com.ragaa.snapadb.feature.performance.chart

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp

@Composable
fun LineChart(
    series: List<ChartSeries>,
    config: ChartConfig,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // Pre-measure Y-axis grid labels outside Canvas
    val yAxisLabels: List<TextLayoutResult> = remember(config) {
        (0..config.gridLineCount).map { i ->
            val fraction = i.toFloat() / config.gridLineCount
            val value = config.minY + fraction * (config.maxY - config.minY)
            val text = "%.0f%s".format(value, config.yAxisLabel)
            textMeasurer.measure(text, labelStyle)
        }
    }

    // Derive X-axis labels from actual data time range
    val xAxisLabels: List<TextLayoutResult> = remember(series, config) {
        val allTimestamps = series.flatMap { s -> s.points.map { it.timestampMs } }
        val minTs = allTimestamps.minOrNull() ?: 0L
        val maxTs = allTimestamps.maxOrNull() ?: 0L
        val rangeSeconds = ((maxTs - minTs) / 1000L).coerceAtLeast(1L)

        val labels = listOf(
            "-${rangeSeconds}s",
            "-${rangeSeconds / 2}s",
            "now",
        )
        labels.map { textMeasurer.measure(it, labelStyle) }
    }

    val leftPadding = (yAxisLabels.maxOfOrNull { it.size.width } ?: 0).toFloat() + 8f
    val bottomPadding = (xAxisLabels.firstOrNull()?.size?.height ?: yAxisLabels.firstOrNull()?.size?.height ?: 0).toFloat() + 8f

    Box(modifier = modifier.padding(top = 4.dp, end = 8.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val chartLeft = leftPadding
            val chartRight = size.width
            val chartTop = 0f
            val chartBottom = size.height - bottomPadding
            val chartWidth = chartRight - chartLeft
            val chartHeight = chartBottom - chartTop

            if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

            // Draw grid lines and Y-axis labels
            yAxisLabels.forEachIndexed { i, measured ->
                val fraction = i.toFloat() / config.gridLineCount
                val y = chartBottom - fraction * chartHeight

                drawLine(
                    color = gridColor,
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1f,
                )

                drawText(
                    textLayoutResult = measured,
                    color = labelColor,
                    topLeft = Offset(
                        chartLeft - measured.size.width - 4f,
                        y - measured.size.height / 2f,
                    ),
                )
            }

            // Draw X-axis labels
            xAxisLabels.forEachIndexed { index, measured ->
                val fraction = index.toFloat() / (xAxisLabels.size - 1)
                val x = chartLeft + fraction * chartWidth
                drawText(
                    textLayoutResult = measured,
                    color = labelColor,
                    topLeft = Offset(
                        x - measured.size.width / 2f,
                        chartBottom + 4f,
                    ),
                )
            }

            // Draw each series
            series.forEach { s ->
                drawSeries(s, config, chartLeft, chartTop, chartWidth, chartHeight, chartBottom)
            }
        }
    }
}

private fun DrawScope.drawSeries(
    series: ChartSeries,
    config: ChartConfig,
    chartLeft: Float,
    chartTop: Float,
    chartWidth: Float,
    chartHeight: Float,
    chartBottom: Float,
) {
    val points = series.points
    if (points.size < 2) return

    val firstTs = points.first().timestampMs
    val lastTs = points.last().timestampMs
    val timeRange = (lastTs - firstTs).coerceAtLeast(1L)
    val yRange = (config.maxY - config.minY).coerceAtLeast(0.001f)

    val linePath = Path()
    val fillPath = Path()

    points.forEachIndexed { index, point ->
        val xFraction = (point.timestampMs - firstTs).toFloat() / timeRange
        val yFraction = ((point.value - config.minY) / yRange).coerceIn(0f, 1f)
        val x = chartLeft + xFraction * chartWidth
        val y = chartBottom - yFraction * chartHeight

        if (index == 0) {
            linePath.moveTo(x, y)
            fillPath.moveTo(x, chartBottom)
            fillPath.lineTo(x, y)
        } else {
            linePath.lineTo(x, y)
            fillPath.lineTo(x, y)
        }
    }

    // Close fill path
    val lastPoint = points.last()
    val lastX = chartLeft + ((lastPoint.timestampMs - firstTs).toFloat() / timeRange) * chartWidth
    fillPath.lineTo(lastX, chartBottom)
    fillPath.close()

    // Draw fill
    drawPath(
        path = fillPath,
        color = series.color.copy(alpha = 0.15f),
    )

    // Draw line
    drawPath(
        path = linePath,
        color = series.color,
        style = Stroke(width = 2f),
    )
}
