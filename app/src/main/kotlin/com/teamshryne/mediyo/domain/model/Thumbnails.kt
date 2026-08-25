package com.teamshryne.mediyo.domain.model

import uniffi.mediyo_ffi.FfiThumbnail

/**
 * InnerTube thumbnail ladders are ordered smallest-first.
 * Pick the largest URL available, falling back to the first entry.
 */
fun List<FfiThumbnail>.bestThumbUrl(): String? =
    maxByOrNull { it.width }?.url ?: firstOrNull()?.url

private val ytimgSizeFile = Regex("""/(default|mqdefault|hqdefault|sddefault)\.jpg""")

/**
 * Rewrites a stored YouTube thumbnail URL to request higher-resolution art.
 * - googleusercontent "...=w60-h60-l90-rj" -> "=w544-h544-l90-rj"
 * - i.ytimg.com "/hqdefault.jpg" -> "/maxresdefault.jpg"
 * Returns the receiver untouched when it matches neither shape.
 */
fun String?.upscaledThumbUrl(): String? {
    if (this == null) return null
    if (contains("ytimg.com")) {
        if (!ytimgSizeFile.containsMatchIn(this)) return this
        return ytimgSizeFile.replace(this, "/maxresdefault.jpg")
    }
    val m = Regex("""w\d+-h\d+""").find(this) ?: return this
    return replaceRange(m.range, "w544-h544")
}
