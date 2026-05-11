package org.spsl.evtracker.domain.notification

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.domain.repository.SettingsReader
import org.spsl.evtracker.domain.repository.SettingsWriter
import org.spsl.evtracker.testing.FakeBackupNotifier

/**
 * Custom paired SettingsReader+Writer so the reporter's internal
 * "read-then-write" sequence sees the value it just wrote in the same
 * turn. The shared `Fakes.kt` reader/writer are independent stores, which
 * doesn't fit the read-then-write pattern under test here.
 */
private class LinkedSettings(initialFailures: Int = 0) : SettingsReader, SettingsWriter {
    private val failuresFlow = MutableStateFlow(initialFailures)
    private val deniedFlow = MutableStateFlow(false)
    var consecutiveBackupFailuresWrites: Int = 0
        private set

    override val activeCarId: Flow<Long> get() = error("unused")
    override val primaryMetric: Flow<String> get() = error("unused")
    override val distanceUnit: Flow<String> get() = error("unused")
    override val currency: Flow<String> get() = error("unused")
    override val driveEnabled: Flow<Boolean> get() = error("unused")
    override val lastBackupAt: Flow<Long?> get() = error("unused")
    override val theme: Flow<String> get() = error("unused")
    override val resetInProgress: Flow<Boolean> get() = error("unused")
    override val setupComplete: Flow<Boolean> get() = error("unused")
    override val consecutiveBackupFailures: Flow<Int> = failuresFlow
    override val notificationPermissionDenied: Flow<Boolean> = deniedFlow
    override val lastSeenRemoteBackupExportedAt: Flow<String> get() = error("unused")
    override val languageTag: Flow<String> get() = error("unused")
    override val iceBaselineLPer100km: Flow<Double> get() = error("unused")
    override val co2Enabled: Flow<Boolean> get() = error("unused")
    override val electricityMapsApiKey: Flow<String> get() = error("unused")
    override val electricityMapsZone: Flow<String> get() = error("unused")
    override val electricityMapsCacheZone: Flow<String> get() = error("unused")
    override val electricityMapsCacheIntensity: Flow<Double> get() = error("unused")
    override val electricityMapsCacheFetchedAtMs: Flow<Long> get() = error("unused")
    override val evDbLastUpdatedAt: Flow<Long> get() = error("unused")
    override val evDbVersion: Flow<String> get() = error("unused")
    override val evDbVehicleCount: Flow<Int> get() = error("unused")

    override suspend fun setActiveCarId(id: Long) = error("unused")
    override suspend fun setDriveEnabled(enabled: Boolean) = error("unused")
    override suspend fun setLastBackupAt(epochMs: Long) = error("unused")
    override suspend fun setTheme(value: String) = error("unused")
    override suspend fun setPrimaryMetric(metric: String) = error("unused")
    override suspend fun setDistanceUnit(unit: String) = error("unused")
    override suspend fun setCurrency(code: String) = error("unused")
    override suspend fun setSetupComplete(value: Boolean) = error("unused")
    override suspend fun setResetInProgress(value: Boolean) = error("unused")
    override suspend fun setPrimaryMetricAndDistanceUnit(metric: String, unit: String) = error("unused")
    override suspend fun completeSetup(metric: String, unit: String, currency: String) = error("unused")
    override suspend fun markGlobalResetInProgress() = error("unused")

    override suspend fun setConsecutiveBackupFailures(value: Int) {
        consecutiveBackupFailuresWrites++
        failuresFlow.value = value
    }
    override suspend fun setNotificationPermissionDenied(value: Boolean) {
        deniedFlow.value = value
    }
    override suspend fun setLastSeenRemoteBackupExportedAt(value: String) = error("unused")
    override suspend fun setLanguageTag(value: String) = error("unused")
    override suspend fun setIceBaselineLPer100km(value: Double) = error("unused")
    override suspend fun setCo2Enabled(enabled: Boolean) = error("unused")
    override suspend fun setElectricityMapsApiKey(value: String) = error("unused")
    override suspend fun setElectricityMapsZone(value: String) = error("unused")
    override suspend fun setElectricityMapsCache(
        zone: String,
        intensityGCo2PerKwh: Double,
        fetchedAtMs: Long,
    ) = error("unused")
    override suspend fun clearElectricityMapsCache() = error("unused")
    override suspend fun setEvDbCache(
        lastUpdatedAtMs: Long,
        version: String,
        vehicleCount: Int,
    ) = error("unused")

    val currentFailures: Int get() = failuresFlow.value
}

class BackupOutcomeReporterTest {

    private fun fixture(initialFailures: Int = 0): Triple<BackupOutcomeReporter, LinkedSettings, FakeBackupNotifier> {
        val settings = LinkedSettings(initialFailures)
        val notifier = FakeBackupNotifier()
        return Triple(BackupOutcomeReporter(settings, settings, notifier), settings, notifier)
    }

    @Test
    fun success_resetsCounter_andClearsNotifications() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 2)
        sut.onResult(BackupResult.Success)
        assertEquals(0, settings.currentFailures)
        assertEquals(1, notifier.clearCount)
        assertEquals(0, notifier.chronicCount)
        assertEquals(0, notifier.authCount)
    }

    @Test
    fun firstFailure_incrementsCounter_doesNotFireChronic() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 0)
        sut.onResult(BackupResult.Failure("boom"))
        assertEquals(1, settings.currentFailures)
        assertEquals(0, notifier.chronicCount)
    }

    @Test
    fun secondFailure_stillBelowThreshold_doesNotFireChronic() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 1)
        sut.onResult(BackupResult.Failure("boom"))
        assertEquals(2, settings.currentFailures)
        assertEquals(0, notifier.chronicCount)
    }

    @Test
    fun thirdFailure_firesChronic() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 2)
        sut.onResult(BackupResult.Failure("boom"))
        assertEquals(3, settings.currentFailures)
        assertEquals(1, notifier.chronicCount)
        assertEquals(0, notifier.clearCount)
    }

    @Test
    fun fourthFailure_keepsFiringChronic() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 3)
        sut.onResult(BackupResult.Failure("boom"))
        assertEquals(4, settings.currentFailures)
        assertEquals(1, notifier.chronicCount)
    }

    @Test
    fun authRequired_firesAuthEveryTime_andIncrementsCounter() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 0)
        sut.onResult(BackupResult.AuthRequired)
        sut.onResult(BackupResult.AuthRequired)
        assertEquals(2, settings.currentFailures)
        assertEquals(2, notifier.authCount)
        assertEquals(0, notifier.chronicCount)
    }

    @Test
    fun successAfterStreak_resetsCounter_andClearsBothChannels() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 5)
        sut.onResult(BackupResult.Success)
        assertEquals(0, settings.currentFailures)
        assertEquals(1, notifier.clearCount)
    }

    @Test
    fun authRequired_doesNotFireChronicEvenAtThreshold() = runTest {
        val (sut, settings, notifier) = fixture(initialFailures = 2)
        sut.onResult(BackupResult.AuthRequired)
        assertEquals(3, settings.currentFailures)
        assertEquals(0, notifier.chronicCount)
        assertEquals(1, notifier.authCount)
    }
}
