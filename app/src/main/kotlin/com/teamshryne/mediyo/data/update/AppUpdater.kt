package com.teamshryne.mediyo.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.teamshryne.mediyo.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateCheck {
    data object UpToDate : UpdateCheck
    data class Available(val info: UpdateInfo) : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/**
 * GitHub-Releases-only updater backend: check update.json, download the APK
 * with progress, verify its SHA-256, and build the install intent.
 * No dependency beyond HttpURLConnection + kotlinx.serialization.
 */
@Singleton
class AppUpdater @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        /** Subdirectory of [Context.getCacheDir] holding downloaded updater APKs. */
        const val UPDATES_SUBDIR = "updates"
    }

    /**
     * Deletes leftover updater APKs from previous runs. Called on app startup:
     * [com.teamshryne.mediyo.feature.update.UpdateViewModel] state is in-memory,
     * so any APK on disk at startup is either already installed or an abandoned
     * download — safe to drop. Only `*.apk` files are touched.
     * @return number of files deleted.
     */
    fun cleanupStaleApks(): Int {
        return try {
            val dir = File(ctx.cacheDir, UPDATES_SUBDIR)
            if (!dir.isDirectory) return 0
            dir.listFiles { file -> file.isFile && file.extension.equals("apk", ignoreCase = true) }
                ?.count { it.delete() } ?: 0
        } catch (ignored: Exception) {
            0
        }
    }

    suspend fun check(): UpdateCheck = withContext(Dispatchers.IO) {
        // Updater is release-only: never hit the network from debug builds
        // (different applicationId/versioning, side-by-side installs).
        if (BuildConfig.DEBUG) return@withContext UpdateCheck.UpToDate
        try {
            val conn = openGet(UpdateUrls.LATEST_META, readTimeoutMs = 15_000)
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext UpdateCheck.Failed("Server returned ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val info = json.decodeFromString<UpdateInfo>(body)
            if (info.versionCode <= 0 || info.apkUrl.isBlank()) {
                return@withContext UpdateCheck.Failed("Bad update metadata")
            }
            if (info.versionCode > BuildConfig.VERSION_CODE) UpdateCheck.Available(info)
            else UpdateCheck.UpToDate
        } catch (e: IOException) {
            UpdateCheck.Failed(e.message ?: "Network error")
        } catch (e: Exception) {
            UpdateCheck.Failed(e.message ?: "Check failed")
        }
    }

    /**
     * Downloads the APK to app-private cache, reporting 0..1 progress
     * (null when the server omits the length). Verifies SHA-256 before
     * returning; deletes and throws on mismatch.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Float?) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (BuildConfig.DEBUG) throw IllegalStateException("App updates are disabled in debug builds")
            val dir = File(ctx.cacheDir, UPDATES_SUBDIR).apply { mkdirs() }
            val out = File(dir, info.apkName.ifBlank { "mediyo-update.apk" })
            val conn = openGet(info.apkUrl, readTimeoutMs = 30_000)
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw IOException("Server returned ${conn.responseCode}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 }
            var done = 0L
            // Report on the IO thread; callers hop to main when touching UI.
            onProgress(if (total == null) null else 0f)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        total?.let { onProgress(done.toFloat() / it) }
                    }
                }
            }
            conn.disconnect()
            if (!verifySha256(out, info.sha256)) {
                out.delete()
                throw SecurityException("SHA-256 mismatch — download rejected")
            }
            out
        }

    fun verifySha256(file: File, expectedHex: String): Boolean {
        if (expectedHex.isBlank() || !file.isFile) return false
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == expectedHex.lowercase()
    }

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Android 8+ requires the user to allow installs from this app's source. */
    fun needsUnknownSourcesPermission(): Boolean =
        Build.VERSION.SDK_INT >= 26 && !ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    private fun openGet(url: String, readTimeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = readTimeoutMs
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json, */*")
        }
}
