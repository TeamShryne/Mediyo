package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JNI wrapper for libmediyo_ffi.so (built via cargo-ndk in CI: rust/crates/mediyo-ffi).
 * Falls back to stub data when .so not yet loaded (pre-build / preview).
 */
@Singleton
class MediyoBridge @Inject constructor(private val auth: AuthRepository) {
    companion object {
        var loaded = false
        init { try { System.loadLibrary("mediyo_ffi"); loaded = true } catch (_: Throwable) {} }
        @JvmStatic external fun nativeSearch(query: String, cookies: String): String
        @JvmStatic external fun nativeHome(cookies: String): String
    }

    // High-level suspend helpers — real FFI JSON, stub fallback
    suspend fun search(query: String): String = withContext(Dispatchers.IO) {
        try {
            if (loaded) nativeSearch(query, "") else """{"results":[],"filters":[]}"""
        } catch (_: Throwable) { """{"results":[],"filters":[]}""" }
    }

    suspend fun home(): String = withContext(Dispatchers.IO) {
        try { if (loaded) nativeHome("") else """{"carousels":[]}""" } catch (_: Throwable) { """{"carousels":[]}""" }
    }

    // Extract stream URL only via NewPipe is handled in data.playback.NewPipeResolver
}
