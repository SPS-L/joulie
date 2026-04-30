package org.spsl.evtracker.core.model

data class AcDcSplit(
    val acCount: Int = 0,
    val dcCount: Int = 0,
    val acKwh: Double = 0.0,
    val dcKwh: Double = 0.0,
)
