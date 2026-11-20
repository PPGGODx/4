package com.playzongx.data.network

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InnerTubeClientTest {

    private val server = MockWebServer()

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private fun client(): InnerTubeClient =
        InnerTubeClient(OkHttpClient(), server.url("/youtubei/v1/search?prettyPrint=false").toString())

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts query to search endpoint`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("search_mixed.json")))

        val result = client().search("lofi")
        val request = server.takeRequest()

        assertEquals("POST", request.method)
        assertEquals("/youtubei/v1/search?prettyPrint=false", request.path)
        assertTrue(request.body.readUtf8().contains("\"query\":\"lofi\""))
        assertEquals(1, result.tracks.size)
    }

    @Test
    fun `sends thai query as utf8`() = runBlocking {
        server.enqueue(MockResponse().setBody(fixture("search_mixed.json")))

        client().search("เพลงไทย")
        val request = server.takeRequest()

        assertTrue(request.body.readUtf8().contains("เพลงไทย"))
        assertEquals("67", request.getHeader("X-YouTube-Client-Name"))
        assertEquals("1.20250101.00.00", request.getHeader("X-YouTube-Client-Version"))
    }

    @Test
    fun `http 500 becomes MusicError Network`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        val error = runCatching { client().search("lofi") }.exceptionOrNull()

        assertTrue("expected MusicError.Network but was $error", error is MusicError.Network)
    }

    @Test
    fun `invalid json becomes MusicError Parse`() = runBlocking {
        server.enqueue(MockResponse().setBody("{oops"))

        val error = runCatching { client().search("lofi") }.exceptionOrNull()

        assertTrue("expected MusicError.Parse but was $error", error is MusicError.Parse)
    }

    @Test
    fun `empty results map to empty SearchResult`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"contents":{}}"""))

        val result = client().search("lofi")

        assertTrue(result.tracks.isEmpty())
        assertTrue(result.albums.isEmpty())
        assertTrue(result.artists.isEmpty())
        assertTrue(result.playlists.isEmpty())
    }
}
