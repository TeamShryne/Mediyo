package com.teamshryne.mediyo.core.design

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * Scrollable bottom clearance matching the floating overlays (tab bar +
 * mini player + system inset), measured live in MainActivity.
 *
 * Screens add this to their list's bottom contentPadding so content
 * physically extends *behind* the translucent bars (visible through them)
 * while the last item can still scroll fully into view. Bottom sheets and
 * dialogs must NOT consume this — they float above everything already.
 */
val LocalOverlayBottom = compositionLocalOf { 0.dp }
