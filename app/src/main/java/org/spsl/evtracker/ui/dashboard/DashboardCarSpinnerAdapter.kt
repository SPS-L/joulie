// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.ui.dashboard

import android.content.Context
import android.widget.ArrayAdapter
import org.spsl.evtracker.R
import org.spsl.evtracker.data.local.entity.CarEntity

/**
 * Spinner adapter that lists cars by name and appends a "Manage cars…" tail item.
 * The tail item's index is `cars.size`. Selecting it should be intercepted by the
 * Fragment, which calls viewModel.onManageCarsClick() and resets the spinner to the
 * previously-selected car.
 */
class DashboardCarSpinnerAdapter(
    context: Context,
    cars: List<CarEntity>,
) : ArrayAdapter<String>(
    context,
    android.R.layout.simple_spinner_dropdown_item,
    buildLabels(context, cars),
) {
    val tailIndex: Int = cars.size

    companion object {
        private fun buildLabels(context: Context, cars: List<CarEntity>): List<String> =
            cars.map { it.name } + context.getString(R.string.manage_cars)
    }
}
