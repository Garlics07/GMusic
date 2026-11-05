package com.gmusic.app.data.download

import android.app.Notification
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.gmusic.app.R
import com.gmusic.app.data.local.GMusicDao
import com.gmusic.app.data.stream.StreamResolver
import com.gmusic.app.domain.model.Track
import com.gmusic.app.playback.queue.TrackMediaItemMapper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "gmusic_download_channel"

    @Provides
    @Singleton
    fun provideDownloadNotificationHelper(
        @ApplicationContext context: Context
    ): DownloadNotificationHelper {
        NotificationUtil.createNotificationChannel(
            context,
            DOWNLOAD_NOTIFICATION_CHANNEL_ID,
            R.string.library_downloads,
            0,
            NotificationUtil.IMPORTANCE_LOW
        )
        return DownloadNotificationHelper(context, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        simpleCache: SimpleCache,
        okHttpClient: OkHttpClient,
        streamResolver: StreamResolver,
        dao: GMusicDao
    ): DownloadManager {
        val dbProvider = StandaloneDatabaseProvider(context)
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)

        val resolvingFactory = ResolvingDataSource.Factory(okHttpFactory) { dataSpec: DataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == TrackMediaItemMapper.SCHEME_GMUSIC && uri.host == TrackMediaItemMapper.HOST_TRACK) {
                val videoId = uri.lastPathSegment
                    ?: throw IOException("VideoId mancante per download $uri")
                val resolvedUrl = runBlocking(Dispatchers.IO) {
                    streamResolver.resolveStreamUrl(videoId, forceRefresh = false)
                }
                val ua = when {
                    resolvedUrl.contains("c=IOS", ignoreCase = true) ->
                        "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
                    resolvedUrl.contains("c=ANDROID_VR", ignoreCase = true) ->
                        "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
                    else ->
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0"
                }
                dataSpec.buildUpon()
                    .setUri(Uri.parse(resolvedUrl))
                    .setHttpRequestHeaders(mapOf("User-Agent" to ua))
                    .setKey(videoId)
                    .build()
            } else {
                dataSpec
            }
        }

        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        return DownloadManager(
            context,
            dbProvider,
            simpleCache,
            resolvingFactory,
            Executors.newFixedThreadPool(3)
        ).apply {
            maxParallelDownloads = 2
            addListener(object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: Download,
                    finalException: Exception?
                ) {
                    val trackId = download.request.id
                    val completed = download.state == Download.STATE_COMPLETED
                    ioScope.launch {
                        dao.setTrackDownloaded(trackId, completed)
                    }
                }

                override fun onDownloadRemoved(
                    downloadManager: DownloadManager,
                    download: Download
                ) {
                    val trackId = download.request.id
                    ioScope.launch {
                        dao.setTrackDownloaded(trackId, false)
                    }
                }
            })
        }
    }
}

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class GMusicDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DownloadModule.DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.library_downloads,
    0
) {
    @Inject lateinit var injectedDownloadManager: DownloadManager
    @Inject lateinit var notificationHelper: DownloadNotificationHelper

    override fun getDownloadManager(): DownloadManager = injectedDownloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return notificationHelper.buildProgressNotification(
            this,
            android.R.drawable.stat_sys_download,
            null,
            null,
            downloads,
            notMetRequirements
        )
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 2001
        private const val JOB_ID = 2002
    }
}

@OptIn(UnstableApi::class)
@Singleton
class OfflineDownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: GMusicDao,
    private val downloadManager: DownloadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun downloadTrack(track: Track) {
        scope.launch {
            dao.upsertTrackPreservingFlags(track)
            val uri = Uri.parse("${TrackMediaItemMapper.SCHEME_GMUSIC}://${TrackMediaItemMapper.HOST_TRACK}/${track.id}")
            val request = DownloadRequest.Builder(track.id, uri)
                .setCustomCacheKey(track.id)
                .setData(track.title.toByteArray(Charsets.UTF_8))
                .build()
            try {
                DownloadService.sendAddDownload(
                    context,
                    GMusicDownloadService::class.java,
                    request,
                    /* foreground = */ false
                )
            } catch (e: Exception) {
                Log.w("GMusicAudio", "Avvio download diretto tramite DownloadManager: ${e.message}")
                downloadManager.addDownload(request)
                downloadManager.resumeDownloads()
            }
        }
    }

    fun downloadTracks(tracks: List<Track>) {
        tracks.forEach { downloadTrack(it) }
    }

    fun removeAllDownloads() {
        scope.launch {
            try {
                DownloadService.sendRemoveAllDownloads(
                    context,
                    GMusicDownloadService::class.java,
                    false
                )
            } catch (_: Exception) {
                downloadManager.removeAllDownloads()
            }
            dao.clearAllDownloadedFlags()
        }
    }
}
