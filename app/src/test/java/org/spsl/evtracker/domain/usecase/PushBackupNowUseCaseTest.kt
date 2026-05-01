package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.testing.FakeBackupRepository
import org.spsl.evtracker.testing.FakeNowProvider
import org.spsl.evtracker.testing.FakeSettingsWriter

class PushBackupNowUseCaseTest {

    @Test
    fun success_callsBackupOnce_andUpdatesLastBackupAt() = runTest {
        val repo = FakeBackupRepository(nextBackupResult = BackupResult.Success)
        val writer = FakeSettingsWriter()
        val now = FakeNowProvider(time = 1_700_000_000_000L)
        val sut = PushBackupNowUseCase(repo, writer, now)

        val result = sut()

        assertEquals(BackupResult.Success, result)
        assertEquals(1, repo.backupCurrentDataCount)
        assertEquals(1_700_000_000_000L, writer.lastBackupAt)
    }

    @Test
    fun authRequired_propagates_andDoesNotUpdateLastBackupAt() = runTest {
        val repo = FakeBackupRepository(nextBackupResult = BackupResult.AuthRequired)
        val writer = FakeSettingsWriter()
        val now = FakeNowProvider(time = 1_700_000_000_000L)
        val sut = PushBackupNowUseCase(repo, writer, now)

        val result = sut()

        assertEquals(BackupResult.AuthRequired, result)
        assertEquals(1, repo.backupCurrentDataCount)
        assertNull("lastBackupAt must stay untouched on AuthRequired", writer.lastBackupAt)
    }

    @Test
    fun failure_propagates_andDoesNotUpdateLastBackupAt() = runTest {
        val repo = FakeBackupRepository(
            nextBackupResult = BackupResult.Failure("Drive storage full"),
        )
        val writer = FakeSettingsWriter()
        val now = FakeNowProvider(time = 1_700_000_000_000L)
        val sut = PushBackupNowUseCase(repo, writer, now)

        val result = sut()

        assertTrue(result is BackupResult.Failure)
        assertEquals("Drive storage full", (result as BackupResult.Failure).reason)
        assertEquals(1, repo.backupCurrentDataCount)
        assertNull("lastBackupAt must stay untouched on Failure", writer.lastBackupAt)
    }

    @Test
    fun success_atDifferentNow_writesThatExactValue() = runTest {
        val repo = FakeBackupRepository(nextBackupResult = BackupResult.Success)
        val writer = FakeSettingsWriter()
        val now = FakeNowProvider(time = 42L)
        val sut = PushBackupNowUseCase(repo, writer, now)

        sut()

        assertEquals(42L, writer.lastBackupAt)
    }
}
