package org.spsl.evtracker.domain.usecase

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.spsl.evtracker.domain.backup.BackupResult
import org.spsl.evtracker.testing.FakeBackupRepository
import org.spsl.evtracker.testing.FakeSettingsWriter

class WipeRemoteBackupUseCaseTest {

    @Test
    fun success_callsDeleteOnce_andClearsLastBackupAt() = runTest {
        val repo = FakeBackupRepository(nextDeleteResult = BackupResult.Success)
        val writer = FakeSettingsWriter()
        // Seed a non-null lastBackupAt so we can prove it gets cleared.
        writer.setLastBackupAt(1_700_000_000_000L)
        val sut = WipeRemoteBackupUseCase(repo, writer)

        val result = sut()

        assertEquals(BackupResult.Success, result)
        assertEquals(1, repo.deleteCount)
        assertEquals(0L, writer.lastBackupAt)
    }

    @Test
    fun authRequired_propagates_andDoesNotClearLastBackupAt() = runTest {
        val repo = FakeBackupRepository(nextDeleteResult = BackupResult.AuthRequired)
        val writer = FakeSettingsWriter()
        writer.setLastBackupAt(1_700_000_000_000L)
        val sut = WipeRemoteBackupUseCase(repo, writer)

        val result = sut()

        assertEquals(BackupResult.AuthRequired, result)
        assertEquals(1, repo.deleteCount)
        assertEquals(
            "lastBackupAt must stay at its previous value on AuthRequired",
            1_700_000_000_000L,
            writer.lastBackupAt,
        )
    }

    @Test
    fun failure_propagates_andDoesNotClearLastBackupAt() = runTest {
        val repo = FakeBackupRepository(
            nextDeleteResult = BackupResult.Failure("HTTP 500"),
        )
        val writer = FakeSettingsWriter()
        writer.setLastBackupAt(1_700_000_000_000L)
        val sut = WipeRemoteBackupUseCase(repo, writer)

        val result = sut()

        assertTrue(result is BackupResult.Failure)
        assertEquals("HTTP 500", (result as BackupResult.Failure).reason)
        assertEquals(1, repo.deleteCount)
        assertEquals(1_700_000_000_000L, writer.lastBackupAt)
    }

    @Test
    fun success_whenNoPriorTimestamp_writesZero() = runTest {
        val repo = FakeBackupRepository(nextDeleteResult = BackupResult.Success)
        val writer = FakeSettingsWriter()
        // Don't seed anything — the field starts at null.
        assertNull(writer.lastBackupAt)
        val sut = WipeRemoteBackupUseCase(repo, writer)

        sut()

        // Even with no prior backup, success normalises the field to 0L so the
        // UI's "Last backup at …" hint reverts to its empty state.
        assertEquals(0L, writer.lastBackupAt)
    }
}
