package com.playzongx.ui.search

import com.playzongx.data.network.MusicError
import com.playzongx.data.repo.MusicRepository
import com.playzongx.domain.SearchResult
import com.playzongx.domain.Track
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    // Shared by the Main dispatcher (used by viewModelScope) and runTest so that
    // advanceUntilIdle() drives the ViewModel's coroutines deterministically.
    private val testScheduler = TestCoroutineScheduler()
    private val mainDispatcher = StandardTestDispatcher(testScheduler)

    private lateinit var repository: FakeMusicRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        repository = FakeMusicRepository()
        viewModel = SearchViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `blank query stays idle and does not call repository`() = runTest(testScheduler) {
        viewModel.search("   \t  ")
        advanceUntilIdle()

        assertEquals(SearchUiState.Idle, viewModel.state.value)
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `success emits result with track and uses trimmed query`() = runTest(testScheduler) {
        val track = Track("t1", "Title", "Artist", null, 123L, null)
        repository.handler = { Result.success(SearchResult(tracks = listOf(track))) }

        viewModel.search("  lofi  ")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("expected Success but was $state", state is SearchUiState.Success)
        assertEquals(listOf(track), (state as SearchUiState.Success).result.tracks)
        assertEquals(listOf("lofi"), repository.queries)
    }

    @Test
    fun `empty result emits Empty`() = runTest(testScheduler) {
        repository.handler = { Result.success(SearchResult()) }

        viewModel.search("nothing")
        advanceUntilIdle()

        assertEquals(SearchUiState.Empty, viewModel.state.value)
    }

    @Test
    fun `failure emits Error with non-blank message`() = runTest(testScheduler) {
        repository.handler = { Result.failure(MusicError.Network(IOException("offline"))) }

        viewModel.search("lofi")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("expected Error but was $state", state is SearchUiState.Error)
        assertTrue((state as SearchUiState.Error).message.isNotBlank())
    }

    @Test
    fun `thrown exception with no message emits readable Error`() = runTest(testScheduler) {
        repository.handler = { throw IllegalStateException() }

        viewModel.search("lofi")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("expected Error but was $state", state is SearchUiState.Error)
        assertTrue((state as SearchUiState.Error).message.isNotBlank())
    }

    @Test
    fun `cancellation failure does not emit Error`() = runTest(testScheduler) {
        repository.handler = { Result.failure(CancellationException()) }

        viewModel.search("lofi")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("cancellation must not produce Error but was $state", state !is SearchUiState.Error)
        assertEquals(SearchUiState.Loading, state)
    }

    @Test
    fun `second search replaces the first result`() = runTest(testScheduler) {
        repository.handler = { query ->
            Result.success(SearchResult(tracks = listOf(Track(query, query, "Artist", null, 1L, null))))
        }

        viewModel.search("first")
        advanceUntilIdle()
        assertTrue(viewModel.state.value is SearchUiState.Success)

        viewModel.search("second")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue("expected Success but was $state", state is SearchUiState.Success)
        assertEquals("second", (state as SearchUiState.Success).result.tracks.single().id)
    }

    @Test
    fun `slower first response cannot overwrite a newer search`() = runTest(testScheduler) {
        val slowGate = CompletableDeferred<Result<SearchResult>>()
        val fast = Track("fast", "Fast", "Artist", null, 100L, null)
        val slow = Track("slow", "Slow", "Artist", null, 200L, null)
        repository.handler = { query ->
            if (query == "slow") slowGate.await()
            else Result.success(SearchResult(tracks = listOf(fast)))
        }

        viewModel.search("slow")
        advanceUntilIdle()
        assertEquals(SearchUiState.Loading, viewModel.state.value)

        viewModel.search("fast")
        advanceUntilIdle()
        val newerState = viewModel.state.value
        assertTrue("expected Success but was $newerState", newerState is SearchUiState.Success)
        assertEquals(fast, (newerState as SearchUiState.Success).result.tracks.single())

        slowGate.complete(Result.success(SearchResult(tracks = listOf(slow))))
        advanceUntilIdle()

        val finalState = viewModel.state.value
        assertTrue("expected Success but was $finalState", finalState is SearchUiState.Success)
        assertEquals(fast, (finalState as SearchUiState.Success).result.tracks.single())
    }

    private class FakeMusicRepository(
        var handler: suspend (String) -> Result<SearchResult> = { Result.success(SearchResult()) },
    ) : MusicRepository {
        var callCount = 0
            private set
        val queries = mutableListOf<String>()

        override suspend fun search(query: String): Result<SearchResult> {
            callCount++
            queries.add(query)
            return handler(query)
        }
    }
}
