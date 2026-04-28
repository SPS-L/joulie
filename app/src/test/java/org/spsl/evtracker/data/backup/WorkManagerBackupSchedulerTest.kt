@file:Suppress("RestrictedApi")  // accessing WorkRequest.workSpec is the standard way to unit-test request shape

package org.spsl.evtracker.data.backup

import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.spsl.evtracker.testing.FakeSettingsReader

class WorkManagerBackupSchedulerTest {

    @Test
    fun driveDisabled_doesNotEnqueue() = runTest {
        val workManager = mock<WorkManager>()
        val reader = FakeSettingsReader(driveEnabledInit = false)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()
        verify(workManager, never()).enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>())
    }

    @Test
    fun driveEnabled_enqueuesUniqueWorkWithReplacePolicy() = runTest {
        val workManager = mock<WorkManager>()
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()
        verify(workManager).enqueueUniqueWork(
            eq(WorkManagerBackupScheduler.UNIQUE_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            any<OneTimeWorkRequest>()
        )
    }

    @Test
    fun rapidCalls_eachUseReplacePolicy_soWorkManagerCollapses() = runTest {
        // The scheduler's contribution to debouncing is using ExistingWorkPolicy.REPLACE on every
        // call. WorkManager itself does the collapsing at runtime; here we assert the scheduler
        // hands REPLACE every time, which is the precondition that makes collapsing possible.
        val workManager = mock<WorkManager>()
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        repeat(5) { sched.enqueueBackup() }
        verify(workManager, times(5)).enqueueUniqueWork(
            eq(WorkManagerBackupScheduler.UNIQUE_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            any<OneTimeWorkRequest>()
        )
    }

    @Test
    fun enqueuedRequest_hasExpectedShape() = runTest {
        // One assertion per request property: CONNECTED constraint, exponential 30 s backoff,
        // 5 s initial delay. Captured via mockito's argumentCaptor + WorkRequest.workSpec.
        val workManager = mock<WorkManager>()
        val reader = FakeSettingsReader(driveEnabledInit = true)
        val sched = WorkManagerBackupScheduler(workManager, reader)
        sched.enqueueBackup()

        val captor = argumentCaptor<OneTimeWorkRequest>()
        verify(workManager).enqueueUniqueWork(
            eq(WorkManagerBackupScheduler.UNIQUE_NAME),
            eq(ExistingWorkPolicy.REPLACE),
            captor.capture()
        )
        val spec = captor.firstValue.workSpec
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, spec.backoffPolicy)
        assertEquals(TimeUnit.SECONDS.toMillis(WorkManagerBackupScheduler.BACKOFF_SECONDS), spec.backoffDelayDuration)
        assertEquals(TimeUnit.SECONDS.toMillis(WorkManagerBackupScheduler.INITIAL_DELAY_SECONDS), spec.initialDelay)
    }
}
