package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.mediyo_ffi.FfiAlbumPage
import uniffi.mediyo_ffi.FfiArtistPage
import uniffi.mediyo_ffi.FfiHomePage
import uniffi.mediyo_ffi.FfiListPage
import uniffi.mediyo_ffi.FfiPlaylistPage
import uniffi.mediyo_ffi.FfiSearchResponse
import uniffi.mediyo_ffi.MediyoSession
import uniffi.mediyo_ffi.browseAlbum
import uniffi.mediyo_ffi.browseArtist
import uniffi.mediyo_ffi.browseHome
import uniffi.mediyo_ffi.browseHomeContinue
import uniffi.mediyo_ffi.browseListPage
import uniffi.mediyo_ffi.browseNextPage
import uniffi.mediyo_ffi.browsePlaylist
import uniffi.mediyo_ffi.browsePodcast
import uniffi.mediyo_ffi.search
import uniffi.mediyo_ffi.searchContinuation
import uniffi.mediyo_ffi.searchWithParams
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediyoBridge @Inject constructor(private val auth: AuthRepository) {
    // Platform-owned visitorData for anonymous — fetched once and persisted
    private var cachedAnonVisitor: String? = null
    private val anonLock = Mutex()

    private suspend fun anonVisitor(): String {
        cachedAnonVisitor?.let { if (it.isNotEmpty()) return it }
        val a = auth.flow.first()
        if (a.visitorData.isNotEmpty()) {
            cachedAnonVisitor = a.visitorData
            return a.visitorData
        }
        // Fetch fresh visitorData once and persist even for anonymous
        val tmp = MediyoSession()
        return try {
            val vd = tmp.fetchVisitorData()
            cachedAnonVisitor = vd
            auth.saveAnonVisitor(vd)
            android.util.Log.d("MediyoBridge", "fetched anon visitor ${vd.take(20)}")
            vd
        } catch (e: Throwable) {
            android.util.Log.e("MediyoBridge", "fetch anon visitor failed", e)
            ""
        } finally {
            tmp.close()
        }
    }

    private suspend fun session(): MediyoSession {
        val a = auth.flow.first()
        return if (a.isLoggedIn) {
            MediyoSession.withAll(a.cookies, a.sapisid.ifEmpty { null }, a.visitorData.ifEmpty { "" }, a.pageId.ifEmpty { null })
        } else {
            val vd = anonLock.withLock { anonVisitor() }
            if (vd.isNotEmpty()) MediyoSession.withAll("", null, vd, null) else MediyoSession()
        }
    }

    suspend fun search(query: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        val s = session()
        try { search(s, query) } finally { s.close() }
    }

    /** Search scoped to a library-provided filter (params from FfiSearchFilter). */
    suspend fun searchFiltered(query: String, params: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        val s = session()
        try { searchWithParams(s, query, params) } finally { s.close() }
    }

    /** Next page of search results (works for plain and filtered searches). */
    suspend fun searchNext(token: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        val s = session()
        try { searchContinuation(s, token) } finally { s.close() }
    }

    /** Next page of the home feed shelves. */
    suspend fun homeContinue(token: String): FfiHomePage = withContext(Dispatchers.IO) {
        val s = session()
        try { browseHomeContinue(s, token) } finally { s.close() }
    }

    /** Generic continuation for browse pages (playlist/album/artist/list items). */
    suspend fun nextPage(token: String): FfiListPage = withContext(Dispatchers.IO) {
        val s = session()
        try { browseNextPage(s, token) } finally { s.close() }
    }

    suspend fun home(): FfiHomePage = withContext(Dispatchers.IO) {
        val s = session()
        try { browseHome(s) } finally { s.close() }
    }

    suspend fun playlist(browseId: String): FfiPlaylistPage = withContext(Dispatchers.IO) {
        val s = session(); try { browsePlaylist(s, browseId) } finally { s.close() }
    }
    suspend fun album(browseId: String): FfiAlbumPage = withContext(Dispatchers.IO) {
        val s = session(); try { browseAlbum(s, browseId) } finally { s.close() }
    }
    suspend fun artist(browseId: String): FfiArtistPage = withContext(Dispatchers.IO) {
        val s = session(); try { browseArtist(s, browseId) } finally { s.close() }
    }
    suspend fun podcast(browseId: String): FfiListPage = withContext(Dispatchers.IO) {
        val s = session(); try { browsePodcast(s, browseId) } finally { s.close() }
    }
    suspend fun listPage(browseId: String, params: String?): FfiListPage = withContext(Dispatchers.IO) {
        val s = session(); try { browseListPage(s, browseId, params) } finally { s.close() }
    }

    suspend fun account(): uniffi.mediyo_ffi.FfiAccountInfo = withContext(Dispatchers.IO) {
        val s = session(); try { uniffi.mediyo_ffi.accountInfo(s) } finally { s.close() }
    }
}
