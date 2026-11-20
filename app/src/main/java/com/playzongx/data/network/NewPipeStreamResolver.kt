package com.playzongx.data.network

import com.playzongx.player.AudioCandidate
import com.playzongx.player.ResolvedStream
import com.playzongx.player.StreamResolver
import com.playzongx.player.selectBestAudioStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.Stream
import java.io.IOException

/** Resolves audio streams with NewPipeExtractor, always off the main thread. */
class NewPipeStreamResolver(
    private val service: StreamingService = ServiceList.YouTube,
) : StreamResolver {

    override suspend fun resolveAudio(videoId: String): Result<ResolvedStream> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(resolve(videoId))
            } catch (error: IOException) {
                Result.failure(MusicError.Network(error))
            } catch (error: ExtractionException) {
                Result.failure(unavailable(error))
            } catch (error: MusicError) {
                Result.failure(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // YouTube markup changes can make the extractor throw unchecked
                // errors; keep them inside the Result contract so callers can
                // show an actionable message instead of crashing.
                Result.failure(MusicError.Unavailable().apply { addSuppressed(error) })
            }
        }

    private fun resolve(videoId: String): ResolvedStream {
        val extractor = service.getStreamExtractor("https://www.youtube.com/watch?v=$videoId")
        extractor.fetchPage()

        // Keep each stream next to its candidate so the chosen candidate can
        // still supply the itag (which AudioCandidate deliberately omits).
        val pairs = extractor.audioStreams.map { stream ->
            val candidate = AudioCandidate(
                url = stream.content,
                mimeType = stream.format?.mimeType,
                bitrateKbps = stream.averageBitrate.takeIf { it != AudioStream.UNKNOWN_BITRATE },
            )
            stream to candidate
        }
        val best = selectBestAudioStream(pairs.map { it.second }) ?: throw MusicError.Unavailable()
        val stream = pairs.first { it.second === best }.first

        return ResolvedStream(
            url = best.url,
            mimeType = best.mimeType ?: stream.format?.mimeType.orEmpty(),
            bitrateKbps = best.bitrateKbps,
            itag = stream.itag.takeIf { it != Stream.ITAG_NOT_AVAILABLE_OR_NOT_APPLICABLE },
        )
    }

    private fun unavailable(cause: ExtractionException): MusicError.Unavailable =
        MusicError.Unavailable().apply { addSuppressed(cause) }
}
