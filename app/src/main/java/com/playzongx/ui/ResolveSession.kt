package com.playzongx.ui

import com.playzongx.domain.Track
import com.playzongx.player.ResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal sealed interface ResolveUiState {
    val track: Track

    data class Resolving(override val track: Track) : ResolveUiState
    data class Ready(override val track: Track, val label: String) : ResolveUiState
    data class Error(override val track: Track, val message: String) : ResolveUiState
}

/**
 * Owns the in-flight resolve job for the bottom sheet. The composable still
 * owns the visible state; this holder makes dismissal cancel the work and
 * prevents a late result from re-showing the sheet.
 */
internal class ResolveSession(
    private val scope: CoroutineScope,
    private val resolveAudio: suspend (String) -> Result<ResolvedStream>,
    private val onState: (ResolveUiState?) -> Unit,
) {
    private var job: Job? = null
    private var generation = 0

    fun start(track: Track) {
        job?.cancel()
        val token = ++generation
        onState(ResolveUiState.Resolving(track))
        job = scope.launch {
            val result = resolveAudio(track.id)
            ensureActive()
            if (token == generation) {
                onState(
                    result.fold(
                        onSuccess = { ResolveUiState.Ready(track, describeStream(it)) },
                        onFailure = { ResolveUiState.Error(track, it.message ?: "Content unavailable") },
                    ),
                )
            }
        }
    }

    fun dismiss() {
        job?.cancel()
        job = null
        generation++
        onState(null)
    }
}

/** Short, URL-free description of the resolved stream for the bottom sheet. */
private fun describeStream(stream: ResolvedStream): String {
    val format = when {
        stream.mimeType.contains("opus", ignoreCase = true) ||
            stream.mimeType.contains("webm", ignoreCase = true) -> "opus"
        stream.mimeType.contains("mp4", ignoreCase = true) ||
            stream.mimeType.contains("m4a", ignoreCase = true) -> "m4a"
        else -> "audio"
    }
    val bitrate = stream.bitrateKbps?.let { "$it kbps" } ?: "unknown bitrate"
    return "$format, $bitrate"
}
