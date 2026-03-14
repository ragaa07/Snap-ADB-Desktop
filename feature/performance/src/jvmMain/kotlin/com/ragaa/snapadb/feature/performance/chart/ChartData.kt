package com.ragaa.snapadb.feature.performance.chart

import androidx.compose.ui.graphics.Color
import com.ragaa.snapadb.feature.performance.model.PerformanceDataPoint

data class ChartSeries(
    val label: String,
    val color: Color,
    val points: List<PerformanceDataPoint>,
)

data class ChartConfig(
    val minY: Float = 0f,
    val maxY: Float = 100f,
    val yAxisLabel: String = "%",
    val gridLineCount: Int = 4,
)
