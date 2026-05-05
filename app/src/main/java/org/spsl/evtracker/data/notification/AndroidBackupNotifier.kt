// SPDX-FileCopyrightText: 2026 Cyprus University of Technology,
//                         Sustainable Power Systems Lab <https://sps-lab.org>
// SPDX-License-Identifier: GPL-3.0-or-later

package org.spsl.evtracker.data.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavDeepLinkBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import org.spsl.evtracker.R
import org.spsl.evtracker.domain.notification.BackupNotifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Posts the two backup-related notifications via [NotificationManagerCompat].
 *
 * If `POST_NOTIFICATIONS` is missing on Android 13+, `notify(...)` is a
 * silent no-op — the permission gate lives in `MainActivity`, not here.
 */
@Singleton
class AndroidBackupNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackupNotifier {

    private val nm get() = NotificationManagerCompat.from(context)

    /**
     * Wraps `NotificationManagerCompat.notify` with the runtime permission gate
     * Android 13+ requires. The check itself is what satisfies lint's
     * `MissingPermission` rule — without it, lint refuses to compile any
     * `notify()` call from app code regardless of manifest declaration.
     */
    @SuppressLint("MissingPermission")
    private fun safeNotify(id: Int, n: android.app.Notification) {
        if (nm.areNotificationsEnabled()) nm.notify(id, n)
    }

    override fun notifyChronicFailure() {
        val intent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.settingsFragment)
            .createPendingIntent()
        val n = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_joulie)
            .setContentTitle(context.getString(R.string.backup_notif_chronic_failure_title))
            .setContentText(context.getString(R.string.backup_notif_chronic_failure_body))
            .setContentIntent(intent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        safeNotify(NOTIFICATION_ID_CHRONIC, n)
    }

    override fun notifyAuthRequired() {
        val intent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.settingsFragment)
            .createPendingIntent()
        val n = NotificationCompat.Builder(context, CHANNEL_AUTH)
            .setSmallIcon(R.drawable.ic_stat_joulie)
            .setContentTitle(context.getString(R.string.backup_notif_auth_required_title))
            .setContentText(context.getString(R.string.backup_notif_auth_required_body))
            .setContentIntent(intent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(NOTIFICATION_ID_AUTH, n)
    }

    override fun clearAll() {
        nm.cancel(NOTIFICATION_ID_CHRONIC)
        nm.cancel(NOTIFICATION_ID_AUTH)
    }

    companion object {
        const val CHANNEL_STATUS = "backup_status"
        const val CHANNEL_AUTH = "backup_auth"
        private const val NOTIFICATION_ID_CHRONIC = 1001
        private const val NOTIFICATION_ID_AUTH = 1002

        /**
         * Idempotent. Called from `EVTrackerApp.onCreate`. Pre-Oreo this is
         * a no-op because notification channels were introduced in API 26
         * (== `minSdk`).
         */
        fun ensureChannels(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.backup_notif_channel_status_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.backup_notif_channel_status_description)
            }
            val authChannel = NotificationChannel(
                CHANNEL_AUTH,
                context.getString(R.string.backup_notif_channel_auth_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.backup_notif_channel_auth_description)
            }
            mgr.createNotificationChannel(statusChannel)
            mgr.createNotificationChannel(authChannel)
        }
    }
}
