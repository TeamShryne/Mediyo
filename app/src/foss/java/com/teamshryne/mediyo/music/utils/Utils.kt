package com.teamshryne.mediyo.music.utils

import com.teamshryne.mediyo.music.MainActivity
import java.lang.Exception
import timber.log.Timber

fun reportException(throwable: Throwable) {
    Timber.e(throwable)
    throwable.printStackTrace()
}
