package com.playzongx.player

/** A playable audio stream reported by the extractor, before a final choice is made. */
data class AudioCandidate(
    val url: String,
    val mimeType: String?,
    val bitrateKbps: Int?,
)

/**
 * Picks the best audio candidate.
 *
 * Opus/WebM streams are preferred over MP4/M4A streams; within the preferred
 * group the highest average bitrate wins. An unknown bitrate counts as zero.
 * Candidates with a blank URL or an unrecognised MIME type are ignored.
 */
fun selectBestAudioStream(candidates: List<AudioCandidate>): AudioCandidate? {
    val usable = candidates.filter { it.url.isNotBlank() }
    val opus = usable.filter { it.isOpus }
    val preferred = opus.ifEmpty { usable.filter { it.isMp4 } }
    return preferred.maxByOrNull { it.bitrateKbps ?: 0 }
}

private val AudioCandidate.isOpus: Boolean
    get() = mimeType?.contains("opus", ignoreCase = true) == true ||
        mimeType?.contains("webm", ignoreCase = true) == true

private val AudioCandidate.isMp4: Boolean
    get() = mimeType?.contains("mp4", ignoreCase = true) == true ||
        mimeType?.contains("m4a", ignoreCase = true) == true
