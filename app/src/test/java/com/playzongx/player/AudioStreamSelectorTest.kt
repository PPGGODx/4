package com.playzongx.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioStreamSelectorTest {

    @Test
    fun `prefers highest bitrate opus`() {
        val lowOpus = AudioCandidate("https://example.com/low", "audio/webm; codecs=opus", 48)
        val highOpus = AudioCandidate("https://example.com/high", "audio/webm; codecs=opus", 160)
        val m4a = AudioCandidate("https://example.com/m4a", "audio/mp4", 256)

        assertEquals(highOpus, selectBestAudioStream(listOf(lowOpus, highOpus, m4a)))
    }

    @Test
    fun `falls back to m4a when no opus`() {
        val lowM4a = AudioCandidate("https://example.com/low", "audio/mp4", 64)
        val highM4a = AudioCandidate("https://example.com/high", "audio/mp4", 128)
        val other = AudioCandidate("https://example.com/mpeg", "audio/mpeg", 320)

        assertEquals(highM4a, selectBestAudioStream(listOf(lowM4a, highM4a, other)))
    }

    @Test
    fun `returns null when no audio candidates`() {
        assertNull(selectBestAudioStream(emptyList()))
    }

    @Test
    fun `ignores candidates with blank url`() {
        val valid = AudioCandidate("https://example.com/valid", "audio/webm", 128)
        val blank = AudioCandidate("   ", "audio/webm", 320)

        assertEquals(valid, selectBestAudioStream(listOf(blank, valid)))
        assertNull(selectBestAudioStream(listOf(blank, blank.copy(url = ""))))
    }

    @Test
    fun `treats unknown bitrate as zero`() {
        val unknown = AudioCandidate("https://example.com/unknown", "audio/webm", null)
        val known = AudioCandidate("https://example.com/known", "audio/webm", 128)

        assertEquals(known, selectBestAudioStream(listOf(unknown, known)))
        assertEquals(unknown, selectBestAudioStream(listOf(unknown)))
    }
}
