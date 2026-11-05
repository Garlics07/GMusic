package com.gmusic.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.Track
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumTitle: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0L,
    val isLiked: Boolean = false,
    val likedTimestamp: Long? = null,
    val isDownloaded: Boolean = false,
    val localFilePath: String? = null
) {
    fun toDomain(): Track = Track(
        id = id,
        title = title,
        artistName = artistName,
        artistId = artistId,
        albumTitle = albumTitle,
        albumId = albumId,
        thumbnailUrl = thumbnailUrl,
        durationSeconds = durationSeconds,
        isLiked = isLiked,
        isDownloaded = isDownloaded
    )

    companion object {
        fun fromDomain(track: Track): TrackEntity = TrackEntity(
            id = track.id,
            title = track.title,
            artistName = track.artistName,
            artistId = track.artistId,
            albumTitle = track.albumTitle,
            albumId = track.albumId,
            thumbnailUrl = track.thumbnailUrl,
            durationSeconds = track.durationSeconds,
            isLiked = track.isLiked,
            isDownloaded = track.isDownloaded
        )
    }
}

@Entity(
    tableName = "play_history",
    indices = [Index(value = ["trackId"]), Index(value = ["playedAt"])]
)
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val trackId: String,
    val playedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stream_url_cache")
data class StreamUrlCacheEntity(
    @PrimaryKey val videoId: String,
    val streamUrl: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val expiresAtMillis: Long,
    val resolverSource: String
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val remoteYtPlaylistId: String? = null
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index(value = ["playlistId"]), Index(value = ["trackId"])]
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_albums")
data class SavedAlbumEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val year: String? = null,
    val thumbnailUrl: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): AlbumSummary = AlbumSummary(
        id = id,
        title = title,
        artistName = artistName,
        artistId = artistId,
        year = year,
        thumbnailUrl = thumbnailUrl
    )
}

@Entity(tableName = "saved_artists")
data class SavedArtistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val subscribersText: String? = null,
    val thumbnailUrl: String,
    val savedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ArtistSummary = ArtistSummary(
        id = id,
        name = name,
        subscribersText = subscribersText,
        thumbnailUrl = thumbnailUrl
    )
}

@Entity(tableName = "search_history")
data class SearchHistoryEntity(
    @PrimaryKey val query: String,
    val searchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "lyrics_cache")
data class LyricsCacheEntity(
    @PrimaryKey val trackId: String,
    val syncedLrc: String?,
    val plainLyrics: String?,
    val hasLyrics: Boolean,
    val cachedAt: Long = System.currentTimeMillis()
)

@Dao
interface GMusicDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackIgnore(track: TrackEntity): Long

    @Query(
        """
        UPDATE tracks SET 
            title = CASE WHEN :title != 'Brano sconosciuto' AND :title != '' THEN :title ELSE title END,
            artistName = CASE WHEN :artistName != 'Artista sconosciuto' AND :artistName != 'Artista' AND :artistName != '' THEN :artistName ELSE artistName END,
            artistId = COALESCE(:artistId, artistId),
            albumTitle = COALESCE(:albumTitle, albumTitle),
            albumId = COALESCE(:albumId, albumId),
            thumbnailUrl = CASE WHEN :thumbnailUrl != '' THEN :thumbnailUrl ELSE thumbnailUrl END,
            durationSeconds = CASE WHEN :durationSeconds > 0 THEN :durationSeconds ELSE durationSeconds END
        WHERE id = :id
        """
    )
    suspend fun updateTrackMetadata(
        id: String,
        title: String,
        artistName: String,
        artistId: String?,
        albumTitle: String?,
        albumId: String?,
        thumbnailUrl: String,
        durationSeconds: Long
    )

    @Query("SELECT * FROM tracks WHERE title = 'Brano sconosciuto' OR artistName = 'Artista sconosciuto' OR artistName = 'Artista'")
    suspend fun getTracksWithPlaceholderMetadata(): List<TrackEntity>

    @Query("DELETE FROM play_history WHERE trackId = :trackId")
    suspend fun deleteHistoryByTrackId(trackId: String)

    @Query("DELETE FROM play_history")
    suspend fun clearPlayHistory()

    @Query("DELETE FROM tracks WHERE id = :trackId AND isLiked = 0 AND isDownloaded = 0")
    suspend fun deleteOrphanTrackById(trackId: String)

    @Transaction
    suspend fun upsertTrackPreservingFlags(track: Track) {
        val rowId = insertTrackIgnore(TrackEntity.fromDomain(track))
        if (rowId == -1L) {
            updateTrackMetadata(
                id = track.id,
                title = track.title,
                artistName = track.artistName,
                artistId = track.artistId,
                albumTitle = track.albumTitle,
                albumId = track.albumId,
                thumbnailUrl = track.thumbnailUrl,
                durationSeconds = track.durationSeconds
            )
        }
    }

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    fun observeTrackById(id: String): Flow<TrackEntity?>

    @Query("UPDATE tracks SET isLiked = :liked, likedTimestamp = :timestamp WHERE id = :trackId")
    suspend fun setTrackLiked(trackId: String, liked: Boolean, timestamp: Long?)

    @Query("SELECT * FROM tracks WHERE isLiked = 1 ORDER BY likedTimestamp DESC")
    fun observeLikedTracks(): Flow<List<TrackEntity>>

    @Query("UPDATE tracks SET isDownloaded = :downloaded WHERE id = :trackId")
    suspend fun setTrackDownloaded(trackId: String, downloaded: Boolean)

    @Query("UPDATE tracks SET isDownloaded = 0")
    suspend fun clearAllDownloadedFlags()

    @Query("SELECT * FROM tracks WHERE isDownloaded = 1 ORDER BY title ASC")
    fun observeDownloadedTracks(): Flow<List<TrackEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayHistory(entry: PlayHistoryEntity)

    @Query("SELECT trackId FROM play_history WHERE playedAt >= :sinceTimestamp ORDER BY playedAt DESC")
    suspend fun getRecentPlayedTrackIds(sinceTimestamp: Long): List<String>

    @Transaction
    suspend fun syncRemotePlayHistory(remoteTracks: List<Track>, baseTime: Long) {
        val threshold = baseTime - (15 * 60 * 1000L)
        val recentLocalIds = getRecentPlayedTrackIds(threshold)

        clearPlayHistory()

        // Reinserisci gli ascolti locali degli ultimi 15 minuti in cima
        recentLocalIds.distinct().forEachIndexed { idx, trackId ->
            insertPlayHistory(
                PlayHistoryEntity(
                    trackId = trackId,
                    playedAt = baseTime + 10_000L - (idx * 500L)
                )
            )
        }

        // Inserisci i brani della cronologia ufficiale di YouTube Music in ordine
        remoteTracks.forEachIndexed { index, track ->
            upsertTrackPreservingFlags(track)
            insertPlayHistory(
                PlayHistoryEntity(
                    trackId = track.id,
                    playedAt = baseTime - (index * 1000L)
                )
            )
        }
    }

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN (
            SELECT trackId, MAX(playedAt) as maxPlayedAt
            FROM play_history
            GROUP BY trackId
            ORDER BY maxPlayedAt DESC
            LIMIT :limit
        ) h ON t.id = h.trackId
        ORDER BY h.maxPlayedAt DESC
        """
    )
    fun observeRecentDistinctTracks(limit: Int = 20): Flow<List<TrackEntity>>

    // Playlists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET title = :newTitle WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newTitle: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylistById(playlistId: String)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun deleteAllTracksFromPlaylist(playlistId: String)

    @Transaction
    suspend fun deletePlaylistComplete(playlistId: String) {
        deleteAllTracksFromPlaylist(playlistId)
        deletePlaylistById(playlistId)
    }

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    fun observePlaylistById(playlistId: String): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    fun observePlaylistTracks(playlistId: String): Flow<List<TrackEntity>>

    @Query(
        """
        SELECT t.* FROM tracks t
        INNER JOIN playlist_tracks pt ON t.id = pt.trackId
        WHERE pt.playlistId = :playlistId
        ORDER BY pt.position ASC
        """
    )
    suspend fun getPlaylistTracksImmediate(playlistId: String): List<TrackEntity>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun getNextPlaylistPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistTrackCrossRef(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)

    @Transaction
    suspend fun replacePlaylistTrackOrder(playlistId: String, orderedTrackIds: List<String>) {
        deleteAllTracksFromPlaylist(playlistId)
        orderedTrackIds.forEachIndexed { index, trackId ->
            insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = trackId,
                    position = index
                )
            )
        }
    }

    // Saved Albums
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedAlbum(album: SavedAlbumEntity)

    @Query("DELETE FROM saved_albums WHERE id = :albumId")
    suspend fun deleteSavedAlbum(albumId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM saved_albums WHERE id = :albumId)")
    fun observeIsAlbumSaved(albumId: String): Flow<Boolean>

    @Query("SELECT * FROM saved_albums ORDER BY savedAt DESC")
    fun observeSavedAlbums(): Flow<List<SavedAlbumEntity>>

    // Saved Artists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedArtist(artist: SavedArtistEntity)

    @Query("DELETE FROM saved_artists WHERE id = :artistId")
    suspend fun deleteSavedArtist(artistId: String)

    @Query("DELETE FROM saved_artists")
    suspend fun clearSavedArtists()

    @Query("SELECT EXISTS(SELECT 1 FROM saved_artists WHERE id = :artistId)")
    fun observeIsArtistSaved(artistId: String): Flow<Boolean>

    @Query("SELECT * FROM saved_artists ORDER BY savedAt DESC")
    fun observeSavedArtists(): Flow<List<SavedArtistEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM play_history WHERE trackId = :trackId)")
    suspend fun hasPlayHistoryForTrack(trackId: String): Boolean

    // Stream cache
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreamCache(cache: StreamUrlCacheEntity)

    @Query("SELECT * FROM stream_url_cache WHERE videoId = :videoId AND expiresAtMillis > :nowMillis LIMIT 1")
    suspend fun getValidStreamCache(videoId: String, nowMillis: Long = System.currentTimeMillis()): StreamUrlCacheEntity?

    @Query("DELETE FROM stream_url_cache WHERE videoId = :videoId")
    suspend fun invalidateStreamCache(videoId: String)

    // Search history
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchHistory(item: SearchHistoryEntity)

    @Query("SELECT * FROM search_history ORDER BY searchedAt DESC LIMIT 15")
    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    // Lyrics cache
    @Query("SELECT * FROM lyrics_cache WHERE trackId = :trackId LIMIT 1")
    suspend fun getLyricsCache(trackId: String): LyricsCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLyricsCache(cache: LyricsCacheEntity)
}

@Database(
    entities = [
        TrackEntity::class,
        PlayHistoryEntity::class,
        StreamUrlCacheEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        SavedAlbumEntity::class,
        SavedArtistEntity::class,
        SearchHistoryEntity::class,
        LyricsCacheEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class GMusicDatabase : RoomDatabase() {
    abstract fun dao(): GMusicDao
}
