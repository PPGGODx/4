package com.playzongx.data.network

import com.playzongx.domain.SearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMapperTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private fun parseFixture(name: String): SearchResult {
        val root = Json.parseToJsonElement(fixture(name)) as JsonObject
        return SearchMapper.parse(root)
    }

    @Test
    fun `maps song rows with duration and thumbnail`() {
        val result = parseFixture("search_mixed.json")

        assertEquals(1, result.tracks.size)
        val track = result.tracks.single()
        assertEquals("vid1", track.id)
        assertEquals("Song One", track.title)
        assertTrue(track.artists.contains("Artist A"))
        assertEquals(225_000L, track.durationMs)
        assertEquals("https://img.test/s.jpg", track.thumbnailUrl)
    }

    @Test
    fun `maps playlist rows by VL browseId`() {
        val result = parseFixture("search_playlist_vl.json")

        assertEquals(1, result.playlists.size)
        val playlist = result.playlists.single()
        assertEquals("VLPL123", playlist.id)
        assertEquals("Lo-fi Beats", playlist.title)
        assertEquals("Lo-fi Girl", playlist.author)
        assertEquals(120, playlist.trackCount)
        assertEquals("https://img.test/playlist.jpg", playlist.thumbnailUrl)
    }

    @Test
    fun `maps album card shelf`() {
        val result = parseFixture("search_mixed.json")

        assertEquals(1, result.albums.size)
        val album = result.albums.single()
        assertEquals("MPREb_album1", album.id)
        assertEquals("Album Y", album.title)
        assertTrue(album.artist!!.contains("Artist B"))
        assertEquals("https://img.test/album.jpg", album.thumbnailUrl)
    }

    @Test
    fun `skips malformed rows without crashing`() {
        val result = parseFixture("search_partial.json")

        assertTrue(result.tracks.isEmpty())
        assertTrue(result.albums.isEmpty())
        assertTrue(result.artists.isEmpty())
        assertTrue(result.playlists.isEmpty())
    }

    @Test
    fun `deduplicates repeated videoIds`() {
        val result = parseFixture("search_mixed.json")

        assertEquals(1, result.tracks.size)
        assertEquals(listOf("vid1"), result.tracks.map { it.id })
    }

    @Test
    fun `maps realistic album row skipping type label`() {
        val result = parseFixture("search_realistic.json")

        val album = result.albums.single { it.id == "MPREb_toofii" }
        assertEquals("Toofii", album.artist)
    }

    @Test
    fun `maps realistic playlist author and localized track count`() {
        val result = parseFixture("search_realistic.json")

        val english = result.playlists.single { it.id == "VLPLreal" }
        assertEquals("Lo-fi Girl", english.author)
        assertEquals(120, english.trackCount)

        val thai = result.playlists.single { it.id == "VLPLthai" }
        assertEquals("Lo-fi Girl", thai.author)
        assertEquals(120, thai.trackCount)
    }

    @Test
    fun `prefers linked artist run over unlinked type label`() {
        val result = parseFixture("search_realistic.json")

        val track = result.tracks.single { it.id == "vidRealistic" }
        assertTrue(track.artists.contains("Artist A"))
        assertTrue("artists should not contain the type label: ${track.artists}", !track.artists.contains("Song"))
        assertEquals(245_000L, track.durationMs)
    }

    @Test
    fun `maps two row renderer thumbnails`() {
        val result = parseFixture("search_realistic.json")

        val playlist = result.playlists.single { it.id == "VLPLtworow" }
        assertEquals("Lo-fi Girl", playlist.author)
        assertEquals(42, playlist.trackCount)
        assertEquals("https://img.test/tworow.jpg", playlist.thumbnailUrl)

        val album = result.albums.single { it.id == "MPREb_tworow" }
        assertEquals("Artist C", album.artist)
        assertEquals("https://img.test/tworow-album.jpg", album.thumbnailUrl)
    }
}
