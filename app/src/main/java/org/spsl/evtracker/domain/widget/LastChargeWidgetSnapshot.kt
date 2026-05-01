package org.spsl.evtracker.domain.widget

import org.spsl.evtracker.core.model.ChargeType
import org.spsl.evtracker.data.local.entity.CarEntity
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.service.UnitConverter
import java.util.concurrent.TimeUnit

/**
 * Pure-data snapshot the home-screen widget renders. Either
 * [Loaded] when the active car has at least one charge event, or
 * [Empty] otherwise (no active car or no events).
 *
 * Rendered values are pre-formatted into strings so the widget code
 * stays a thin RemoteViews assembler — no formatters live in the widget.
 */
sealed class LastChargeWidgetSnapshot {
    object Empty : LastChargeWidgetSnapshot()

    data class Loaded(
        val carName: String,
        /** "Today", "Yesterday", "3 days ago", "12 weeks ago", or an absolute date for far-past. */
        val relativeDateLabel: String,
        /** kWh added on the most recent event. */
        val kwhAdded: Double,
        /**
         * Efficiency value pre-converted into the user's primary metric and
         * distance unit. Null when efficiency cannot be computed (single
         * event, non-positive odometer delta, or zero kWh).
         */
        val efficiencyValue: Double?,
        /** Unit token for the efficiency label, e.g. "km/kWh", "kWh/100 km", "mi/kWh". Null when [efficiencyValue] is null. */
        val efficiencyUnitLabel: String?,
        val chargeType: ChargeType,
        val costTotal: Double?,
        val currency: String?,
    ) : LastChargeWidgetSnapshot()

    companion object {
        /**
         * Build the snapshot from raw inputs. [events] may be unsorted —
         * the helper sorts internally and uses the latest two events to
         * compute efficiency from the odometer delta (matching
         * [org.spsl.evtracker.domain.service.StatsCalculator]'s
         * delta-odometer convention from `docs/DESIGN.md §7`).
         *
         * @param primaryMetric one of `km_per_kwh` / `kwh_per_100km` / `mi_per_kwh`
         * @param distanceUnit `km` or `miles` — only affects display via the metric
         * @param nowMillis snapshot wall clock for the relative-date bucket
         */
        fun compute(
            activeCar: CarEntity?,
            events: List<ChargeEventEntity>,
            primaryMetric: String,
            nowMillis: Long,
        ): LastChargeWidgetSnapshot {
            if (activeCar == null || events.isEmpty()) return Empty
            val sorted = events.sortedBy { it.eventDate }
            val latest = sorted.last()

            val efficiencyKmPerKwh: Double? = run {
                if (sorted.size < 2 || latest.kwhAdded <= 0.0) return@run null
                val previous = sorted[sorted.lastIndex - 1]
                val dist = latest.odometerKm - previous.odometerKm
                if (dist <= 0.0) null else dist / latest.kwhAdded
            }
            val (efficiencyValue, efficiencyUnitLabel) = formatEfficiency(efficiencyKmPerKwh, primaryMetric)

            return Loaded(
                carName = activeCar.name,
                relativeDateLabel = relativeDateLabel(latest.eventDate, nowMillis),
                kwhAdded = latest.kwhAdded,
                efficiencyValue = efficiencyValue,
                efficiencyUnitLabel = efficiencyUnitLabel,
                chargeType = latest.chargeType,
                costTotal = latest.costTotal,
                currency = latest.currency,
            )
        }

        private fun formatEfficiency(kmPerKwh: Double?, primaryMetric: String): Pair<Double?, String?> {
            if (kmPerKwh == null) return null to null
            return when (primaryMetric) {
                "km_per_kwh" -> kmPerKwh to "km/kWh"
                "kwh_per_100km" -> (100.0 / kmPerKwh) to "kWh/100 km"
                "mi_per_kwh" -> UnitConverter.kmPerKwhToMiPerKwh(kmPerKwh) to "mi/kWh"
                else -> kmPerKwh to "km/kWh"
            }
        }

        /**
         * Buckets:
         * - same calendar day → "Today"
         * - exactly one day earlier → "Yesterday"
         * - 2..6 days → "N days ago"
         * - 7..27 days → "N week(s) ago"
         * - else → "MMM d, yyyy" via the platform formatter
         *
         * Uses elapsed wall-clock days, not calendar arithmetic — close
         * enough for a glance widget and avoids `Calendar` allocation
         * cost per render.
         */
        private fun relativeDateLabel(eventMillis: Long, nowMillis: Long): String {
            val deltaMs = (nowMillis - eventMillis).coerceAtLeast(0L)
            val days = TimeUnit.MILLISECONDS.toDays(deltaMs).toInt()
            return when {
                days == 0 -> "Today"
                days == 1 -> "Yesterday"
                days < 7 -> "$days days ago"
                days < 28 -> {
                    val weeks = days / 7
                    if (weeks == 1) "1 week ago" else "$weeks weeks ago"
                }
                else -> java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(eventMillis))
            }
        }
    }
}
