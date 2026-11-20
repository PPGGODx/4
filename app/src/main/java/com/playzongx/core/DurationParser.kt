package com.playzongx.core

/**
 * Parses a colon-separated duration such as `"3:45"` (minutes:seconds) or
 * `"1:02:03"` (hours:minutes:seconds) into milliseconds.
 *
 * Returns `null` for null/blank input, non-numeric parts, a seconds part of 60
 * or more, or any part count other than two or three.
 */
fun parseDurationText(text: String?): Long? {
    val trimmed = text?.trim()
    if (trimmed.isNullOrEmpty()) return null

    val parts = trimmed.split(':')
    if (parts.size != 2 && parts.size != 3) return null
    if (parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null

    val values = parts.map { it.toLongOrNull() ?: return null }
    val seconds = values.last()
    if (seconds >= 60) return null

    val totalSeconds = if (values.size == 2) {
        values[0] * 60 + seconds
    } else {
        values[0] * 3_600 + values[1] * 60 + seconds
    }
    return totalSeconds * 1_000
}

/**
 * Formats milliseconds as display text: `"--:--"` when null or negative,
 * `M:SS` under one hour, and `H:MM:SS` from one hour onwards. Milliseconds are
 * truncated.
 */
fun formatDuration(ms: Long?): String {
    if (ms == null || ms < 0) return "--:--"

    val totalSeconds = ms / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60

    val secondsText = seconds.toString().padStart(2, '0')
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:$secondsText"
    } else {
        "$minutes:$secondsText"
    }
}
