package org.spsl.evtracker.core.model

sealed class ChartsUiState {

    object Loading : ChartsUiState()

    /** No rows in cars OR activeCarId == -1. Period chips and TabLayout are hidden. */
    object NoCar : ChartsUiState()

    /** Active car exists but the per-car charge_events stream is empty. Period chips
     *  and TabLayout are hidden; full-screen "Log charge" CTA shown. */
    object NoEvents : ChartsUiState()

    data class Loaded(
        val periodHasEvents: Boolean,
        val mixedCurrency: Boolean,
        val periodCurrency: String?,
        /** Start of the resolved period window, used by the trend tab to express the
         *  Line chart's x-axis as a day offset from this anchor. Storing raw
         *  epoch millis as a Float would alias because Float only has ~7 decimal
         *  digits of integer precision while modern timestamps need ~13. */
        val periodStartMillis: Long,
        val trend: EfficiencySeries,
        val monthlyKwh: List<MonthBucket>,
        val monthlyCost: List<MonthBucket>,
        val acDc: AcDcSplit,
        val locations: List<LocationSlice>
    ) : ChartsUiState()
}
