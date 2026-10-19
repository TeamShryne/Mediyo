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
     * *non-prerelease* release's asset, so the check needs no API calls or
     * rate limits — and nightlies, published as prereleases, stay invisible
     * to stable users.
     */
    const val LATEST_META = "https://github.com/$REPO/releases/latest/download/update.json"

    /**
     * The rolling `nightly` release, whose assets are replaced by each daily
     * build. Nightlies are prereleases, so they need their own tag to be
     * addressable at all.
     */
    const val NIGHTLY_META = "https://github.com/$REPO/releases/download/nightly/update.json"

    /** Metadata endpoint for a given channel. */
    fun metaUrl(channel: UpdateChannel): String = when (channel) {
        UpdateChannel.STABLE -> LATEST_META
        UpdateChannel.NIGHTLY -> NIGHTLY_META
    }
}
