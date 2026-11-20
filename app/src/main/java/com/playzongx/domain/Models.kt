package com.playzongx.domain

data class Track(
    val id: String,
    val title: String,
    val artists: String,
    val album: String?,
    val durationMs: Long?,
    val thumbnailUrl: String?,
)

data class AlbumSummary(
    val id: String,
    val title: String,
    val artist: String?,
    val thumbnailUrl: String?,
)

data class ArtistSummary(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
)

data class PlaylistSummary(
    val id: String,
    val title: String,
    val author: String?,
    val trackCount: Int?,
    val thumbnailUrl: String?,
)

data class SearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
)
