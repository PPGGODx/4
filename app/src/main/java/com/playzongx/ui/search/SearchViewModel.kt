package com.playzongx.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.playzongx.data.repo.MusicRepository
import com.playzongx.domain.SearchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Loading : SearchUiState
    data class Success(val result: SearchResult) : SearchUiState
    data class Error(val message: String) : SearchUiState
    data object Empty : SearchUiState
}

/** Holds search screen state; each new search invalidates any in-flight response. */
class SearchViewModel(private val repository: MusicRepository) : ViewModel() {

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var latestQuery: String? = null
    private var requestId = 0L

    fun search(query: String) {
        val trimmed = query.trim()
        val currentRequestId = ++requestId
        latestQuery = trimmed.takeIf { it.isNotEmpty() }

        if (trimmed.isEmpty()) {
            _state.value = SearchUiState.Idle
            return
        }

        _state.value = SearchUiState.Loading
        viewModelScope.launch {
            val result = try {
                repository.search(trimmed)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }

            if (currentRequestId != requestId || trimmed != latestQuery) return@launch

            _state.value = result.fold(
                onSuccess = { searchResult ->
                    if (searchResult.isEmpty()) SearchUiState.Empty else SearchUiState.Success(searchResult)
                },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    SearchUiState.Error(
                        error.message?.takeIf { it.isNotBlank() } ?: GENERIC_ERROR_MESSAGE,
                    )
                },
            )
        }
    }

    private fun SearchResult.isEmpty(): Boolean =
        tracks.isEmpty() && albums.isEmpty() && artists.isEmpty() && playlists.isEmpty()

    private companion object {
        const val GENERIC_ERROR_MESSAGE = "Search failed. Please try again."
    }
}
