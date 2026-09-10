package com.teamshryne.mediyo.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Widget-safe artwork fetch: small, no-hardware bitmap, fast timeout, disk-cached by Coil. */
suspend fun loadWidgetArtwork(context: Context, url: String?): Bitmap? {
    if (url.isNullOrBlank()) return null
    return withContext(Dispatchers.IO) {
        withTimeoutOrNull(4_000) {
            try {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context)
                    .data(url)
                    .allowHardware(false)
                    .size(320, 320)
                    .build()
                (loader.execute(req).drawable as? BitmapDrawable)?.bitmap
            } catch (_: Throwable) {
                null
            }
        }
    }
}
