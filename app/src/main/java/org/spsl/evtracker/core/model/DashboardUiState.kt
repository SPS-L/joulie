package org.spsl.evtracker.core.model

data class DashboardUiState(
    val emptyState: EmptyState? = null,
    val stats: Stats? = null,
    val showMultiCurrencyBanner: Boolean = false
)

sealed class EmptyState {
    object NoCar : EmptyState()
    object NoEvents : EmptyState()
}

enum class ChargeTypeFilter { ALL, AC, DC }
