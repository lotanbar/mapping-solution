package com.mappingsolution.ui.common

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

/**
 * Returns true if the string contains any RTL character (Hebrew / Arabic).
 * Uses the "any RTL wins" heuristic instead of "first strong character",
 * because Android's soft keyboard often silently prepends an invisible
 * LTR mark (U+200E) to Hebrew input in LTR-configured TextFields, which
 * would fool a first-strong heuristic into choosing LTR.
 */
fun String.isRtl(): Boolean = any { char ->
    val d = Character.getDirectionality(char)
    d == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
        d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
}

fun String.resolvedTextDirection(): TextDirection =
    if (isRtl()) TextDirection.Rtl else TextDirection.Ltr

fun String.resolvedTextAlign(): TextAlign =
    if (isRtl()) TextAlign.Right else TextAlign.Left
