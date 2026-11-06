package com.gmusic.app.data.repository

import android.util.Log
import com.gmusic.app.data.auth.AuthCredentialStore
import com.gmusic.app.data.auth.YtMusicSyncScheduler
import com.gmusic.app.data.innertube.InnerTubeClient
import com.gmusic.app.data.local.GMusicDao
import com.gmusic.app.data.local.PlayHistoryEntity
import com.gmusic.app.data.local.PlaylistEntity
import com.gmusic.app.data.local.PlaylistTrackCrossRef
import com.gmusic.app.data.local.SavedAlbumEntity
import com.gmusic.app.data.local.SavedArtistEntity
import com.gmusic.app.data.local.SearchHistoryEntity
import com.gmusic.app.data.stream.NewPipeOkHttpDownloader
import com.gmusic.app.domain.model.AlbumDetail
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.ArtistDetail
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.HomeItemType
import com.gmusic.app.domain.model.HomeSection
import com.gmusic.app.domain.model.HomeShelfItem
import com.gmusic.app.domain.model.MoodGenreChip
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import com.gmusic.app.domain.model.toSongShelfItem
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class SearchFullResult(
    val songs: List<Track> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList()
)

@Singleton
class MusicRepository @Inject constructor(
    private val dao: GMusicDao,
    private val innerTubeClient: InnerTubeClient,
    private val newPipeDownloader: NewPipeOkHttpDownloader,
    private val authCredentialStore: AuthCredentialStore,
    private val syncScheduler: YtMusicSyncScheduler
) {
    private val repoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _remotePlaylists = MutableStateFlow<List<PlaylistSummary>>(emptyList())
    val remotePlaylists: StateFlow<List<PlaylistSummary>> = _remotePlaylists.asStateFlow()

    private val _accountAvatarUrl = MutableStateFlow<String?>(null)
    val accountAvatarUrl: StateFlow<String?> = _accountAvatarUrl.asStateFlow()

    private val _accountName = MutableStateFlow<String?>(null)
    val accountName: StateFlow<String?> = _accountName.asStateFlow()

    private val artistDiscographyCache = ConcurrentHashMap<String, List<Track>>()

    val isLoggedIn: StateFlow<Boolean> = authCredentialStore.isLoggedIn

    init {
        repoScope.launch {
            repairPlaceholderTracksInDb()
        }
        repoScope.launch {
            authCredentialStore.isLoggedIn.collectLatest { loggedIn ->
                if (loggedIn) {
                    syncRemoteLibraryIfLoggedIn()
                } else {
                    _remotePlaylists.value = emptyList()
                    _accountAvatarUrl.value = null
                    _accountName.value = null
                }
            }
        }
    }

    private suspend fun repairPlaceholderTracksInDb() = withContext(Dispatchers.IO) {
        try {
            val placeholders = dao.getTracksWithPlaceholderMetadata()
            for (entity in placeholders) {
                val repaired = innerTubeClient.fetchTrackMetadata(entity.id)
                    ?: innerTubeClient.searchSongs(entity.id).firstOrNull { it.id == entity.id }
                if (repaired != null && repaired.title != "Brano sconosciuto") {
                    dao.upsertTrackPreservingFlags(repaired)
                } else if (entity.title == "Brano sconosciuto") {
                    dao.deleteHistoryByTrackId(entity.id)
                    dao.deleteOrphanTrackById(entity.id)
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Sincronizza Cronologia, Canzoni Preferite, Playlist, Album Preferiti, Artisti Preferiti e Avatar
     * dell'account YouTube Music quando l'utente è autenticato.
     */
    suspend fun syncRemoteLibraryIfLoggedIn() = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) {
            _remotePlaylists.value = emptyList()
            _accountAvatarUrl.value = null
            _accountName.value = null
            return@withContext
        }
        try {
            coroutineScope {
                val accountInfoDeferred = async { innerTubeClient.fetchAccountInfo() }
                val likesDeferred = async { innerTubeClient.fetchAccountLikedTracks() }
                val playlistsDeferred = async { innerTubeClient.fetchAccountPlaylists() }
                val historyDeferred = async { innerTubeClient.fetchAccountHistoryTracks() }
                val albumsDeferred = async { innerTubeClient.fetchAccountSavedAlbums() }
                val artistsDeferred = async { innerTubeClient.fetchAccountSavedArtists() }

                accountInfoDeferred.await().let { info ->
                    if (!info.photoUrl.isNullOrBlank()) _accountAvatarUrl.value = info.photoUrl
                    if (!info.name.isNullOrBlank()) _accountName.value = info.name
                }

                val remoteLikes = likesDeferred.await()
                val baseTime = System.currentTimeMillis()
                remoteLikes.forEachIndexed { index, track ->
                    dao.upsertTrackPreservingFlags(track)
                    val existing = dao.getTrackById(track.id)
                    if (existing == null || !existing.isLiked) {
                        dao.setTrackLiked(
                            trackId = track.id,
                            liked = true,
                            timestamp = baseTime - (index * 1000L)
                        )
                    }
                }

                val accountPlaylists = playlistsDeferred.await()
                _remotePlaylists.value = accountPlaylists

                val remoteHistory = historyDeferred.await()
                dao.syncRemotePlayHistory(remoteHistory, baseTime)

                val remoteAlbums = albumsDeferred.await()
                remoteAlbums.forEachIndexed { index, alb ->
                    dao.upsertSavedAlbum(
                        SavedAlbumEntity(
                            id = alb.id,
                            title = alb.title,
                            artistName = alb.artistName,
                            artistId = alb.artistId,
                            year = alb.year,
                            thumbnailUrl = alb.thumbnailUrl,
                            savedAt = baseTime - (index * 1000L)
                        )
                    )
                }

                val remoteArtists = artistsDeferred.await()
                dao.clearSavedArtists()
                remoteArtists.forEachIndexed { index, art ->
                    dao.upsertSavedArtist(
                        SavedArtistEntity(
                            id = art.id,
                            name = art.name,
                            subscribersText = art.subscribersText,
                            thumbnailUrl = art.thumbnailUrl,
                            savedAt = baseTime - (index * 1000L)
                        )
                    )
                }

                Log.i(
                    TAG,
                    "Sincronizzazione YouTube Music completata: ${remoteLikes.size} preferiti, ${accountPlaylists.size} playlist, ${remoteHistory.size} cronologia, ${remoteAlbums.size} album, ${remoteArtists.size} artisti"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Errore sincronizzazione libreria YouTube Music: ${e.message}")
        }
    }

    fun observeRecentHistory(limit: Int = 20): Flow<List<Track>> =
        dao.observeRecentDistinctTracks(limit).map { list -> list.map { it.toDomain() } }

    fun observeLikedTracks(): Flow<List<Track>> =
        dao.observeLikedTracks().map { list -> list.map { it.toDomain() } }

    fun observeDownloadedTracks(): Flow<List<Track>> =
        dao.observeDownloadedTracks().map { list -> list.map { it.toDomain() } }

    fun observeSavedAlbums(): Flow<List<AlbumSummary>> =
        dao.observeSavedAlbums().map { list -> list.map { it.toDomain() } }

    fun observeIsAlbumSaved(albumId: String): Flow<Boolean> =
        dao.observeIsAlbumSaved(albumId)

    fun observeSavedArtists(): Flow<List<ArtistSummary>> =
        dao.observeSavedArtists().map { list -> list.map { it.toDomain() } }

    fun observeIsArtistSaved(artistId: String): Flow<Boolean> =
        dao.observeIsArtistSaved(artistId)

    fun observeSearchHistory(): Flow<List<String>> =
        dao.observeSearchHistory().map { list -> list.map { it.query } }

    fun observeTrackById(trackId: String): Flow<Track?> =
        dao.observeTrackById(trackId).map { it?.toDomain() }

    /**
     * Risolve i metadati completi di un brano a partire dal suo ID (DB locale -> next/player/ricerca YouTube Music).
     */
    suspend fun resolveTrackById(trackId: String): Track? = withContext(Dispatchers.IO) {
        val local = dao.getTrackById(trackId)?.toDomain()
        if (local != null &&
            local.title != "Brano sconosciuto" &&
            local.title.isNotBlank() &&
            local.artistName != "Artista sconosciuto" &&
            local.artistName != "Artista" &&
            !local.albumTitle.isNullOrBlank()
        ) {
            return@withContext local
        }
        val fromMeta = innerTubeClient.fetchTrackMetadata(trackId)
        if (fromMeta != null) {
            dao.upsertTrackPreservingFlags(fromMeta)
            return@withContext dao.getTrackById(trackId)?.toDomain() ?: fromMeta
        }
        val fromSearch = innerTubeClient.searchSongs(trackId).firstOrNull { it.id == trackId }
        if (fromSearch != null) {
            dao.upsertTrackPreservingFlags(fromSearch)
            return@withContext dao.getTrackById(trackId)?.toDomain() ?: fromSearch
        }
        local?.takeIf { it.title != "Brano sconosciuto" && it.title.isNotBlank() }
    }

    /**
     * Osserva le playlist attive: se loggato con account Google restituisce ESCLUSIVAMENTE le playlist di YouTube Music;
     * se ospite (Guest) restituisce le playlist locali.
     */
    fun observeActivePlaylists(): Flow<List<PlaylistSummary>> =
        combine(isLoggedIn, remotePlaylists, observeLocalPlaylists()) { loggedIn, remote, local ->
            if (loggedIn) remote else local
        }

    /**
     * Osserva le playlist locali calcolando automaticamente la copertina (mosaico 2x2 delle prime 4 tracce).
     */
    fun observeLocalPlaylists(): Flow<List<PlaylistSummary>> =
        dao.observePlaylists().map { entities ->
            entities.map { entity ->
                val tracks = dao.getPlaylistTracksImmediate(entity.id)
                PlaylistSummary(
                    id = entity.id,
                    title = entity.title,
                    author = "GMusic",
                    trackCount = tracks.size,
                    thumbnailUrls = tracks.take(4).map { it.thumbnailUrl },
                    isLocal = true
                )
            }
        }

    fun observePlaylistDetail(playlistId: String): Flow<Pair<PlaylistSummary?, List<Track>>> =
        if (playlistId.startsWith("local_pl_")) {
            combine(
                dao.observePlaylistById(playlistId),
                dao.observePlaylistTracks(playlistId)
            ) { entity, trackEntities ->
                val domainTracks = trackEntities.map { it.toDomain() }
                val summary = entity?.let {
                    PlaylistSummary(
                        id = it.id,
                        title = it.title,
                        author = "GMusic",
                        trackCount = domainTracks.size,
                        thumbnailUrls = domainTracks.take(4).map { t -> t.thumbnailUrl },
                        isLocal = true
                    )
                }
                summary to domainTracks
            }
        } else {
            flow {
                val remoteSummary = _remotePlaylists.value.firstOrNull { it.id == playlistId }
                val detail = innerTubeClient.fetchAlbumOrPlaylistBrowse(playlistId, "Playlist")
                val tracks = detail?.tracks.orEmpty()
                val summary = remoteSummary ?: PlaylistSummary(
                    id = playlistId,
                    title = detail?.title ?: "Playlist",
                    author = detail?.artistName ?: "YouTube Music",
                    trackCount = tracks.size,
                    thumbnailUrls = tracks.take(4).map { it.thumbnailUrl },
                    isLocal = false
                )
                emit(summary to tracks)
            }
        }

    suspend fun clearPlayHistory() = withContext(Dispatchers.IO) {
        dao.clearPlayHistory()
    }

    suspend fun createPlaylist(title: String): String = withContext(Dispatchers.IO) {
        val clean = title.trim().ifEmpty { "Nuova playlist" }
        if (authCredentialStore.hasValidCookie()) {
            // Quando l'utente è autenticato, crea la playlist ESCLUSIVAMENTE su YouTube Music
            val remoteId = innerTubeClient.createRemotePlaylist(clean)
            val refreshed = innerTubeClient.fetchAccountPlaylists()
            if (refreshed.isNotEmpty()) {
                _remotePlaylists.value = refreshed
            }
            if (!remoteId.isNullOrBlank()) {
                return@withContext if (remoteId.startsWith("VL")) remoteId else "VL$remoteId"
            }
            return@withContext refreshed.firstOrNull()?.id ?: ""
        } else {
            val id = "local_pl_${UUID.randomUUID()}"
            dao.insertPlaylist(PlaylistEntity(id = id, title = clean))
            return@withContext id
        }
    }

    suspend fun renamePlaylist(playlistId: String, newTitle: String) = withContext(Dispatchers.IO) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return@withContext
        if (playlistId.startsWith("local_pl_")) {
            dao.renamePlaylist(playlistId, clean)
        } else if (authCredentialStore.hasValidCookie()) {
            innerTubeClient.renameRemotePlaylist(playlistId, clean)
            _remotePlaylists.value = innerTubeClient.fetchAccountPlaylists()
        }
    }

    suspend fun deletePlaylist(playlistId: String) = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("local_pl_")) {
            dao.deletePlaylistComplete(playlistId)
        } else if (authCredentialStore.hasValidCookie()) {
            innerTubeClient.deleteRemotePlaylist(playlistId)
            _remotePlaylists.value = innerTubeClient.fetchAccountPlaylists()
        }
    }

    suspend fun addTrackToPlaylist(playlistId: String, track: Track) = withContext(Dispatchers.IO) {
        dao.upsertTrackPreservingFlags(track)
        if (playlistId.startsWith("local_pl_")) {
            val nextPos = dao.getNextPlaylistPosition(playlistId)
            dao.insertPlaylistTrackCrossRef(
                PlaylistTrackCrossRef(
                    playlistId = playlistId,
                    trackId = track.id,
                    position = nextPos
                )
            )
        } else if (authCredentialStore.hasValidCookie()) {
            innerTubeClient.addTrackToRemotePlaylist(playlistId, track.id)
        }
    }

    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("local_pl_")) {
            dao.removeTrackFromPlaylist(playlistId, trackId)
        } else if (authCredentialStore.hasValidCookie()) {
            innerTubeClient.removeTrackFromRemotePlaylist(playlistId, trackId)
        }
    }

    suspend fun reorderPlaylistTracks(playlistId: String, orderedTracks: List<Track>) = withContext(Dispatchers.IO) {
        if (playlistId.startsWith("local_pl_")) {
            dao.replacePlaylistTrackOrder(playlistId, orderedTracks.map { it.id })
        }
    }

    suspend fun toggleSaveAlbum(album: AlbumSummary, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        if (currentlySaved) {
            dao.deleteSavedAlbum(album.id)
        } else {
            dao.upsertSavedAlbum(
                SavedAlbumEntity(
                    id = album.id,
                    title = album.title,
                    artistName = album.artistName,
                    artistId = album.artistId,
                    year = album.year,
                    thumbnailUrl = album.thumbnailUrl
                )
            )
        }
        if (authCredentialStore.hasValidCookie()) {
            // Sincronizza anche su YouTube Music se supportato
            repoScope.launch {
                syncRemoteLibraryIfLoggedIn()
            }
        }
    }

    suspend fun toggleSaveArtist(artist: ArtistSummary, currentlySaved: Boolean) = withContext(Dispatchers.IO) {
        val newSubscribe = !currentlySaved
        if (currentlySaved) {
            dao.deleteSavedArtist(artist.id)
        } else {
            dao.upsertSavedArtist(
                SavedArtistEntity(
                    id = artist.id,
                    name = artist.name,
                    subscribersText = artist.subscribersText,
                    thumbnailUrl = artist.thumbnailUrl
                )
            )
        }
        if (authCredentialStore.hasValidCookie() && artist.id.startsWith("UC")) {
            innerTubeClient.syncArtistSubscription(artist.id, newSubscribe)
        }
    }

    suspend fun recordTrackPlayed(track: Track) = withContext(Dispatchers.IO) {
        val enriched = if (
            track.title == "Brano sconosciuto" ||
            track.artistName == "Artista sconosciuto" ||
            track.artistName == "Artista" ||
            track.albumTitle.isNullOrBlank()
        ) {
            innerTubeClient.fetchTrackMetadata(track.id)?.copy(
                isLiked = track.isLiked,
                isDownloaded = track.isDownloaded
            ) ?: track
        } else {
            track
        }
        dao.upsertTrackPreservingFlags(enriched)
        dao.insertPlayHistory(PlayHistoryEntity(trackId = enriched.id))
        if (authCredentialStore.hasValidCookie()) {
            repoScope.launch {
                innerTubeClient.reportPlayback(enriched.id)
            }
        }
    }

    suspend fun toggleLike(track: Track): Boolean = withContext(Dispatchers.IO) {
        dao.upsertTrackPreservingFlags(track)
        val current = dao.getTrackById(track.id)?.isLiked ?: false
        val newLiked = !current
        dao.setTrackLiked(
            trackId = track.id,
            liked = newLiked,
            timestamp = if (newLiked) System.currentTimeMillis() else null
        )
        syncScheduler.enqueueLikeSync(track.id, newLiked)
        newLiked
    }

    suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isNotEmpty()) {
            dao.insertSearchHistory(SearchHistoryEntity(query = clean))
        }
    }

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        dao.clearSearchHistory()
    }

    /**
     * Infinite Autoplay: recupera fino a 10 brani dalla radio YT Music del brano corrente,
     * escludendo i brani già in coda o nella cronologia recente. Esegue su IO.
     */
    suspend fun fetchAutoplayRadioAddition(
        currentTrack: Track,
        existingQueueIds: Set<String>
    ): List<Track> = withContext(Dispatchers.IO) {
        val recentIds = dao.observeRecentDistinctTracks(30).first().map { it.id }.toSet()
        val excluded = existingQueueIds + recentIds + currentTrack.id
        val candidates = innerTubeClient.fetchRadioTracks(currentTrack.id).ifEmpty {
            searchSongs("${currentTrack.artistName} brani")
        }
        candidates.filter { it.id !in excluded }.distinctBy { it.id }.take(10)
    }

    /**
     * Genera i caroselli Home in stile Spotify:
     * a. "Fatto per te" (mix personali con sottotitoli artisti)
     * b. "Riproduzioni recenti" (carosello di album/playlist/artisti/brani ascoltati)
     * c. "Consigliati per te" (brani)
     * d. "I tuoi artisti preferiti" (copertine circolari)
     * e. "Nuove uscite per te"
     * f. Caroselli tematici per mood/genere ("Relax", "Allenamento", "Focus"...) con caricamento infinito paginato.
     * In assenza di rete, mostra solo caroselli costruiti da brani scaricati e cronologia locale senza errori.
     */
    suspend fun buildHomeFeedSections(page: Int = 0): List<HomeSection> = withContext(Dispatchers.IO) {
        val sections = mutableListOf<HomeSection>()
        val recentHistory = dao.observeRecentDistinctTracks(16).first().map { it.toDomain() }
        val likedTracks = dao.observeLikedTracks().first().map { it.toDomain() }
        val downloadedTracks = dao.observeDownloadedTracks().first().map { it.toDomain() }
        val activePlaylists = observeActivePlaylists().first()
        val savedAlbums = dao.observeSavedAlbums().first().map { it.toDomain() }
        val savedArtists = dao.observeSavedArtists().first().map { it.toDomain() }

        if (page == 0) {
            // Recupero dati in parallelo senza mai bloccare o sollevare eccezioni in caso di assenza rete
            val (feedTracks, exploreAlbums, radioSeed1, radioSeed2, ytmMixes) = coroutineScope {
                val feedDef = async {
                    try {
                        innerTubeClient.fetchHomeTracks().ifEmpty {
                            innerTubeClient.searchSongs("Top Brani Italia")
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val albumsDef = async {
                    try {
                        val (_, browseAlbums) = innerTubeClient.fetchItalyExploreData()
                        browseAlbums.ifEmpty {
                            innerTubeClient.searchAlbums("Nuove uscite Italia")
                        }
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val mixesDef = async {
                    try {
                        innerTubeClient.fetchYouTubeMusicMixes()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val seed1 = recentHistory.getOrNull(0) ?: likedTracks.getOrNull(0)
                val seed2 = recentHistory.getOrNull(1) ?: likedTracks.getOrNull(1)
                val radio1Def = async {
                    if (seed1 != null) {
                        try {
                            innerTubeClient.fetchRadioTracks(seed1.id).ifEmpty {
                                innerTubeClient.searchSongs(seed1.artistName)
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else emptyList()
                }
                val radio2Def = async {
                    if (seed2 != null) {
                        try {
                            innerTubeClient.fetchRadioTracks(seed2.id).ifEmpty {
                                innerTubeClient.searchSongs(seed2.artistName)
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    } else emptyList()
                }
                Quintuple(feedDef.await(), albumsDef.await(), radio1Def.await(), radio2Def.await(), mixesDef.await())
            }

            val isOfflineFallback = feedTracks.isEmpty() && radioSeed1.isEmpty() && exploreAlbums.isEmpty() && ytmMixes.isEmpty()

            if (isOfflineFallback) {
                // Modalità offline: mostra solo contenuti scaricati e cronologia locale
                if (downloadedTracks.isNotEmpty()) {
                    sections.add(
                        HomeSection(
                            id = "offline_downloads",
                            title = "Scaricati",
                            subtitle = "ASCOLTO OFFLINE",
                            isSongShelf = true,
                            items = downloadedTracks.map { it.toSongShelfItem() },
                            tracks = downloadedTracks
                        )
                    )
                }
                if (recentHistory.isNotEmpty()) {
                    sections.add(
                        HomeSection(
                            id = "offline_recent",
                            title = "Riproduzioni recenti",
                            isSongShelf = true,
                            items = recentHistory.map { it.toSongShelfItem() },
                            tracks = recentHistory
                        )
                    )
                }
                if (likedTracks.isNotEmpty()) {
                    sections.add(
                        HomeSection(
                            id = "offline_liked",
                            title = "Canzoni preferite",
                            isSongShelf = true,
                            items = likedTracks.take(15).map { it.toSongShelfItem() },
                            tracks = likedTracks.take(15)
                        )
                    )
                }
                return@withContext sections
            }

            // 1. "I tuoi mix" (6 mix da YouTube Music o personalizzati, nessun duplicato "Fatto per te")
            val mixPool1 = radioSeed1.ifEmpty { feedTracks.take(12) }
            val mixPool2 = radioSeed2.ifEmpty { feedTracks.drop(6).take(12) }
            val mixPool3 = (likedTracks + feedTracks).distinctBy { it.id }.take(12)
            val mixPool4 = (recentHistory + radioSeed1.reversed()).distinctBy { it.id }.take(12)
            val mixPool5 = feedTracks.takeLast(12)
            val mixPool6 = (radioSeed2 + likedTracks).distinctBy { it.id }.take(12)

            val backupPools = listOf(mixPool1, mixPool2, mixPool3, mixPool4, mixPool5, mixPool6)
            val finalMixes = mutableListOf<HomeShelfItem>()
            finalMixes.addAll(ytmMixes)

            for (idx in finalMixes.size until 6) {
                val pool = backupPools.getOrElse(idx) { feedTracks.shuffled().take(12) }
                if (pool.isNotEmpty()) {
                    val artistNames = pool.map { it.artistName }.distinct().take(3).joinToString(", ")
                    val lead = pool.first()
                    finalMixes.add(
                        HomeShelfItem(
                            id = "mix_for_you_${idx + 1}",
                            title = "Mix ${idx + 1}",
                            subtitle = artistNames.ifBlank { "Playlist • GMusic" },
                            thumbnailUrl = lead.hdArtworkUrl,
                            type = HomeItemType.MIX,
                            track = lead,
                            tracks = pool
                        )
                    )
                }
            }

            if (finalMixes.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        id = "shelf_made_for_you",
                        title = "I tuoi mix",
                        subtitle = null,
                        isSongShelf = true,
                        items = finalMixes.take(6),
                        tracks = finalMixes.flatMap { it.tracks }.distinctBy { it.id }
                    )
                )
            }

            // 2. "Riproduzioni recenti" (ultimi 10 brani su mobile)
            if (recentHistory.isNotEmpty()) {
                val recentShelfItems = recentHistory.take(10).map { it.toSongShelfItem() }
                sections.add(
                    HomeSection(
                        id = "shelf_recent_carousel",
                        title = "Riproduzioni recenti",
                        isSongShelf = true,
                        items = recentShelfItems,
                        tracks = recentHistory.take(10)
                    )
                )
            }

            // 3. "Consigliati per te" (brani)
            val recommendedSongs = (radioSeed1 + feedTracks).distinctBy { it.id }.take(14)
            if (recommendedSongs.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        id = "shelf_recommended",
                        title = "Consigliati per te",
                        isSongShelf = true,
                        items = recommendedSongs.map { it.toSongShelfItem() },
                        tracks = recommendedSongs
                    )
                )
            }

            // 4. "I tuoi artisti preferiti" (copertine circolari degli artisti seguiti)
            val savedArtistItems = savedArtists.map { art ->
                HomeShelfItem(
                    id = art.id,
                    title = art.name,
                    subtitle = "Artista",
                    thumbnailUrl = art.thumbnailUrl,
                    type = HomeItemType.ARTIST
                )
            }.take(16)
            if (savedArtistItems.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        id = "shelf_favorite_artists",
                        title = "I tuoi artisti preferiti",
                        isSongShelf = false,
                        items = savedArtistItems
                    )
                )
            }

            // 5. "Le tue playlist"
            val playlistItems = activePlaylists.take(12).map { pl ->
                HomeShelfItem(
                    id = pl.id,
                    title = pl.title,
                    subtitle = "Playlist • ${pl.author}",
                    thumbnailUrl = pl.thumbnailUrls.firstOrNull() ?: "",
                    type = HomeItemType.PLAYLIST
                )
            }
            if (playlistItems.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        id = "shelf_playlists",
                        title = "Le tue playlist",
                        isSongShelf = false,
                        items = playlistItems
                    )
                )
            }

            // 6. "Nuove uscite"
            val newReleaseItems = if (exploreAlbums.isNotEmpty()) {
                exploreAlbums.take(12).map { alb ->
                    HomeShelfItem(
                        id = alb.id,
                        title = alb.title,
                        subtitle = "Album • ${alb.artistName}",
                        thumbnailUrl = alb.hdArtworkUrl,
                        type = HomeItemType.ALBUM
                    )
                }
            } else {
                feedTracks.takeLast(8).map { it.toSongShelfItem() }
            }
            if (newReleaseItems.isNotEmpty()) {
                sections.add(
                    HomeSection(
                        id = "shelf_new_releases",
                        title = "Nuove uscite",
                        isSongShelf = false,
                        items = newReleaseItems,
                        albums = exploreAlbums.take(12)
                    )
                )
            }

            // f. Caroselli tematici iniziali per mood/genere ("Relax", "Allenamento")
            val initialThemes = listOf(
                "Relax" to "Musica Relax Chill Italia",
                "Allenamento" to "Musica Allenamento Carica"
            )
            coroutineScope {
                initialThemes.map { (themeTitle, query) ->
                    async {
                        val tracks = try {
                            innerTubeClient.searchSongs(query).take(12)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (tracks.isNotEmpty()) {
                            HomeSection(
                                id = "shelf_theme_${themeTitle.lowercase()}",
                                title = themeTitle,
                                isSongShelf = true,
                                items = tracks.map { it.toSongShelfItem() },
                                tracks = tracks
                            )
                        } else null
                    }
                }.mapNotNull { it.await() }.forEach { sections.add(it) }
            }
        } else {
            // Paginazione scaffali tematici (singolo ciclo / Vol. 1 non indicato, senza volumi infiniti)
            val pagedThemes = listOf(
                "Focus" to "Lo-Fi Focus Studio Strumentale",
                "Pop Italiano" to "Hit Pop Italiano",
                "Rap & Urban" to "Rap Trap Italia Brani",
                "Energia" to "Musica Energia Motivazione",
                "Indie Italia" to "Indie Italiano Brani",
                "Anni 90 & 2000" to "Successi Anni 90 2000 Italia",
                "Elettronica & Dance" to "Dance Electronic Hits",
                "Rock Classico" to "Rock Leggende Brani",
                "Serata & Festa" to "Party Hits Italia",
                "Acustico & Unplugged" to "Brani Acustici Chitarra"
            )
            val startIdx = (page - 1) * 3
            if (startIdx >= pagedThemes.size) {
                return@withContext emptyList()
            }
            val selectedThemes = pagedThemes.drop(startIdx).take(3)
            coroutineScope {
                selectedThemes.mapIndexed { idx, (themeTitle, query) ->
                    async {
                        val tracks = try {
                            innerTubeClient.searchSongs(query).take(12)
                        } catch (_: Exception) {
                            emptyList()
                        }
                        if (tracks.isNotEmpty()) {
                            HomeSection(
                                id = "shelf_paged_${page}_${idx}_$themeTitle",
                                title = themeTitle,
                                isSongShelf = true,
                                items = tracks.map { it.toSongShelfItem() },
                                tracks = tracks
                            )
                        } else null
                    }
                }.mapNotNull { it.await() }.forEach { sections.add(it) }
            }
        }
        sections.filterNot { InnerTubeClient.isAudiobookOrPodcast(it.title) || InnerTubeClient.isAudiobookOrPodcast(it.subtitle) }
            .map { sec ->
                sec.copy(
                    items = sec.items.filterNot { InnerTubeClient.isAudiobookOrPodcast(it.title) || InnerTubeClient.isAudiobookOrPodcast(it.subtitle) },
                    tracks = sec.tracks.filterNot { InnerTubeClient.isAudiobookOrPodcast(it.title) || InnerTubeClient.isAudiobookOrPodcast(it.artistName) }
                )
            }
            .filter { it.items.isNotEmpty() || it.tracks.isNotEmpty() }
    }

    private fun List<Track>.GroupByArtistWithCover(): List<HomeShelfItem> {
        val seen = LinkedHashMap<String, HomeShelfItem>()
        for (track in this) {
            val cleanName = track.artistName.split(",", " feat.", "&").firstOrNull()?.trim() ?: track.artistName
            if (cleanName.isNotBlank() && cleanName.lowercase() !in seen) {
                seen[cleanName.lowercase()] = HomeShelfItem(
                    id = track.artistId ?: cleanName,
                    title = cleanName,
                    subtitle = "Artista",
                    thumbnailUrl = track.hdArtworkUrl,
                    type = HomeItemType.ARTIST
                )
            }
        }
        return seen.values.toList()
    }

    /**
     * Recupera i brani in tendenza da YouTube Music.
     */
    suspend fun fetchTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        try {
            val trending = innerTubeClient.fetchTrendingTracks()
            if (trending.isNotEmpty()) trending else searchSongs("Tendenze Italia")
        } catch (_: Exception) {
            searchSongs("Tendenze Italia")
        }
    }

    /**
     * Dati Esplora/Ricerca per Italia (IT): Tendenze e 8 generi principali.
     */
    suspend fun loadExploreItalyData(): Triple<List<Track>, List<AlbumSummary>, List<MoodGenreChip>> =
        withContext(Dispatchers.IO) {
            val trendingTracks = fetchTrendingTracks().take(30)
            val moods = listOf(
                MoodGenreChip("Pop Italia", "Pop Italiano", 0xFFE91E63),
                MoodGenreChip("Rock", "Rock Italiano", 0xFFFF5722),
                MoodGenreChip("Hip-Hop", "Hip Hop Rap Italiano", 0xFF9C27B0),
                MoodGenreChip("Indie", "Indie Italia", 0xFF00BCD4),
                MoodGenreChip("Elettronica", "Musica Elettronica Dance", 0xFF009688),
                MoodGenreChip("Jazz", "Jazz Italiano", 0xFF3F51B5),
                MoodGenreChip("R&B", "R&B Soul", 0xFFFF9800),
                MoodGenreChip("Relax", "Musica Relax Chill", 0xFF4CAF50)
            )
            Triple(trendingTracks, emptyList(), moods)
        }

    suspend fun getSearchSuggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        val itSuggestions = innerTubeClient.getSearchSuggestions(input)
        if (itSuggestions.isNotEmpty()) return@withContext itSuggestions
        try {
            newPipeDownloader.ensureInitialized()
            ServiceList.YouTube.suggestionExtractor.suggestionList(input).take(8)
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Cerca ESCLUSIVAMENTE brani musicali da YouTube Music (nessun video musicale o video generico).
     */
    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isEmpty()) return@withContext emptyList()

        try {
            val ytMusicSongs = innerTubeClient.searchSongs(clean)
            if (ytMusicSongs.isNotEmpty()) {
                return@withContext ytMusicSongs
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube searchSongs fallback per '$clean': ${e.message}")
        }

        try {
            newPipeDownloader.ensureInitialized()
            val extractor = ServiceList.YouTube.getSearchExtractor(
                clean,
                listOf(YoutubeMusicSearchFilter.MUSIC_SONGS),
                ""
            )
            extractor.fetchPage()
            extractor.initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { mapStreamInfoItemToTrack(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Ricerca brani YouTube Music fallita per '$clean'", e)
            emptyList()
        }
    }

    /**
     * Ricerca completa su YouTube Music (Brani, Artisti, Album, Playlist).
     */
    suspend fun searchAllCategories(query: String, filter: String = "all"): SearchFullResult =
        withContext(Dispatchers.IO) {
            val clean = query.trim()
            if (clean.isEmpty()) return@withContext SearchFullResult()

            coroutineScope {
                val songsDeferred = async {
                    if (filter == "all" || filter == "songs") searchSongs(clean) else emptyList()
                }
                val artistsDeferred = async {
                    if (filter == "all" || filter == "artists") innerTubeClient.searchArtists(clean) else emptyList()
                }
                val albumsDeferred = async {
                    if (filter == "all" || filter == "albums") innerTubeClient.searchAlbums(clean) else emptyList()
                }
                val playlistsDeferred = async {
                    if (filter == "all" || filter == "playlists") innerTubeClient.searchPlaylists(clean) else emptyList()
                }

                SearchFullResult(
                    songs = songsDeferred.await(),
                    artists = artistsDeferred.await(),
                    albums = albumsDeferred.await(),
                    playlists = playlistsDeferred.await()
                )
            }
        }

    suspend fun fetchAlbumOrPlaylistDetail(id: String, fallbackTitle: String = "Album"): AlbumDetail =
        withContext(Dispatchers.IO) {
            val browseDetail = innerTubeClient.fetchAlbumOrPlaylistBrowse(id, fallbackTitle)
            if (browseDetail != null && browseDetail.tracks.isNotEmpty()) {
                return@withContext browseDetail
            }

            try {
                newPipeDownloader.ensureInitialized()
                val cleanPlId = id.removePrefix("VL")
                val url = if (id.startsWith("http")) id else "https://www.youtube.com/playlist?list=$cleanPlId"
                val info = PlaylistInfo.getInfo(ServiceList.YouTube, url)
                val tracks = info.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .mapNotNull { mapStreamInfoItemToTrack(it) }
                if (tracks.isNotEmpty()) {
                    val thumb = info.thumbnails.maxByOrNull { it.width }?.url
                        ?: tracks.firstOrNull()?.thumbnailUrl
                        ?: ""
                    return@withContext AlbumDetail(
                        id = id,
                        title = info.name ?: fallbackTitle,
                        artistName = info.uploaderName ?: tracks.firstOrNull()?.artistName ?: "Artista",
                        thumbnailUrl = thumb,
                        tracks = tracks
                    )
                }
            } catch (_: Exception) {
            }

            val tracks = searchSongs(fallbackTitle)
            AlbumDetail(
                id = id,
                title = fallbackTitle,
                artistName = tracks.firstOrNull()?.artistName ?: "Artista",
                thumbnailUrl = tracks.firstOrNull()?.thumbnailUrl ?: "",
                tracks = tracks
            )
        }

    suspend fun fetchArtistDetail(artistId: String, artistNameHint: String = "Artista"): ArtistDetail =
        withContext(Dispatchers.IO) {
            val browseArtist = innerTubeClient.fetchArtistBrowse(artistId, artistNameHint)
            if (browseArtist != null && (browseArtist.popularTracks.isNotEmpty() || browseArtist.albums.isNotEmpty())) {
                val popTracks = browseArtist.popularTracks.ifEmpty {
                    searchSongs(browseArtist.name).take(12)
                }
                val albums = browseArtist.albums.ifEmpty {
                    innerTubeClient.searchAlbums(browseArtist.name).take(8)
                }
                return@withContext browseArtist.copy(
                    popularTracks = popTracks,
                    albums = albums,
                    discography = (browseArtist.discography + popTracks).distinctBy { it.id }
                )
            }

            val popularTracks = searchSongs(artistNameHint).take(12)
            val avatarUrl = popularTracks.firstOrNull()?.hdArtworkUrl ?: ""
            val albums = innerTubeClient.searchAlbums(artistNameHint).take(8)
            val singles = popularTracks.take(6).map {
                AlbumSummary(
                    id = it.albumId ?: it.id,
                    title = it.title,
                    artistName = it.artistName,
                    thumbnailUrl = it.thumbnailUrl
                )
            }
            val similar = innerTubeClient.searchArtists(artistNameHint)
                .filter { !it.name.equals(artistNameHint, ignoreCase = true) }
                .take(6)

            ArtistDetail(
                id = artistId,
                name = artistNameHint,
                description = null,
                thumbnailUrl = avatarUrl,
                popularTracks = popularTracks,
                albums = albums,
                singles = singles,
                similarArtists = similar,
                discography = popularTracks
            )
        }

    suspend fun fetchArtistDiscography(artistId: String, artistNameHint: String = "Artista"): List<Track> =
        withContext(Dispatchers.IO) {
            val cacheKey = if (artistId.isNotBlank()) artistId else artistNameHint.trim().lowercase()
            artistDiscographyCache[cacheKey]?.let { cached ->
                if (cached.size >= 8) return@withContext cached
            }

            val artistDetail = fetchArtistDetail(artistId, artistNameHint)
            val resolvedName = artistDetail.name.ifBlank { artistNameHint }.trim()
            val allTracks = mutableListOf<Track>()

            // 1. Includi i brani dalla discografia diretta di InnerTube e i brani popolari
            allTracks.addAll(artistDetail.discography)
            allTracks.addAll(artistDetail.popularTracks)

            // 2. Cerca brani specifici dell'artista tramite InnerTube
            try {
                val searched = innerTubeClient.searchSongs(resolvedName)
                val filteredSearched = searched.filter { track ->
                    isTrackByArtist(track, resolvedName, artistId)
                }
                allTracks.addAll(filteredSearched)
            } catch (e: Exception) {
                Log.w("GMusicAudio", "Errore ricerca brani discografia per $resolvedName: ${e.message}")
            }

            // 3. Estrai tracce da album e singoli dell'artista (fino a 6 album/singoli)
            val albumsToScan = (artistDetail.albums + artistDetail.singles).distinctBy { it.id }.take(6)
            albumsToScan.forEach { alb ->
                try {
                    val albumData = fetchAlbumOrPlaylistDetail(alb.id, alb.title)
                    val albumTracks = albumData.tracks.filter { track ->
                        isTrackByArtist(track, resolvedName, artistId) ||
                            albumData.artistName.contains(resolvedName, ignoreCase = true)
                    }
                    allTracks.addAll(albumTracks)
                } catch (e: Exception) {
                    Log.w("GMusicAudio", "Errore scansione tracce album ${alb.title}: ${e.message}")
                }
            }

            // 4. Filtra rigorosamente che ogni brano appartenga all'artista (nessun brano di altri artisti)
            val filtered = allTracks.filter { track ->
                isTrackByArtist(track, resolvedName, artistId)
            }

            // 5. Deduplica per ID e titolo normalizzato
            val finalDiscography = (if (filtered.isNotEmpty()) filtered else allTracks)
                .distinctBy { it.id }
                .distinctBy { it.title.trim().lowercase() }
                .ifEmpty { artistDetail.popularTracks }

            if (finalDiscography.isNotEmpty()) {
                artistDiscographyCache[cacheKey] = finalDiscography
                if (artistId.isNotBlank()) {
                    artistDiscographyCache[resolvedName.lowercase()] = finalDiscography
                }
            }
            finalDiscography
        }

    suspend fun fetchArtistAutoplayAddition(
        artistId: String,
        artistName: String,
        existingQueueIds: Set<String>
    ): List<Track> = withContext(Dispatchers.IO) {
        val discography = fetchArtistDiscography(artistId, artistName)
        if (discography.isEmpty()) return@withContext emptyList()

        val unqueued = discography.filter { it.id !in existingQueueIds }
        if (unqueued.isNotEmpty()) {
            return@withContext unqueued.shuffled().take(10)
        }

        // Se tutti i brani della discografia sono già stati aggiunti in coda,
        // rimescola l'intera discografia per continuare la riproduzione in loop infinito
        // ESCLUSIVAMENTE sui brani di questo artista!
        return@withContext discography.shuffled().take(10)
    }

    private fun isTrackByArtist(track: Track, targetArtistName: String, targetArtistId: String?): Boolean {
        if (!targetArtistId.isNullOrBlank() && track.artistId == targetArtistId) return true
        val cleanTarget = targetArtistName.trim().lowercase()
        val cleanTrackArtist = track.artistName.trim().lowercase()
        if (cleanTrackArtist == cleanTarget) return true
        if (cleanTrackArtist.startsWith("$cleanTarget ") ||
            cleanTrackArtist.startsWith("$cleanTarget,") ||
            cleanTrackArtist.startsWith("$cleanTarget &") ||
            cleanTrackArtist.startsWith("$cleanTarget feat") ||
            cleanTrackArtist.startsWith("$cleanTarget with")
        ) return true
        if (cleanTarget.contains(cleanTrackArtist) || cleanTrackArtist.contains(cleanTarget)) return true
        return false
    }

    private fun mapStreamInfoItemToTrack(item: StreamInfoItem): Track? {
        val videoId = extractVideoId(item.url) ?: return null
        val title = item.name ?: return null
        val lowerTitle = title.lowercase()
        // Filtra eventuali video musicali dal fallback NewPipe
        if (lowerTitle.contains("official video") ||
            lowerTitle.contains("official music video") ||
            lowerTitle.contains("video ufficiale")
        ) {
            return null
        }
        val thumb = item.thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val cleanArtist = (item.uploaderName ?: "Artista").removeSuffix(" - Topic").trim()
        return Track(
            id = videoId,
            title = title,
            artistName = cleanArtist,
            artistId = extractChannelId(item.uploaderUrl),
            thumbnailUrl = thumb,
            durationSeconds = item.duration.coerceAtLeast(0L)
        )
    }

    private fun extractVideoId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        Regex("[?&]v=([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        Regex("youtu\\.be/([a-zA-Z0-9_-]{11})").find(url)?.groupValues?.getOrNull(1)?.let { return it }
        return null
    }

    private fun extractChannelId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return Regex("channel/(UC[a-zA-Z0-9_-]+)").find(url)?.groupValues?.getOrNull(1)
    }

    companion object {
        private const val TAG = "GMusicAudio"
    }
}

private object YoutubeMusicSearchFilter {
    const val MUSIC_SONGS = "music_songs"
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)
