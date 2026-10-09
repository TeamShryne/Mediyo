package com.teamshryne.mediyo.data.cache

import javax.inject.Qualifier

/** Transient LRU song cache: everything streamed lands here, evicted oldest-first. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PlayerCache

/** Permanent offline store: only the download manager writes here, never evicted. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DownloadCache
