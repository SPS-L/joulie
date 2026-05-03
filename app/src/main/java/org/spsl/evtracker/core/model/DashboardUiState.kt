// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.core.model

data class DashboardUiState(
    val emptyState: EmptyState? = null,
    val stats: Stats? = null,
    val showMultiCurrencyBanner: Boolean = false,
)

sealed class EmptyState {
    object NoCar : EmptyState()
    object NoEvents : EmptyState()
}

enum class ChargeTypeFilter { ALL, AC, DC }
