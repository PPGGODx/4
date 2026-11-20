package com.playzongx.ui

import com.playzongx.domain.Track
import com.playzongx.player.ResolvedStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResolveSessionTest {

    private val track = Track(
        id = "video-1",
        title = "Track",
        artists = "Artist",
        album = null,
        durationMs = null,
        thumbnailUrl = null,
    )

    private val stream = ResolvedStream(
        url = "https://example.com/audio",
        mimeType = "audio/webm",
        bitrateKbps = 128,
        itag = 251,
    )

    @Test
    fun `dismiss cancels in-flight resolve and clears sheet state`() = runTest {
        val states = mutableListOf<ResolveUiState?>()
        val pending = CompletableDeferred<Result<ResolvedStream>>()
        val session = ResolveSession(this, { pending.await() }) { states.add(it) }

        session.start(track)
        assertEquals(listOf(ResolveUiState.Resolving(track)), states)

        session.dismiss()
        assertEquals(listOf(ResolveUiState.Resolving(track), null), states)

        pending.complete(Result.success(stream))
        runCurrent()
        assertEquals("late result must not reopen the sheet", listOf(ResolveUiState.Resolving(track), null), states)
    }

    @Test
    fun `late result from cancellation-ignoring resolver is not published`() = runTest {
        val states = mutableListOf<ResolveUiState?>()
        val pending = CompletableDeferred<Result<ResolvedStream>>()
        val session = ResolveSession(
            scope = this,
            resolveAudio = { withContext(NonCancellable) { pending.await() } },
            onState = { states.add(it) },
        )

        session.start(track)
        session.dismiss()
        pending.complete(Result.success(stream))
        runCurrent()

        assertEquals(listOf(ResolveUiState.Resolving(track), null), states)
    }

    @Test
    fun `starting a new resolve cancels the previous one`() = runTest {
        val states = mutableListOf<ResolveUiState?>()
        val firstPending = CompletableDeferred<Result<ResolvedStream>>()
        val secondPending = CompletableDeferred<Result<ResolvedStream>>()
        val nextTrack = track.copy(id = "video-2")
        val resolveJobs = mutableListOf<Job>()
        var calls = 0
        val session = ResolveSession(
            scope = this,
            resolveAudio = {
                resolveJobs.add(currentCoroutineContext()[Job]!!)
                if (calls++ == 0) firstPending.await() else secondPending.await()
            },
            onState = { states.add(it) },
        )

        session.start(track)
        runCurrent()
        session.start(nextTrack)
        assertTrue("previous resolve job should be cancelled", resolveJobs[0].isCancelled)
        runCurrent()
        firstPending.complete(Result.success(stream))
        runCurrent()
        assertEquals(listOf(ResolveUiState.Resolving(track), ResolveUiState.Resolving(nextTrack)), states)

        secondPending.complete(Result.success(stream))
        runCurrent()
        assertEquals(
            listOf(
                ResolveUiState.Resolving(track),
                ResolveUiState.Resolving(nextTrack),
                ResolveUiState.Ready(nextTrack, "opus, 128 kbps"),
            ),
            states,
        )
    }
}
