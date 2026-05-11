// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.flow.first
import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.CarbonIntensitySource
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.intensityOrNull
import org.spsl.evtracker.domain.service.CostParser
import org.spsl.evtracker.domain.widget.WidgetRefresher
import javax.inject.Inject

class SaveChargeEventUseCase @Inject constructor(
    private val chargeEventQueries: ChargeEventQueries,
    private val chargeEventWriter: ChargeEventWriter,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler,
    private val widgetRefresher: WidgetRefresher,
    private val costParser: CostParser,
    private val settingsReader: SettingsReader,
    private val carbonIntensitySource: CarbonIntensitySource,
    private val now: NowProvider,
) {
    suspend operator fun invoke(input: SaveChargeEventInput): SaveChargeEventResult {
        // 1. Validate odometer > previous event's (excluding own id when updating).
        val sorted = chargeEventQueries.getAllForCarSorted(input.carId)
        val previous = sorted.lastOrNull { it.eventDate < input.eventDate && it.id != input.eventId }
        if (previous != null && input.odometerKm <= previous.odometerKm) {
            return SaveChargeEventResult.OdometerNotIncreasing
        }

        // 2. Normalize cost.
        val (costTotal, costPerKwh) = input.costInput?.let { ci ->
            costParser.parse(ci.value, input.kwhAdded, ci.mode)
        } ?: Pair(null, null)
        val currency = if (costTotal != null) input.costInput?.currency else null

        // 3. Resolve the per-event grid intensity from the Electricity Maps
        //    live feed only. No manual fallback exists by design (issue #1
        //    follow-up): either a live value is captured at save time, or
        //    the column stays null and the dashboard / charts CO₂ surfaces
        //    stay hidden for this event. The repository caches per zone for
        //    1 h, so two saves in the same hour share one API call.
        val co2Enabled = settingsReader.co2Enabled.first()
        val gridIntensityGCo2PerKwh: Double? = if (co2Enabled) {
            val apiKey = settingsReader.electricityMapsApiKey.first()
            if (apiKey.isNotBlank()) {
                carbonIntensitySource.fetchCarbonIntensity(
                    settingsReader.electricityMapsZone.first(),
                    apiKey,
                ).intensityOrNull
            } else {
                null
            }
        } else {
            null
        }

        val nowMs = now.nowMillis()
        val entity = ChargeEventEntity(
            id = input.eventId ?: 0L,
            carId = input.carId,
            eventDate = input.eventDate,
            odometerKm = input.odometerKm,
            kwhAdded = input.kwhAdded,
            chargeType = input.chargeType,
            costTotal = costTotal,
            costPerKwh = costPerKwh,
            currency = currency,
            location = input.location?.takeIf { it.isNotBlank() },
            note = input.note,
            socBefore = input.socBefore,
            socAfter = input.socAfter,
            kwhSource = input.kwhSource,
            gridIntensityGCo2PerKwh = gridIntensityGCo2PerKwh,
            createdAt = nowMs,
        )

        // 3. Persist (insert or update by id).
        val savedId: Long = if (input.eventId == null) {
            chargeEventWriter.insert(entity)
        } else {
            chargeEventWriter.update(entity)
            input.eventId
        }

        // 4. Record location usage if non-blank.
        input.location?.takeIf { it.isNotBlank() }?.let { locationWriter.recordUsage(it, nowMs) }

        // 5. Enqueue backup + refresh the home-screen widget.
        backupScheduler.enqueueBackup()
        widgetRefresher.refresh()

        return SaveChargeEventResult.Success(savedId)
    }
}
