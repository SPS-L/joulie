package org.spsl.evtracker.data.backup

import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.UninstallModules
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.spsl.evtracker.di.BackupModule
import org.spsl.evtracker.domain.backup.DriveAuthManager
import org.spsl.evtracker.domain.backup.DriveRemoteSource
import org.spsl.evtracker.testing.FakeDriveAuthManager
import org.spsl.evtracker.testing.FakeDriveRemoteSource

@HiltAndroidTest
@UninstallModules(BackupModule::class)
@RunWith(AndroidJUnit4::class)
class DriveBackupWorkerTest {

    @get:Rule val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var fakeAuth: FakeDriveAuthManager
    @Inject lateinit var fakeRemote: FakeDriveRemoteSource

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class TestBackupModule {
        @Binds @Singleton abstract fun bindAuth(impl: FakeDriveAuthManager): DriveAuthManager
        @Binds @Singleton abstract fun bindRemote(impl: FakeDriveRemoteSource): DriveRemoteSource
    }

    private lateinit var context: Context

    @Before fun setUp() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun happyPath_returnsSuccess() = runBlocking {
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun authRevoked_returnsFailure() = runBlocking {
        fakeAuth.nextResult = DriveAuthManager.AuthResult.Failed("revoked")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.failure(), result)
    }

    @Test
    fun ioError_returnsRetry() = runBlocking {
        fakeRemote.failNext = IOException("offline")
        val worker = TestListenableWorkerBuilder<DriveBackupWorker>(context)
            .setWorkerFactory(workerFactory)
            .build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}
