package com.elowen.niceTV

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler

@OptIn(UnstableApi::class)
class MyDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description
) {

    companion object {
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val CHANNEL_ID = "download_channel"
    }

    override fun getDownloadManager(): DownloadManager {
        return NiceTVApplication.downloadManager
    }

    override fun getScheduler(): Scheduler? {
        return null
    }

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)
        
        // Simple implementation: show "Downloading..." if any download is running
        // Ideally we iterate downloads to find the active one and show its progress
        // For now, using standard helper
        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_launcher_foreground, // Make sure this exists, or use small icon
            null,
            null,
            downloads,
            notMetRequirements
        )
    }
}
