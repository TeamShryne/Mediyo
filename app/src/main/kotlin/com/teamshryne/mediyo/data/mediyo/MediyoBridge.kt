package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
import uniffi.mediyo_ffi.browseListPage
import uniffi.mediyo_ffi.browsePlaylist
import uniffi.mediyo_ffi.browsePodcast
import uniffi.mediyo_ffi.search
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediyoBridge @Inject constructor(private val auth: AuthRepository) {
    private suspend fun session(): MediyoSession {
        val a = auth.flow.first()
        return if (a.isLoggedIn) MediyoSession.withAll(a.cookies, a.sapisid.ifEmpty { null }, a.visitorData.ifEmpty { "" }, a.pageId.ifEmpty { null })
        else MediyoSession()
    }

    suspend fun search(query: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        val s = session()
        try { search(s, query) } finally { s.close() }
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
