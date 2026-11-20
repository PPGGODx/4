package com.playzongx.data.repo

import com.playzongx.data.network.InnerTubeClient
import com.playzongx.domain.SearchResult
import kotlinx.coroutines.CancellationException

/** Adapts [InnerTubeClient] exceptions into a [Result] for the UI layer. */
class InnerTubeMusicRepository(private val client: InnerTubeClient) : MusicRepository {
    override suspend fun search(query: String): Result<SearchResult> = try {
        Result.success(client.search(query))
    } catch (error: CancellationException) {
        // Cancellation is control flow, not a search failure; let it propagate.
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }
}
