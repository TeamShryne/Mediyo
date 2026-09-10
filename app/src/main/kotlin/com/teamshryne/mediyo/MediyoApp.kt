package com.teamshryne.mediyo

import android.app.Application
import com.teamshryne.mediyo.data.update.AppUpdater
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdaterCleanupEntryPoint {
    fun appUpdater(): AppUpdater
}

@HiltAndroidApp
class MediyoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Drop leftover updater APKs (cacheDir/updates) from previous installs.
        // UpdateViewModel state is in-memory, so anything on disk at startup
        // is either already installed or an abandoned download.
        try {
            EntryPointAccessors.fromApplication(this, UpdaterCleanupEntryPoint::class.java)
                .appUpdater()
                .cleanupStaleApks()
        } catch (ignored: Exception) {
            // Best-effort: never crash startup over cache pruning.
        }
    }
}
