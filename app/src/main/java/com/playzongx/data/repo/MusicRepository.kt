package com.playzongx.data.repo

import com.playzongx.domain.SearchResult

/** Search boundary between the UI and the music data source. */
interface MusicRepository {
    suspend fun search(query: String): Result<SearchResult>
}
