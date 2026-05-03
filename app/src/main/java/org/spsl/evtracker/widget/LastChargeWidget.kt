// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.spsl.evtracker.MainActivity
import org.spsl.evtracker.R
import org.spsl.evtracker.domain.widget.LastChargeWidgetSnapshot
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * TASK-12: home-screen widget showing the most recent charge for the active
 * car. The provider is invoked by:
 *  - the platform on add / configuration change (`onUpdate`);
 *  - [AndroidWidgetRefresher] sending a self-broadcast after every committed
 *    snapshot-affecting use case (`onReceive` falls through to `onUpdate`).
 *
 * Data is fetched in a fire-and-forget supervisor scope inside
 * [renderWidgets] — `goAsync()` isn't used because the read is fast and we
 * don't need to keep the broadcast pinned. If the read takes too long the
 * widget simply re-renders on the next refresh.
 */
class LastChargeWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        renderWidgets(context, appWidgetManager, appWidgetIds)
    }

    private fun renderWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            LastChargeWidgetEntryPoint::class.java,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            val snapshot = buildSnapshot(entry)
            val views = buildRemoteViews(context, snapshot)
            for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
        }
    }

    private suspend fun buildSnapshot(entry: LastChargeWidgetEntryPoint): LastChargeWidgetSnapshot {
        val activeCarId = entry.settingsReader().activeCarId.first()
        val activeCar = if (activeCarId == -1L) null else entry.carReader().getById(activeCarId)
        val events = if (activeCar == null) {
            emptyList()
        } else {
            entry.chargeEventQueries().getAllForCarSorted(activeCar.id)
        }
        val primaryMetric = entry.settingsReader().primaryMetric.first()
        return LastChargeWidgetSnapshot.compute(
            activeCar = activeCar,
            events = events,
            primaryMetric = primaryMetric,
            nowMillis = entry.nowProvider().nowMillis(),
        )
    }

    private fun buildRemoteViews(context: Context, snapshot: LastChargeWidgetSnapshot): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_last_charge)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)

        when (snapshot) {
            LastChargeWidgetSnapshot.Empty -> {
                views.setViewVisibility(R.id.widget_loaded, View.GONE)
                views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
                views.setTextViewText(
                    R.id.widget_empty,
                    context.getString(R.string.widget_empty_state),
                )
            }
            is LastChargeWidgetSnapshot.Loaded -> {
                views.setViewVisibility(R.id.widget_empty, View.GONE)
                views.setViewVisibility(R.id.widget_loaded, View.VISIBLE)
                views.setTextViewText(R.id.widget_car_name, snapshot.carName)
                views.setTextViewText(R.id.widget_relative_date, snapshot.relativeDateLabel)
                views.setTextViewText(
                    R.id.widget_kwh,
                    context.getString(
                        R.string.widget_kwh_added_format,
                        formatNumber(snapshot.kwhAdded, decimals = 1),
                    ),
                )
                if (snapshot.efficiencyValue != null && snapshot.efficiencyUnitLabel != null) {
                    views.setTextViewText(
                        R.id.widget_efficiency,
                        context.getString(
                            R.string.widget_efficiency_format,
                            formatNumber(snapshot.efficiencyValue, decimals = 1),
                            snapshot.efficiencyUnitLabel,
                        ),
                    )
                } else {
                    views.setTextViewText(
                        R.id.widget_efficiency,
                        context.getString(R.string.widget_efficiency_dash),
                    )
                }
                if (snapshot.costTotal != null && snapshot.currency != null) {
                    views.setViewVisibility(R.id.widget_cost, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_cost,
                        formatCurrency(snapshot.costTotal, snapshot.currency),
                    )
                } else {
                    views.setViewVisibility(R.id.widget_cost, View.GONE)
                }
            }
        }
        return views
    }

    private fun formatNumber(value: Double, decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)

    private fun formatCurrency(amount: Double, currency: String): String {
        val fmt = NumberFormat.getCurrencyInstance(Locale.getDefault())
        return try {
            fmt.currency = Currency.getInstance(currency)
            fmt.format(amount)
        } catch (_: IllegalArgumentException) {
            // Unknown ISO 4217 code — fall back to "<code> <amount>".
            "$currency ${formatNumber(amount, 2)}"
        }
    }

    companion object {
        /**
         * Convenience the [AndroidWidgetRefresher] uses to push a refresh
         * for every installed copy of the widget. Caller is the
         * application context; safe to call from any thread.
         */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, LastChargeWidget::class.java))
            if (ids.isEmpty()) return
            // Re-enter through the broadcast surface so the platform's normal
            // `onUpdate` lifecycle still applies (instead of calling
            // `updateAppWidget` directly, which would skip provider state).
            val intent = Intent(context, LastChargeWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }
}
