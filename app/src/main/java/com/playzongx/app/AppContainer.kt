package com.playzongx.app

import android.content.Context
import com.playzongx.data.network.InnerTubeClient
import com.playzongx.data.network.NewPipeDownloader
import com.playzongx.data.network.NewPipeStreamResolver
import com.playzongx.data.repo.InnerTubeMusicRepository
import com.playzongx.data.repo.MusicRepository
import com.playzongx.player.StreamResolver
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe

/** Manual dependency holder owned by the application, shared for the whole process. */
class AppContainer(context: Context) {

    /** Single shared client so connections can be reused across requests. */
    val httpClient: OkHttpClient = OkHttpClient()

    val musicRepository: MusicRepository = InnerTubeMusicRepository(InnerTubeClient(httpClient))

    /** NewPipe must be given its transport before any extractor is created. */
    val streamResolver: StreamResolver by lazy {
        NewPipe.init(NewPipeDownloader(httpClient))
        NewPipeStreamResolver()
    }
}
