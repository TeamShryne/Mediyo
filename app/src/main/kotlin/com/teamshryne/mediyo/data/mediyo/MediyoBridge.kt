package com.teamshryne.mediyo.data.mediyo

import com.teamshryne.mediyo.FfiHomePage
import com.teamshryne.mediyo.FfiSearchResponse
import com.teamshryne.mediyo.MediyoSession
import com.teamshryne.mediyo.data.auth.AuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediyoBridge @Inject constructor(private val auth: AuthRepository) {
    private suspend fun session(): MediyoSession {
        val a = auth.flow.first()
        return if (a.isLoggedIn) MediyoSession.withAll(a.cookies, a.sapisid.ifEmpty { null }, a.visitorData.ifEmpty { "" }, a.pageId.ifEmpty { null })
        else MediyoSession.new()
    }

    suspend fun search(query: String): FfiSearchResponse = withContext(Dispatchers.IO) {
        val s = session()
        try { s.search(query) } finally { s.close() }
    }

    suspend fun home(): FfiHomePage = withContext(Dispatchers.IO) {
        val s = session()
        try { s.browseHome() } finally { s.close() }
    }
}
