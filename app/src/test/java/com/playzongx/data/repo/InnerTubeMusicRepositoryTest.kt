package com.playzongx.data.repo

import com.playzongx.data.network.InnerTubeClient
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class InnerTubeMusicRepositoryTest {

    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `canceled search throws CancellationException instead of returning a failure result`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setBody("""{"contents":{}}""")
                .setBodyDelay(1, TimeUnit.SECONDS),
        )
        val repository = InnerTubeMusicRepository(
            InnerTubeClient(
                OkHttpClient(),
                server.url("/youtubei/v1/search?prettyPrint=false").toString(),
            ),
        )
        val outcome = CompletableDeferred<Throwable?>()

        val job = launch(Dispatchers.IO) {
            val result = try {
                repository.search("lofi")
            } catch (error: Throwable) {
                outcome.complete(error)
                return@launch
            }
            // Reaching here means cancellation was wrapped into Result.failure.
            outcome.complete(null)
        }

        // Wait until the request reached the server (body is deliberately delayed),
        // then cancel while the client is blocked reading the response.
        withTimeout(5_000) { withContext(Dispatchers.IO) { server.takeRequest() } }
        job.cancel()

        val error = withTimeout(5_000) { outcome.await() }
        assertTrue("expected CancellationException but was $error", error is CancellationException)
        job.join()
    }
}
