package com.fearmikey.projectreporter.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-wide haptic vocabulary, so the same kind of action always feels the same.
 *
 * Built on Compose's [HapticFeedback], which respects the system "touch feedback" setting
 * and falls back to the closest supported effect on older Android versions.
 *
 * Note: long-press gestures (`combinedClickable(onLongClick = …)`) already play a long-press
 * effect, so don't add another one on top of them.
 */
@Stable
class AppHaptics internal constructor(private val haptics: HapticFeedback) {

    /** An item was added to / removed from a multi-selection by tapping it. */
    fun selectionToggled(selected: Boolean) = perform(
        if (selected) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
    )

    /** Selection mode was entered from a button (not a long press), or "Select all" was used. */
    fun selectionChanged() = perform(HapticFeedbackType.SegmentTick)

    /** Something was moved to the Recycle Bin (recoverable). */
    fun movedToBin() = perform(HapticFeedbackType.Confirm)

    /** Something was restored from the Recycle Bin or a delete was undone. */
    fun restored() = perform(HapticFeedbackType.Confirm)

    /** Something was permanently deleted. Stronger than [movedToBin] because it can't be undone. */
    fun deletedPermanently() = perform(HapticFeedbackType.LongPress)

    private fun perform(type: HapticFeedbackType) = haptics.performHapticFeedback(type)
}

@Composable
fun rememberAppHaptics(): AppHaptics {
    val haptics = LocalHapticFeedback.current
    return remember(haptics) { AppHaptics(haptics) }
}
