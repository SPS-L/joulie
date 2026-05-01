package org.spsl.evtracker.domain.usecase

import org.spsl.evtracker.core.model.SaveChargeEventInput
import org.spsl.evtracker.core.model.SaveChargeEventResult
import org.spsl.evtracker.data.local.entity.ChargeEventEntity
import org.spsl.evtracker.domain.backup.BackupScheduler
import org.spsl.evtracker.domain.repository.ChargeEventQueries
import org.spsl.evtracker.domain.repository.ChargeEventWriter
import org.spsl.evtracker.domain.repository.LocationWriter
import org.spsl.evtracker.domain.service.CostParser
import javax.inject.Inject

class SaveChargeEventUseCase @Inject constructor(
    private val chargeEventQueries: ChargeEventQueries,
    private val chargeEventWriter: ChargeEventWriter,
    private val locationWriter: LocationWriter,
    private val backupScheduler: BackupScheduler,
    private val costParser: CostParser,
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

        // 5. Enqueue backup.
        backupScheduler.enqueueBackup()

        return SaveChargeEventResult.Success(savedId)
    }
}
