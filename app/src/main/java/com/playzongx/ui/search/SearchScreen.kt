package com.playzongx.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.playzongx.core.formatDuration
import com.playzongx.domain.Track

@Composable
fun SearchScreen(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onRetry: () -> Unit,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("ค้นหาเพลง...") },
            placeholder = { Text("ค้นหาเพลง...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboardController?.hide()
                    onSearch(query)
                },
            ),
        )

        Spacer(Modifier.height(12.dp))

        when (state) {
            SearchUiState.Idle -> CenteredMessage("Search for a song to get started.")
            SearchUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Searching…", style = MaterialTheme.typography.bodyMedium)
                }
            }
            is SearchUiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Retry") }
                    }
                }
            }
            SearchUiState.Empty -> CenteredMessage("No results")
            is SearchUiState.Success -> SearchResults(state, onTrackClick)
        }
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState.Success,
    onTrackClick: (Track) -> Unit,
) {
    val result = state.result
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (result.tracks.isNotEmpty()) {
            item(key = "tracks_header") { SectionHeader("Songs") }
            items(items = result.tracks, key = { it.id }) { track ->
                TrackRow(track, onTrackClick)
            }
        }
        if (result.albums.isNotEmpty()) {
            item(key = "albums_header") { SectionHeader("Albums") }
            items(items = result.albums, key = { "album:${it.id}" }) { album ->
                SummaryRow(album.title, album.artist, album.thumbnailUrl)
            }
        }
        if (result.artists.isNotEmpty()) {
            item(key = "artists_header") { SectionHeader("Artists") }
            items(items = result.artists, key = { "artist:${it.id}" }) { artist ->
                SummaryRow(artist.name, null, artist.thumbnailUrl)
            }
        }
        if (result.playlists.isNotEmpty()) {
            item(key = "playlists_header") { SectionHeader("Playlists") }
            items(items = result.playlists, key = { "playlist:${it.id}" }) { playlist ->
                val subtitle = listOfNotNull(
                    playlist.author,
                    playlist.trackCount?.let { "$it tracks" },
                ).joinToString(" · ")
                SummaryRow(playlist.title, subtitle, playlist.thumbnailUrl)
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

@Composable
private fun TrackRow(track: Track, onClick: (Track) -> Unit) {
    ListItem(
        headlineContent = {
            Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(track.artists, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = { Thumbnail(track.thumbnailUrl) },
        trailingContent = {
            Text(
                formatDuration(track.durationMs),
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.clickable { onClick(track) },
    )
}

@Composable
private fun SummaryRow(title: String, subtitle: String?, thumbnailUrl: String?) {
    ListItem(
        headlineContent = {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        leadingContent = { Thumbnail(thumbnailUrl) },
    )
}

/** Thumbnail with a tinted placeholder that also shows through if loading fails. */
@Composable
private fun Thumbnail(url: String?) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Text("♪", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
