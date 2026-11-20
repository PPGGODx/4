package com.playzongx.player

/** A playable audio stream, ready to be handed to the player. */
data class ResolvedStream(
    val url: String,
    val mimeType: String,
    val bitrateKbps: Int?,
    val itag: Int?,
)

/** Resolves a YouTube video id to a playable audio stream. */
interface StreamResolver {
    suspend fun resolveAudio(videoId: String): Result<ResolvedStream>
}
