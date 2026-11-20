package com.playzongx.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.playzongx.app.AppContainer
import com.playzongx.domain.Track
import com.playzongx.ui.search.SearchScreen
import com.playzongx.ui.search.SearchViewModel

/** Root composable: owns the search view model and connects it to the search screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(
    container: AppContainer,
    onTrackClick: (Track) -> Unit = {},
) {
    val repository = container.musicRepository
    val streamResolver = container.streamResolver
    val factory = remember(repository) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
                    return SearchViewModel(repository) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
    }
    val viewModel: SearchViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Keep the submitted query so Retry can repeat it after an error. Saved so it
    // survives configuration changes just like the text field's displayed query.
    var lastQuery by rememberSaveable { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    var resolveState by remember { mutableStateOf<ResolveUiState?>(null) }
    val resolveSession = remember(streamResolver, scope) {
        ResolveSession(scope, { videoId -> streamResolver.resolveAudio(videoId) }) { resolveState = it }
    }

    fun startResolve(track: Track) {
        onTrackClick(track)
        resolveSession.start(track)
    }

    SearchScreen(
        state = state,
        onSearch = { query ->
            lastQuery = query
            viewModel.search(query)
        },
        onRetry = { viewModel.search(lastQuery) },
        onTrackClick = ::startResolve,
    )

    val currentResolve = resolveState
    if (currentResolve != null) {
        ModalBottomSheet(onDismissRequest = { resolveSession.dismiss() }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    text = currentResolve.track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(12.dp))
                when (currentResolve) {
                    is ResolveUiState.Resolving -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(8.dp))
                        Text("Resolving…", style = MaterialTheme.typography.bodyMedium)
                    }
                    is ResolveUiState.Ready ->
                        Text("Ready (${currentResolve.label})", style = MaterialTheme.typography.bodyMedium)
                    is ResolveUiState.Error -> {
                        Text(
                            text = currentResolve.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { startResolve(currentResolve.track) }) { Text("Retry") }
                    }
                }
            }
        }
    }
}
