package com.teamshryne.mediyo.data.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the update.json asset published on every GitHub release by
 * .github/workflows/release.yml. This is the whole "server" side of the
 * in-app updater — no backend of our own involved.
 */
@Serializable
data class UpdateInfo(
    @SerialName("package") val apkPackage: String = "",
    val version: String = "",
    val versionCode: Int = 0,
    val apkName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val publishedAt: String = "",
)

object UpdateUrls {
    const val REPO = "TeamShryne/Mediyo"

    /**
     * GitHub resolves /releases/latest/download/<asset> to the newest
     * release's asset, so the check needs no API calls or rate limits.
     */
    const val LATEST_META = "https://github.com/$REPO/releases/latest/download/update.json"
}
