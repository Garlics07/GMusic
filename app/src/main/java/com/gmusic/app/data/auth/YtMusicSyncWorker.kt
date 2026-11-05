package com.gmusic.app.data.auth

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.gmusic.app.data.innertube.InnerTubeClient
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coda di sincronizzazione best-effort tramite WorkManager per sincronizzare
 * Preferiti (like/unlike) e Playlist (creazione, rinomina, eliminazione, aggiunta/rimozione brani)
 * con l'account YouTube Music quando l'utente è autenticato.
 */
class YtMusicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun innerTubeClient(): InnerTubeClient
        fun authCredentialStore(): AuthCredentialStore
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            SyncWorkerEntryPoint::class.java
        )
        if (!entryPoint.authCredentialStore().hasValidCookie()) {
            return Result.success()
        }

        val action = inputData.getString(KEY_ACTION) ?: return Result.success()
        val client = entryPoint.innerTubeClient()

        return try {
            val ok = when (action) {
                ACTION_LIKE -> {
                    val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.success()
                    val liked = inputData.getBoolean(KEY_LIKED, true)
                    client.syncLikeStatus(videoId, liked)
                }
                ACTION_CREATE_PLAYLIST -> {
                    val title = inputData.getString(KEY_TITLE) ?: return Result.success()
                    client.createRemotePlaylist(title) != null
                }
                ACTION_RENAME_PLAYLIST -> {
                    val remoteId = inputData.getString(KEY_PLAYLIST_ID) ?: return Result.success()
                    val title = inputData.getString(KEY_TITLE) ?: return Result.success()
                    client.renameRemotePlaylist(remoteId, title)
                }
                ACTION_DELETE_PLAYLIST -> {
                    val remoteId = inputData.getString(KEY_PLAYLIST_ID) ?: return Result.success()
                    client.deleteRemotePlaylist(remoteId)
                }
                ACTION_ADD_TRACK -> {
                    val remoteId = inputData.getString(KEY_PLAYLIST_ID) ?: return Result.success()
                    val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.success()
                    client.addTrackToRemotePlaylist(remoteId, videoId)
                }
                else -> true
            }
            if (ok) Result.success() else Result.retry()
        } catch (e: Exception) {
            Log.w("GMusicAudio", "Sync YT Music rimandato in coda retry: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACTION = "sync_action"
        const val KEY_VIDEO_ID = "video_id"
        const val KEY_PLAYLIST_ID = "playlist_id"
        const val KEY_TITLE = "title"
        const val KEY_LIKED = "liked"

        const val ACTION_LIKE = "like"
        const val ACTION_CREATE_PLAYLIST = "create_playlist"
        const val ACTION_RENAME_PLAYLIST = "rename_playlist"
        const val ACTION_DELETE_PLAYLIST = "delete_playlist"
        const val ACTION_ADD_TRACK = "add_track"
    }
}

@Singleton
class YtMusicSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authCredentialStore: AuthCredentialStore
) {
    fun enqueueLikeSync(videoId: String, liked: Boolean) {
        if (!authCredentialStore.hasValidCookie()) return
        enqueue(
            Data.Builder()
                .putString(YtMusicSyncWorker.KEY_ACTION, YtMusicSyncWorker.ACTION_LIKE)
                .putString(YtMusicSyncWorker.KEY_VIDEO_ID, videoId)
                .putBoolean(YtMusicSyncWorker.KEY_LIKED, liked)
                .build()
        )
    }

    fun enqueueCreatePlaylistSync(title: String) {
        if (!authCredentialStore.hasValidCookie()) return
        enqueue(
            Data.Builder()
                .putString(YtMusicSyncWorker.KEY_ACTION, YtMusicSyncWorker.ACTION_CREATE_PLAYLIST)
                .putString(YtMusicSyncWorker.KEY_TITLE, title)
                .build()
        )
    }

    private fun enqueue(data: Data) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val req = OneTimeWorkRequestBuilder<YtMusicSyncWorker>()
            .setConstraints(constraints)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(req)
    }
}
