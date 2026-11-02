package com.teamshryne.mediyo.music.utils

import com.teamshryne.mediyo.music.db.entities.LyricsEntity

object TranslationHelper {
    suspend fun translate(lyrics: LyricsEntity): LyricsEntity = lyrics
    suspend fun clearModels() {}
}