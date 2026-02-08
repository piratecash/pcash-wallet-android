package io.horizontalsystems.chartview.chart

import io.horizontalsystems.chartview.models.ChartIndicator
import io.horizontalsystems.chartview.models.ChartPoint
import java.math.BigDecimal

data class ChartPointsWrapper(
    val items: List<ChartPoint>,
    val currentPrice: BigDecimal? = null,
    val isMovementChart: Boolean = true,
    val indicators: Map<String, ChartIndicator> = mapOf(),
)
