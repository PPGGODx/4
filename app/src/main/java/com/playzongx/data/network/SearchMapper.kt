package com.playzongx.data.network

import com.playzongx.core.parseDurationText
import com.playzongx.domain.AlbumSummary
import com.playzongx.domain.ArtistSummary
import com.playzongx.domain.PlaylistSummary
import com.playzongx.domain.SearchResult
import com.playzongx.domain.Track
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Converts the InnerTube search response into domain models.
 *
 * Every lookup is defensive: missing, renamed, or unexpected fields are
 * skipped rather than throwing. Rows without both an id and a title are
 * dropped, as are renderers we do not understand, so a partial or changed
 * response still yields the results it can.
 *
 * Subtitle metadata is read run by run rather than from the flattened text.
 * InnerTube prefixes subtitles with an unlinked result-type label
 * (`"Song"`, `"Single"`, `"Album"`, `"Playlist"`, localized under `hl`), so
 * the artist/author is identified by its browse endpoint (UC/MPLA) first and
 * only falls back to skipping a leading type-label run.
 */
object SearchMapper {

    fun parse(root: JsonObject): SearchResult {
        val tracks = mutableListOf<Track>()
        val albums = mutableListOf<AlbumSummary>()
        val artists = mutableListOf<ArtistSummary>()
        val playlists = mutableListOf<PlaylistSummary>()

        val sections = root.obj("contents")
            ?.obj("sectionListRenderer")
            ?.array("contents")
            .orEmpty()

        for (sectionElement in sections) {
            val section = sectionElement as? JsonObject ?: continue

            section.obj("musicShelfRenderer")?.let { shelf ->
                for (rowElement in shelf.array("contents").orEmpty()) {
                    val row = rowElement as? JsonObject ?: continue
                    row.obj("musicResponsiveListItemRenderer")?.let { renderer ->
                        addResponsiveListItem(renderer, tracks, albums, artists, playlists)
                    }
                    row.obj("musicTwoRowItemRenderer")?.let { renderer ->
                        addTwoRowItem(renderer, tracks, albums, artists, playlists)
                    }
                    row.obj("musicCardShelfRenderer")?.let { renderer ->
                        addCardShelf(renderer, albums, artists, playlists)
                    }
                }
            }

            section.obj("musicCardShelfRenderer")?.let { renderer ->
                addCardShelf(renderer, albums, artists, playlists)
            }
            section.obj("musicResponsiveListItemRenderer")?.let { renderer ->
                addResponsiveListItem(renderer, tracks, albums, artists, playlists)
            }
            section.obj("musicTwoRowItemRenderer")?.let { renderer ->
                addTwoRowItem(renderer, tracks, albums, artists, playlists)
            }
        }

        return SearchResult(
            tracks = tracks.distinctBy { it.id },
            albums = albums.distinctBy { it.id },
            artists = artists.distinctBy { it.id },
            playlists = playlists.distinctBy { it.id },
        )
    }

    private fun addResponsiveListItem(
        item: JsonObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>,
    ) {
        val title = item.runsText(0).trim()
        if (title.isEmpty()) return

        val subtitleRuns = item.flexColumnRuns(1)

        val videoId = item.videoId()
        if (videoId != null) {
            val artist = subtitleRuns.artistOrAuthor().orEmpty()
            tracks += Track(
                id = videoId,
                title = title,
                artists = artist,
                album = subtitleRuns.albumAfter(artist),
                durationMs = item.durationFromFlexColumns(),
                thumbnailUrl = item.bestThumbnail(),
            )
            return
        }

        val browseId = item.browseId() ?: return
        val name = subtitleRuns.artistOrAuthor()
        when {
            browseId.startsWith("VL") -> playlists += PlaylistSummary(
                id = browseId,
                title = title,
                author = name,
                trackCount = subtitleRuns.trackCount(name),
                thumbnailUrl = item.bestThumbnail(),
            )

            browseId.startsWith("MPRE") -> albums += AlbumSummary(
                id = browseId,
                title = title,
                artist = name,
                thumbnailUrl = item.bestThumbnail(),
            )

            browseId.startsWith("UC") || browseId.startsWith("MPLA") -> artists += ArtistSummary(
                id = browseId,
                name = title,
                thumbnailUrl = item.bestThumbnail(),
            )
        }
    }

    private fun addTwoRowItem(
        item: JsonObject,
        tracks: MutableList<Track>,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>,
    ) {
        val title = item.obj("title")?.textValue()?.trim().orEmpty()
        if (title.isEmpty()) return

        val subtitleRuns = item.textRuns("subtitle")

        val videoId = item.videoId()
        if (videoId != null) {
            val artist = subtitleRuns.artistOrAuthor().orEmpty()
            tracks += Track(
                id = videoId,
                title = title,
                artists = artist,
                album = subtitleRuns.albumAfter(artist),
                durationMs = subtitleRuns.duration(),
                thumbnailUrl = item.bestThumbnail(),
            )
            return
        }

        val browseId = item.browseId() ?: return
        val name = subtitleRuns.artistOrAuthor()
        when {
            browseId.startsWith("VL") -> playlists += PlaylistSummary(
                id = browseId,
                title = title,
                author = name,
                trackCount = subtitleRuns.trackCount(name),
                thumbnailUrl = item.bestThumbnail(),
            )

            browseId.startsWith("MPRE") -> albums += AlbumSummary(
                id = browseId,
                title = title,
                artist = name,
                thumbnailUrl = item.bestThumbnail(),
            )

            browseId.startsWith("UC") || browseId.startsWith("MPLA") -> artists += ArtistSummary(
                id = browseId,
                name = title,
                thumbnailUrl = item.bestThumbnail(),
            )
        }
    }

    private fun addCardShelf(
        card: JsonObject,
        albums: MutableList<AlbumSummary>,
        artists: MutableList<ArtistSummary>,
        playlists: MutableList<PlaylistSummary>,
    ) {
        val header = card.obj("header")?.obj("musicCardShelfRenderer")
        val title = (card.obj("title") ?: header?.obj("title"))
            ?.textValue()
            ?.trim()
            .orEmpty()
        if (title.isEmpty()) return

        val subtitleRuns = (card.obj("subtitle") ?: header?.obj("subtitle"))
            ?.runs()
            .orEmpty()

        val browseId = card.browseId() ?: return
        val name = subtitleRuns.artistOrAuthor()
        when {
            browseId.startsWith("VL") -> playlists += PlaylistSummary(
                id = browseId,
                title = title,
                author = name,
                trackCount = subtitleRuns.trackCount(name),
                thumbnailUrl = card.bestThumbnail(),
            )

            browseId.startsWith("MPRE") -> albums += AlbumSummary(
                id = browseId,
                title = title,
                artist = name,
                thumbnailUrl = card.bestThumbnail(),
            )

            browseId.startsWith("UC") || browseId.startsWith("MPLA") -> artists += ArtistSummary(
                id = browseId,
                name = title,
                thumbnailUrl = card.bestThumbnail(),
            )
        }
    }

    // ---- Run-based metadata extraction ----

    private data class TextRun(val text: String, val browseId: String?)

    /**
     * Picks the artist/author. Runs linked to a channel/artist browse page win;
     * otherwise the first non-separator run is used after dropping any leading
     * unlinked result-type label.
     */
    private fun List<TextRun>.artistOrAuthor(): String? {
        val linked = filter { it.isLinkedArtist() }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (linked.isNotEmpty()) return linked.joinToString(", ")

        return plainTexts()
            .dropWhile { it.isTypeLabel() }
            .firstOrNull()
    }

    private fun TextRun.isLinkedArtist(): Boolean {
        val id = browseId ?: return false
        return id.startsWith("UC") || id.startsWith("MPLA")
    }

    /** First non-duration text after the artist run, i.e. the album name. */
    private fun List<TextRun>.albumAfter(artist: String?): String? {
        val texts = plainTexts()
        val artistIndex = if (artist.isNullOrBlank()) -1 else texts.indexOfFirst { it == artist }
        if (artistIndex < 0) return null
        return texts.drop(artistIndex + 1)
            .firstOrNull { parseDurationText(it) == null && !it.isTypeLabel() }
    }

    private fun List<TextRun>.duration(): Long? =
        firstNotNullOfOrNull { parseDurationText(it.text) }

    /**
     * Finds the track count from the run after the author, extracting the
     * numeric part without depending on an English "songs" suffix. Falls back
     * to the first purely numeric run.
     */
    private fun List<TextRun>.trackCount(author: String?): Int? {
        val texts = plainTexts()
        val authorIndex = if (author.isNullOrBlank()) {
            -1
        } else {
            texts.indexOfFirst { run ->
                run == author || (!run.isTypeLabel() && author.contains(run))
            }
        }
        val afterAuthor = if (authorIndex >= 0) texts.drop(authorIndex + 1) else emptyList()
        return afterAuthor.firstNotNullOfOrNull { it.firstNumber() }
            ?: texts.firstNotNullOfOrNull { it.toIntOrNull() }
    }

    private fun List<TextRun>.plainTexts(): List<String> =
        map { it.text.trim() }
            .filter { it.isNotEmpty() && !it.isSeparator() }

    private fun String.firstNumber(): Int? =
        Regex("""(\d[\d,]*)""").find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toIntOrNull()

    private fun String.isSeparator(): Boolean = trim() == "•" || trim() == "·"

    private fun String.isTypeLabel(): Boolean = trim().lowercase() in TYPE_LABELS

    private val TYPE_LABELS = setOf(
        "song", "single", "album", "playlist", "video", "ep", "artist", "music video",
        "เพลง", "ซิงเกิล", "อัลบั้ม", "เพลย์ลิสต์", "วิดีโอ", "ศิลปิน", "มิวสิกวิดีโอ",
    )

    // ---- JSON helpers ----

    /** Joins the runs of the `flexColumns[index]` text object. */
    private fun JsonObject.runsText(index: Int): String =
        flexColumnRuns(index).joinToString("") { it.text }

    private fun JsonObject.flexColumnRuns(index: Int): List<TextRun> =
        array("flexColumns")
            ?.getOrNull(index)
            ?.let { it as? JsonObject }
            ?.obj("musicResponsiveListItemFlexColumnRenderer")
            ?.obj("text")
            ?.runs()
            .orEmpty()

    private fun JsonObject.textRuns(key: String): List<TextRun> =
        obj(key)?.runs().orEmpty()

    private fun JsonObject.runs(): List<TextRun> =
        array("runs")
            ?.mapNotNull { element ->
                val run = element as? JsonObject ?: return@mapNotNull null
                val text = run.nonBlank("text") ?: return@mapNotNull null
                TextRun(text = text, browseId = run.browseId())
            }
            .orEmpty()

    /** Reads the first duration found, preferring individual runs over joined text. */
    private fun JsonObject.durationFromFlexColumns(): Long? {
        val columns = array("flexColumns") ?: return null
        for (column in columns) {
            val text = (column as? JsonObject)
                ?.obj("musicResponsiveListItemFlexColumnRenderer")
                ?.obj("text")
                ?: continue
            text.runs().firstNotNullOfOrNull { parseDurationText(it.text) }?.let { return it }
            parseDurationText(text.textValue())?.let { return it }
        }
        return null
    }

    private fun JsonObject.videoId(): String? =
        obj("playlistItemData")?.nonBlank("videoId")
            ?: obj("navigationEndpoint")?.obj("watchEndpoint")?.nonBlank("videoId")

    private fun JsonObject.browseId(): String? {
        fun JsonObject.browseEndpointId(): String? =
            obj("browseEndpoint")?.nonBlank("browseId")

        return obj("navigationEndpoint")?.browseEndpointId()
            ?: obj("onTap")?.obj("navigationEndpoint")?.browseEndpointId()
            ?: browseEndpointId()
    }

    /** Returns the last thumbnail URL, which InnerTube orders smallest to largest. */
    private fun JsonObject.bestThumbnail(): String? {
        val thumbnails = thumbnailArray() ?: return null
        return thumbnails.asReversed().firstNotNullOfOrNull { element ->
            (element as? JsonObject)?.nonBlank("url")
        }
    }

    private fun JsonObject.thumbnailArray(): JsonArray? {
        obj("thumbnail")?.let { thumbnail ->
            val direct = thumbnail.obj("musicThumbnailRenderer")
                ?.obj("thumbnail")
                ?.array("thumbnails")
                ?: thumbnail.obj("croppedSquareThumbnailRenderer")
                    ?.obj("thumbnail")
                    ?.array("thumbnails")
                ?: thumbnail.array("thumbnails")
            if (direct != null) return direct
        }

        // musicTwoRowItemRenderer nests thumbnails one level deeper.
        return obj("thumbnailRenderer")
            ?.obj("musicThumbnailRenderer")
            ?.obj("thumbnail")
            ?.array("thumbnails")
    }

    /** Reads `simpleText` or joins `runs[].text` from a text object. */
    private fun JsonObject.textValue(): String? =
        this["simpleText"].asStringOrNull()
            ?: array("runs")?.mapNotNull { (it as? JsonObject)?.nonBlank("text") }?.joinToString("")

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.array(key: String): JsonArray? = this[key] as? JsonArray

    private fun JsonObject.nonBlank(key: String): String? =
        this[key].asStringOrNull()?.takeIf { it.isNotBlank() }

    private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull
}
