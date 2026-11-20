package com.playzongx.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.StreamingService.ServiceInfo.MediaCapability
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabExtractor
import org.schabi.newpipe.extractor.comments.CommentsExtractor
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.linkhandler.LinkHandler
import org.schabi.newpipe.extractor.linkhandler.LinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandlerFactory
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandlerFactory
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.search.SearchExtractor
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.subscription.SubscriptionExtractor
import org.schabi.newpipe.extractor.suggestion.SuggestionExtractor

class NewPipeStreamResolverTest {

    @Test
    fun `unchecked extractor exception becomes unavailable failure`() = runTest {
        val resolver = NewPipeStreamResolver(service = serviceThrowing(RuntimeException("unexpected extractor failure")))

        val result = resolver.resolveAudio("broken-video")

        val error = result.exceptionOrNull()
        assertTrue("expected MusicError.Unavailable but was $error", error is MusicError.Unavailable)
        assertEquals("Content unavailable", error!!.message)
        assertTrue(
            "unexpected cause should be suppressed",
            error.suppressedExceptions.any { it.message == "unexpected extractor failure" },
        )
    }

    @Test
    fun `cancellation is not converted into an error result`() = runBlocking {
        val resolver = NewPipeStreamResolver(
            service = serviceThrowing(CancellationException("resolve cancelled")),
        )

        val outcome = runCatching { resolver.resolveAudio("broken-video") }

        assertTrue(
            "expected CancellationException but was ${outcome.exceptionOrNull()}",
            outcome.exceptionOrNull() is CancellationException,
        )
    }
}

/** Only [StreamingService.getStreamExtractor] is exercised by the resolver. */
private fun serviceThrowing(failure: RuntimeException): StreamingService = object : StreamingService(
    0,
    "Fake",
    listOf(MediaCapability.AUDIO),
) {
    override fun getBaseUrl(): String = error("unused")
    override fun getStreamLHFactory(): LinkHandlerFactory = error("unused")
    override fun getChannelLHFactory(): ListLinkHandlerFactory = error("unused")
    override fun getChannelTabLHFactory(): ListLinkHandlerFactory = error("unused")
    override fun getPlaylistLHFactory(): ListLinkHandlerFactory = error("unused")
    override fun getSearchQHFactory(): SearchQueryHandlerFactory = error("unused")
    override fun getCommentsLHFactory(): ListLinkHandlerFactory = error("unused")
    override fun getSearchExtractor(queryHandler: SearchQueryHandler): SearchExtractor = error("unused")
    override fun getSuggestionExtractor(): SuggestionExtractor = error("unused")
    override fun getSubscriptionExtractor(): SubscriptionExtractor = error("unused")
    override fun getKioskList(): KioskList = error("unused")
    override fun getChannelExtractor(linkHandler: ListLinkHandler): ChannelExtractor = error("unused")
    override fun getChannelTabExtractor(linkHandler: ListLinkHandler): ChannelTabExtractor = error("unused")
    override fun getPlaylistExtractor(linkHandler: ListLinkHandler): PlaylistExtractor = error("unused")
    override fun getStreamExtractor(linkHandler: LinkHandler): StreamExtractor = error("unused")
    override fun getCommentsExtractor(linkHandler: ListLinkHandler): CommentsExtractor = error("unused")

    override fun getStreamExtractor(url: String): StreamExtractor = throw failure
}
