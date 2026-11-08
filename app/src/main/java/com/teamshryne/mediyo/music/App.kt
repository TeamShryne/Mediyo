package com.teamshryne.mediyo.music

import android.app.Application
import android.os.Build
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.datastore.preferences.core.edit
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.teamshryne.mediyo.data.migration.LegacyMediyoMigration
import com.teamshryne.mediyo.innertube.YouTube
import com.teamshryne.mediyo.innertube.models.YouTubeLocale
import com.teamshryne.mediyo.kugou.KuGou
import com.teamshryne.mediyo.music.constants.ContentCountryKey
import com.teamshryne.mediyo.music.constants.ContentLanguageKey
import com.teamshryne.mediyo.music.constants.CountryCodeToName
import com.teamshryne.mediyo.music.constants.InnerTubeCookieKey
import com.teamshryne.mediyo.music.constants.LanguageCodeToName
import com.teamshryne.mediyo.music.constants.MaxImageCacheSizeKey
import com.teamshryne.mediyo.music.constants.ProxyEnabledKey
import com.teamshryne.mediyo.music.constants.ProxyTypeKey
import com.teamshryne.mediyo.music.constants.ProxyUrlKey
import com.teamshryne.mediyo.music.constants.SYSTEM_DEFAULT
import com.teamshryne.mediyo.music.constants.UseLoginForBrowse
import com.teamshryne.mediyo.music.constants.VisitorDataKey
import com.teamshryne.mediyo.music.extensions.toEnum
import com.teamshryne.mediyo.music.extensions.toInetSocketAddress
import com.teamshryne.mediyo.music.utils.dataStore
import com.teamshryne.mediyo.music.utils.get
import com.teamshryne.mediyo.music.utils.FileTree
import com.teamshryne.mediyo.music.utils.reportException
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber
import java.net.Proxy
import java.util.Locale

@HiltAndroidApp
class App : Application(), ImageLoaderFactory {
    @Inject
    lateinit var legacyMigration: LegacyMediyoMigration

    @OptIn(DelicateCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        Timber.plant(FileTree(this))

        // Import the previous app's library. The import is guarded by a
        // preference, idempotent, and runs off the main thread: a large
        // library used to hold the first frame while it was copied.
        legacyMigration.start(GlobalScope)

        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "") // replace zh-Hant-* to zh-*
        YouTube.locale = YouTubeLocale(
            gl = dataStore[ContentCountryKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.country.takeIf { it in CountryCodeToName }
                ?: "US",
            hl = dataStore[ContentLanguageKey]?.takeIf { it != SYSTEM_DEFAULT }
                ?: locale.language.takeIf { it in LanguageCodeToName }
                ?: languageTag.takeIf { it in LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }

        if (dataStore[ProxyEnabledKey] == true) {
            try {
                YouTube.proxy = Proxy(
                    dataStore[ProxyTypeKey].toEnum(defaultValue = Proxy.Type.HTTP),
                    dataStore[ProxyUrlKey]!!.toInetSocketAddress()
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse proxy url.", LENGTH_SHORT).show()
                reportException(e)
            }
        }

        if (dataStore[UseLoginForBrowse] == true) {
            YouTube.useLoginForBrowse = true
        }

        GlobalScope.launch {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    YouTube.visitorData = visitorData
                        ?.takeIf { it != "null" } // Previously visitorData was sometimes saved as "null" due to a bug
                        ?: YouTube.visitorData().getOrNull()?.also { newVisitorData ->
                            dataStore.edit { settings ->
                                settings[VisitorDataKey] = newVisitorData
                            }
                        } ?: YouTube.DEFAULT_VISITOR_DATA
                }
        }
        GlobalScope.launch {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    YouTube.cookie = cookie
                }
        }
    }

    override fun newImageLoader() = ImageLoader.Builder(this)
        .crossfade(true)
        .respectCacheHeaders(false)
        .allowHardware(Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        .diskCache(
            DiskCache.Builder()
                .directory(cacheDir.resolve("coil"))
                .maxSizeBytes((dataStore[MaxImageCacheSizeKey] ?: 512) * 1024 * 1024L)
                .build()
        )
        .build()
}
