package org.spsl.evtracker.core.model

data class EfficiencySeries(
    val acPoints: List<EfficiencyPoint> = emptyList(),
    val dcPoints: List<EfficiencyPoint> = emptyList()
)
