package com.teamshryne.mediyo.debug

import android.util.Log
import com.teamshryne.mediyo.data.mediyo.MediyoBridge
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * adb control bus — lets an agent drive the app with nothing but `adb`.
 *
 * ```bash
 * adb shell am start -n com.teamshryne.mediyo.debug/com.teamshryne.mediyo.MainActivity
 * adb shell am broadcast -p com.teamshryne.mediyo.debug -a com.teamshryne.mediyo.adb \
 *     --es cmd go --es route artist --es arg UCXkARuJI4MIDAfuBqh0gA
 * adb exec-out screencap -p > shot.png
 * ```
 *
 * Why the split across source sets: the receiver that receives `am broadcast`
 * lives in `src/debug` and is declared only in `src/debug/AndroidManifest.xml`,
 * so a release APK has no component that can be talked to. This bus has to sit
 * in `main` because the activity is what owns the nav controller and the
 * player, and `main` must compile against something. Nothing in a release build
 * can emit on it, and the collection site in `AppShell` sits behind a
 * constant-false `BuildConfig.DEBUG`, so R8 strips the lot.
 */
object AdbControl {
    const val TAG = "MediyoAdb"

    /**
     * Commands are buffered so one sent before the activity is up still lands.
     * But a buffered command firing 15s late would move the app out from under
     * whatever the caller is looking at, so anything older is dropped instead.
     */
    const val MaxAgeMs = 15_000L

    // replay > 0 is what buffers while nothing is collecting; the staleness
    // check above is what keeps the cache from replaying ancient navigations.
    private val _commands = MutableSharedFlow<AdbCommand>(replay = 4, extraBufferCapacity = 8)
    val commands: SharedFlow<AdbCommand> = _commands.asSharedFlow()

    fun send(command: AdbCommand) {
        _commands.tryEmit(command)
    }

    fun isFresh(command: AdbCommand) =
        System.currentTimeMillis() - command.at <= MaxAgeMs

    fun log(message: String) = Log.i(TAG, message)
}

/** One adb request, already validated by the receiver. */
sealed interface AdbCommand {
    /** Wall clock at the moment adb handed the command over. */
    val at: Long

    /** Navigate to a route, e.g. `playlist/PLxxx` or `home`. */
    data class Go(override val at: Long, val route: String) : AdbCommand

    /** Open the search screen and run this query. */
    data class Search(override val at: Long, val query: String) : AdbCommand

    /**
     * Play something. [query] searches and plays the best song match;
     * [videoId] plays an exact id and wins over [query] when both are given.
     */
    data class Play(
        override val at: Long,
        val query: String?,
        val videoId: String?,
        val title: String?
    ) : AdbCommand

    /** Log the current player state to logcat. */
    data class Dump(override val at: Long) : AdbCommand
}

/**
 * Reaches the shared [MediyoBridge] for one-shot work that has no ViewModel to
 * live in — `play --es query` has to search before it can play.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AdbBridgeEntryPoint {
    fun bridge(): MediyoBridge
}