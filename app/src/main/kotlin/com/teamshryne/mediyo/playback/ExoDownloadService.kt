package com.teamshryne.mediyo.playback

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import com.teamshryne.mediyo.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Runs downloads in a `dataSync` foreground service with a progress
 * notification, and resumes interrupted downloads via the platform
 * scheduler. All download state lives in [DownloadUtil]; this is plumbing.
 */
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class ExoDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.downloads,
    0
) {
    @Inject lateinit var downloadUtil: DownloadUtil

    override fun getDownloadManager() = downloadUtil.downloadManager

    override fun getScheduler() = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int
    ): Notification {
        val title = if (downloads.size == 1) {
            runCatching { String(downloads[0].request.data).takeIf { it.isNotBlank() } }.getOrNull()
        } else {
            "${downloads.size} downloads"
        } ?: "Downloads"
        return downloadUtil.notificationHelper.buildProgressNotification(
            this,
            R.drawable.ic_notification_small,
            null,
            title,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        const val CHANNEL_ID = "mediyo_downloads"
        const val NOTIFICATION_ID = 2001
        const val JOB_ID = 2001
    }
}
