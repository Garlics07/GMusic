package com.gmusic.app.data.innertube

import android.util.Log
import com.gmusic.app.data.auth.AuthCredentialStore
import com.gmusic.app.domain.model.AlbumDetail
import com.gmusic.app.domain.model.AlbumSummary
import com.gmusic.app.domain.model.ArtistDetail
import com.gmusic.app.domain.model.ArtistSummary
import com.gmusic.app.domain.model.HomeItemType
import com.gmusic.app.domain.model.HomeShelfItem
import com.gmusic.app.domain.model.PlaylistSummary
import com.gmusic.app.domain.model.Track
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class ResolvedAudioFormat(
    val url: String,
    val itag: Int,
    val mimeType: String,
    val bitrate: Int,
    val expiresAtMillis: Long,
    val source: String
)

@Singleton
class InnerTubeClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val authCredentialStore: AuthCredentialStore,
    private val json: Json
) {
    /**
     * Risolve lo stream audio tramite InnerTube (ANDROID_VR / IOS / ANDROID_TESTSUITE / WEB_REMIX).
     * Rispetta il vincolo assoluto: SOLO AUDIO (itag 251 -> 250 -> 140, mimeType audio/).
     */
    suspend fun resolveAudioStream(
        videoId: String,
        requireAuthenticated: Boolean = false
    ): ResolvedAudioFormat? = withContext(Dispatchers.IO) {
        if (requireAuthenticated && !authCredentialStore.hasValidCookie()) {
            return@withContext null
        }

        val clientsToTry = if (authCredentialStore.hasValidCookie()) {
            listOf(
                ClientProfile.WEB_REMIX_AUTH,
                ClientProfile.ANDROID_VR,
                ClientProfile.IOS,
                ClientProfile.ANDROID_TESTSUITE
            )
        } else {
            listOf(
                ClientProfile.ANDROID_VR,
                ClientProfile.IOS,
                ClientProfile.ANDROID_TESTSUITE
            )
        }

        for (profile in clientsToTry) {
            try {
                val payload = buildJsonObject {
                    put("videoId", videoId)
                    put("racyCheckOk", true)
                    put("contentCheckOk", true)
                    putJsonObject("context") {
                        putJsonObject("client") {
                            put("clientName", profile.clientName)
                            put("clientVersion", profile.clientVersion)
                            put("hl", "it")
                            put("gl", "IT")
                            if (profile.androidSdkVersion != null) {
                                put("androidSdkVersion", profile.androidSdkVersion)
                            }
                            if (profile.osName != null) put("osName", profile.osName)
                            if (profile.osVersion != null) put("osVersion", profile.osVersion)
                            if (profile.deviceMake != null) put("deviceMake", profile.deviceMake)
                            if (profile.deviceModel != null) put("deviceModel", profile.deviceModel)
                            authCredentialStore.getVisitorData()?.let { put("visitorData", it) }
                        }
                    }
                }

                val requestBuilder = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                    .header("User-Agent", profile.userAgent)
                    .header("X-YouTube-Client-Name", profile.clientIdHeader)
                    .header("X-YouTube-Client-Version", profile.clientVersion)
                    .header("Origin", "https://www.youtube.com")
                    .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

                if (profile.sendAuth && authCredentialStore.hasValidCookie()) {
                    authCredentialStore.getRawCookies()?.let { requestBuilder.header("Cookie", it) }
                    authCredentialStore.buildSapisidHashHeader("https://music.youtube.com")?.let {
                        requestBuilder.header("Authorization", it)
                    }
                }

                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val root = json.parseToJsonElement(bodyStr).jsonObject
                    val adaptiveFormats = root["streamingData"]
                        ?.jsonObject
                        ?.get("adaptiveFormats")
                        ?.jsonArray
                        ?: return@use

                    val audioCandidate = selectBestAudioFormat(
                        formats = adaptiveFormats,
                        sourceName = "InnerTube-${profile.clientName}"
                    )
                    if (audioCandidate != null && isUrlPlayableBeyond64Kb(audioCandidate.url, profile.userAgent)) {
                        return@withContext audioCandidate
                    }
                }
            } catch (_: Exception) {
            }
        }
        null
    }

    private fun selectBestAudioFormat(
        formats: JsonArray,
        sourceName: String
    ): ResolvedAudioFormat? {
        val candidates = mutableListOf<ResolvedAudioFormat>()
        val defaultExpiry = System.currentTimeMillis() + FIVE_HOURS_MILLIS

        for (element in formats) {
            val obj = element.jsonObject
            val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull ?: continue
            if (!mimeType.startsWith("audio/", ignoreCase = true)) continue

            val url = obj["url"]?.jsonPrimitive?.contentOrNull ?: continue
            val itag = obj["itag"]?.jsonPrimitive?.intOrNull ?: -1
            if (itag !in ALLOWED_AUDIO_ITAGS && !mimeType.startsWith("audio/")) continue

            val bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0
            val expireSec = Regex("[?&]expire=(\\d+)").find(url)?.groupValues?.getOrNull(1)?.toLongOrNull()
            val expiresAtMillis = if (expireSec != null) {
                (expireSec * 1000L) - 60_000L
            } else {
                defaultExpiry
            }

            candidates.add(
                ResolvedAudioFormat(
                    url = url,
                    itag = itag,
                    mimeType = mimeType,
                    bitrate = bitrate,
                    expiresAtMillis = expiresAtMillis,
                    source = sourceName
                )
            )
        }

        if (candidates.isEmpty()) return null

        return candidates.sortedWith(
            compareByDescending<ResolvedAudioFormat> { formatPriority(it.itag, it.mimeType) }
                .thenByDescending { it.bitrate }
        ).firstOrNull()
    }

    private fun isUrlPlayableBeyond64Kb(url: String, userAgent: String): Boolean {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=131072-135167")
                .get()
                .build()
            okHttpClient.newCall(req).execute().use { resp ->
                resp.code in 200..299
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Suggerimenti live di ricerca tramite InnerTube YouTube Music (`music/get_search_suggestions`).
     */
    suspend fun getSearchSuggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        if (input.isBlank()) return@withContext emptyList()
        val payload = buildWebRemixPayload {
            put("input", input)
        }
        val root = executeWebRemixPost("music/get_search_suggestions", payload) ?: return@withContext emptyList()
        val suggestions = mutableListOf<String>()
        collectSearchSuggestions(root, suggestions)
        suggestions.distinct().take(8)
    }

    /**
     * Cerca SOLO BRANI UFFICIALI su YouTube Music (esclude rigorosamente i Video musicali).
     * Params non URL-encoded: `EgWKAQIIAWoKEAkQBRAKEAMQBA==` = filtro "Songs" (Audio Track / ATV).
     */
    suspend fun searchSongs(query: String): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("query", query)
            put("params", PARAMS_SONGS_ONLY)
        }
        val root = executeWebRemixPost("search", payload)
            ?: throw IOException("InnerTube searchSongs failed")
        val tracks = mutableListOf<Track>()
        collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = true)
        if (tracks.isEmpty()) {
            // Fallback senza filtro params ma scartando comunque qualsiasi VIDEO_TYPE_OMV / UGC
            val fallbackPayload = buildWebRemixPayload { put("query", query) }
            executeWebRemixPost("search", fallbackPayload)?.let { fbRoot ->
                collectMusicResponsiveListItems(fbRoot, tracks, onlyAudioSongs = true)
            }
        }
        tracks.distinctBy { it.id }
    }

    suspend fun searchArtists(query: String): List<ArtistSummary> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("query", query)
            put("params", PARAMS_ARTISTS_ONLY)
        }
        val root = executeWebRemixPost("search", payload) ?: return@withContext emptyList()
        val artists = mutableListOf<ArtistSummary>()
        collectArtistItems(root, artists)
        artists.distinctBy { it.id }
    }

    suspend fun searchAlbums(query: String): List<AlbumSummary> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("query", query)
            put("params", PARAMS_ALBUMS_ONLY)
        }
        val root = executeWebRemixPost("search", payload) ?: return@withContext emptyList()
        val albums = mutableListOf<AlbumSummary>()
        collectAlbumItems(root, albums)
        collectTwoRowItemAlbums(root, albums)
        albums.distinctBy { it.id }
    }

    suspend fun searchPlaylists(query: String): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("query", query)
            put("params", PARAMS_PLAYLISTS_ONLY)
        }
        val root = executeWebRemixPost("search", payload) ?: return@withContext emptyList()
        val playlists = mutableListOf<PlaylistSummary>()
        collectPlaylistItems(root, playlists, isLocal = false)
        collectTwoRowItemPlaylists(root, playlists, isLocal = false)
        playlists.distinctBy { it.id }
    }

    /**
     * Recupera i Brani Piaciuti ("LM" / "VLLM") dall'account YouTube Music autenticato.
     */
    suspend fun fetchAccountLikedTracks(): List<Track> = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext emptyList()
        val tracks = mutableListOf<Track>()
        for (browseId in listOf("VLLM", "FEmusic_liked_videos")) {
            val payload = buildWebRemixPayload {
                put("browseId", browseId)
            }
            val root = executeWebRemixPost("browse", payload) ?: continue
            collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = false)
            if (tracks.isNotEmpty()) break
        }
        tracks.distinctBy { it.id }
    }

    /**
     * Recupera le Playlist dell'utente dall'account YouTube Music autenticato (`FEmusic_liked_playlists`).
     */
    suspend fun fetchAccountPlaylists(): List<PlaylistSummary> = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext emptyList()
        val playlists = mutableListOf<PlaylistSummary>()
        for (browseId in listOf("FEmusic_liked_playlists", "FEmusic_library_landing")) {
            val payload = buildWebRemixPayload {
                put("browseId", browseId)
            }
            val root = executeWebRemixPost("browse", payload) ?: continue
            collectTwoRowItemPlaylists(root, playlists, isLocal = false)
            collectPlaylistItems(root, playlists, isLocal = false)
            if (playlists.isNotEmpty()) break
        }
        playlists
            .filter { it.id != "VLLM" && it.id != "LM" && it.id != "VLSE" }
            .distinctBy { it.id }
    }

    /**
     * Recupera la cronologia di ascolto ("Riproduzioni recenti") direttamente da YouTube Music (`FEmusic_history`).
     */
    suspend fun fetchAccountHistoryTracks(): List<Track> = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext emptyList()
        val payload = buildWebRemixPayload {
            put("browseId", "FEmusic_history")
        }
        val root = executeWebRemixPost("browse", payload) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()
        collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = false)
        collectTwoRowItemTracks(root, tracks)
        tracks.distinctBy { it.id }
    }

    /**
     * Recupera gli Album salvati nella libreria di YouTube Music (`FEmusic_liked_albums`).
     */
    suspend fun fetchAccountSavedAlbums(): List<AlbumSummary> = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext emptyList()
        val albums = mutableListOf<AlbumSummary>()
        for (browseId in listOf("FEmusic_liked_albums", "FEmusic_library_landing")) {
            val payload = buildWebRemixPayload {
                put("browseId", browseId)
            }
            val root = executeWebRemixPost("browse", payload) ?: continue
            collectTwoRowItemAlbums(root, albums)
            collectAlbumItems(root, albums)
            if (albums.isNotEmpty()) break
        }
        albums.distinctBy { it.id }
    }

    /**
     * Recupera gli Artisti effettivamente seguiti/iscritti nella libreria di YouTube Music (`FEmusic_library_corpus_artists`).
     * Non include gli artisti secondari dedotti dai singoli brani o playlist.
     */
    suspend fun fetchAccountSavedArtists(): List<ArtistSummary> = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext emptyList()
        val artists = mutableListOf<ArtistSummary>()
        for (browseId in listOf(
            "FEmusic_library_corpus_artists",
            "FEmusic_subscribed_artists"
        )) {
            val payload = buildWebRemixPayload {
                put("browseId", browseId)
            }
            val root = executeWebRemixPost("browse", payload) ?: continue
            collectArtistItems(root, artists)
            collectTwoRowItemArtists(root, artists)
            if (artists.isNotEmpty()) break
        }
        artists.distinctBy { it.id }
    }

    /**
     * Recupera l'URL dell'immagine profilo dell'account Google/YouTube Music autenticato.
     */
    suspend fun fetchAccountAvatarUrl(): String? = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext null
        val payload = buildWebRemixPayload {}
        val root = executeWebRemixPost("account/account_menu", payload)
        if (root != null) {
            findAccountPhotoUrl(root)?.let { return@withContext it }
        }
        null
    }

    /**
     * Recupera i metadati completi (titolo, artista, album, durata, copertina) di un singolo videoId.
     */
    suspend fun fetchTrackMetadata(videoId: String): Track? = withContext(Dispatchers.IO) {
        val nextPayload = buildWebRemixPayload {
            put("videoId", videoId)
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        executeWebRemixPost("next", nextPayload)?.let { root ->
            val panelTracks = mutableListOf<Track>()
            collectPlaylistPanelVideoRenderers(root, panelTracks)
            panelTracks.firstOrNull { it.id == videoId }?.let { return@withContext it }
            panelTracks.firstOrNull()?.let { return@withContext it }
        }

        val playerPayload = buildWebRemixPayload {
            put("videoId", videoId)
        }
        executeWebRemixPost("player", playerPayload)?.let { root ->
            val details = root["videoDetails"]?.jsonObject ?: return@let
            val title = details["title"]?.jsonPrimitive?.contentOrNull ?: return@let
            val author = (details["author"]?.jsonPrimitive?.contentOrNull ?: "Artista")
                .removeSuffix(" - Topic")
                .trim()
            val channelId = details["channelId"]?.jsonPrimitive?.contentOrNull
            val duration = details["lengthSeconds"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
            val thumb = details["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            return@withContext Track(
                id = videoId,
                title = title,
                artistName = author,
                artistId = channelId,
                thumbnailUrl = thumb,
                durationSeconds = duration
            )
        }
        null
    }

    /**
     * Sincronizza l'iscrizione/salvataggio di un Artista su YouTube Music.
     */
    suspend fun syncArtistSubscription(artistId: String, subscribe: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!authCredentialStore.hasValidCookie() || !artistId.startsWith("UC")) return@withContext false
            val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
            val payload = buildWebRemixPayload {
                put("channelIds", JsonArray(listOf(JsonPrimitive(artistId))))
            }
            executeWebRemixPost(endpoint, payload) != null
        }

    /**
     * Recupera il dettaglio di un Album (`MPREb_...`) o di una Playlist YouTube Music (`VL...` / `PL...`).
     */
    suspend fun fetchAlbumOrPlaylistBrowse(id: String, fallbackTitle: String): AlbumDetail? =
        withContext(Dispatchers.IO) {
            val isAlbum = id.startsWith("MPREb_") || id.startsWith("OLAK5uy_")
            val browseId = when {
                id.startsWith("MPREb_") -> id
                id.startsWith("OLAK5uy_") -> "VL$id"
                id.startsWith("VL") || id.startsWith("FE") -> id
                else -> "VL$id"
            }
            val payload = buildWebRemixPayload {
                put("browseId", browseId)
            }
            val root = executeWebRemixPost("browse", payload) ?: return@withContext null
            val tracks = mutableListOf<Track>()
            // Per gli album e raccolte musicali richiediamo categoricamente le tracce audio ufficiali (ATV), non video musicali
            collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = true)
            if (tracks.isEmpty()) {
                collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = false)
            }
            if (tracks.isEmpty()) return@withContext null

            val headerTitle = findFirstHeaderTitle(root) ?: fallbackTitle
            val headerThumb = findFirstHeaderThumbnail(root)?.takeIf { it.isNotBlank() }
                ?: tracks.first().hdArtworkUrl
            val artist = tracks.firstOrNull()?.artistName ?: "YouTube Music"

            val cleanTracks = tracks.map { t ->
                val bestThumb = if (headerThumb.isNotBlank() && (isAlbum || !t.thumbnailUrl.contains("googleusercontent.com") || t.thumbnailUrl.contains("ytimg.com/vi/"))) {
                    headerThumb
                } else if (t.thumbnailUrl.isBlank() || t.thumbnailUrl.contains("/vi//")) {
                    headerThumb
                } else {
                    t.thumbnailUrl
                }
                t.copy(
                    thumbnailUrl = bestThumb,
                    albumTitle = if (isAlbum) headerTitle else t.albumTitle ?: headerTitle,
                    albumId = if (isAlbum) id else t.albumId ?: id
                )
            }

            AlbumDetail(
                id = id,
                title = headerTitle,
                artistName = artist,
                thumbnailUrl = headerThumb,
                tracks = cleanTracks
            )
        }

    /**
     * Recupera il dettaglio di un Artista (`UC...`) tramite InnerTube YouTube Music.
     */
    suspend fun fetchArtistBrowse(artistId: String, nameHint: String): ArtistDetail? =
        withContext(Dispatchers.IO) {
            if (!artistId.startsWith("UC")) return@withContext null
            val payload = buildWebRemixPayload {
                put("browseId", artistId)
            }
            val root = executeWebRemixPost("browse", payload) ?: return@withContext null
            val name = findFirstHeaderTitle(root) ?: nameHint
            val headerThumb = findFirstHeaderThumbnail(root) ?: ""
            val songs = mutableListOf<Track>()
            val albums = mutableListOf<AlbumSummary>()
            collectMusicResponsiveListItems(root, songs, onlyAudioSongs = true)
            collectTwoRowItemAlbums(root, albums)

            // Tentativo di recuperare la lista estesa "Vedi tutti i brani" dell'artista
            val moreSongsEndpoint = findArtistMoreSongsBrowseEndpoint(root)
            if (moreSongsEndpoint != null) {
                val (moreBrowseId, moreParams) = moreSongsEndpoint
                val morePayload = buildWebRemixPayload {
                    put("browseId", moreBrowseId)
                    if (!moreParams.isNullOrBlank()) {
                        put("params", moreParams)
                    }
                }
                executeWebRemixPost("browse", morePayload)?.let { moreRoot ->
                    collectMusicResponsiveListItems(moreRoot, songs, onlyAudioSongs = true)
                }
            }

            if (songs.isEmpty() && albums.isEmpty()) return@withContext null
            val distinctSongs = songs.distinctBy { it.id }
            ArtistDetail(
                id = artistId,
                name = name,
                thumbnailUrl = headerThumb.ifBlank { distinctSongs.firstOrNull()?.hdArtworkUrl ?: "" },
                popularTracks = distinctSongs.take(15),
                albums = albums.distinctBy { it.id }.take(15),
                discography = distinctSongs
            )
        }

    /**
     * Radio / Up Next Mix basato su un videoId (solo tracce audio).
     */
    suspend fun fetchRadioTracks(videoId: String): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("videoId", videoId)
            put("playlistId", "RDAMVM$videoId")
            put("isAudioOnly", true)
            put("enablePersistentPlaylistPanel", true)
        }
        val root = executeWebRemixPost("next", payload) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()
        collectPlaylistPanelVideoRenderers(root, tracks)
        tracks.filter { it.id != videoId }.distinctBy { it.id }
    }

    /**
     * Feed Home di YouTube Music (`FEmusic_home`, `hl=it`, `gl=IT`), autenticato o ospite.
     */
    suspend fun fetchHomeTracks(): List<Track> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("browseId", "FEmusic_home")
        }
        val root = executeWebRemixPost("browse", payload) ?: return@withContext emptyList()
        val tracks = mutableListOf<Track>()
        collectMusicResponsiveListItems(root, tracks, onlyAudioSongs = true)
        collectTwoRowItemTracks(root, tracks)
        tracks.distinctBy { it.id }
    }

    /**
     * Classifiche e Nuove Uscite Italia (Top 100 brani ufficiali YouTube Music Italia e Nuove uscite).
     */
    suspend fun fetchItalyExploreData(): Pair<List<Track>, List<AlbumSummary>> = withContext(Dispatchers.IO) {
        val chartsTracks = mutableListOf<Track>()
        val newAlbums = mutableListOf<AlbumSummary>()

        // 1. Recupero ufficiale Classifica Top 100 Italia da YouTube Music (playlist ufficiale Italia)
        try {
            val officialCharts = fetchAlbumOrPlaylistBrowse("PL4fGSI46T5U59Zc18v5o_5u8mB4v_7t9l", "Top 100 Italia")
            if (officialCharts != null && officialCharts.tracks.isNotEmpty()) {
                chartsTracks.addAll(officialCharts.tracks)
            }
        } catch (_: Exception) {}

        if (chartsTracks.isEmpty()) {
            try {
                val officialHits = fetchAlbumOrPlaylistBrowse("RDCLAK5uy_kfd5De34w92NG56O3MaCdBmQg76Yg6yA4", "Top Hits Italia")
                if (officialHits != null && officialHits.tracks.isNotEmpty()) {
                    chartsTracks.addAll(officialHits.tracks)
                }
            } catch (_: Exception) {}
        }

        if (chartsTracks.isEmpty()) {
            val chartsPayload = buildWebRemixPayload {
                put("browseId", "FEmusic_charts")
                putJsonObject("formData") {
                    put("selectedValues", JsonArray(listOf(JsonPrimitive("IT"))))
                }
            }
            executeWebRemixPost("browse", chartsPayload)?.let { root ->
                collectMusicResponsiveListItems(root, chartsTracks, onlyAudioSongs = true)
                collectTwoRowItemTracks(root, chartsTracks)
            }
        }

        val explorePayload = buildWebRemixPayload {
            put("browseId", "FEmusic_explore")
        }
        executeWebRemixPost("browse", explorePayload)?.let { root ->
            collectMusicResponsiveListItems(root, chartsTracks, onlyAudioSongs = true)
            collectTwoRowItemAlbums(root, newAlbums)
        }

        chartsTracks.distinctBy { it.id } to newAlbums.distinctBy { it.id }
    }

    /**
     * Recupera i mix personali ufficiali da YouTube Music ("I tuoi mix" / "Mixed for you").
     */
    suspend fun fetchYouTubeMusicMixes(): List<HomeShelfItem> = withContext(Dispatchers.IO) {
        val payload = buildWebRemixPayload {
            put("browseId", "FEmusic_home")
        }
        val root = executeWebRemixPost("browse", payload) ?: return@withContext emptyList()
        val mixes = mutableListOf<HomeShelfItem>()
        collectMixShelfItems(root, mixes)
        mixes.distinctBy { it.id }.take(6)
    }

    /**
     * Recupera i brani in tendenza in Italia da YouTube Music ("Tendenze").
     */
    suspend fun fetchTrendingTracks(): List<Track> = withContext(Dispatchers.IO) {
        val trending = mutableListOf<Track>()
        // 1. Classifica Top 100 Italia ufficiale YouTube Music
        try {
            val officialCharts = fetchAlbumOrPlaylistBrowse("PL4fGSI46T5U59Zc18v5o_5u8mB4v_7t9l", "Tendenze Italia")
            if (officialCharts != null && officialCharts.tracks.isNotEmpty()) {
                trending.addAll(officialCharts.tracks)
            }
        } catch (_: Exception) {}

        // 2. Fallback Top Hits Italia
        if (trending.isEmpty()) {
            try {
                val officialHits = fetchAlbumOrPlaylistBrowse("RDCLAK5uy_kfd5De34w92NG56O3MaCdBmQg76Yg6yA4", "Top Hits Italia")
                if (officialHits != null && officialHits.tracks.isNotEmpty()) {
                    trending.addAll(officialHits.tracks)
                }
            } catch (_: Exception) {}
        }

        // 3. Fallback FEmusic_charts
        if (trending.isEmpty()) {
            val chartsPayload = buildWebRemixPayload {
                put("browseId", "FEmusic_charts")
                putJsonObject("formData") {
                    put("selectedValues", JsonArray(listOf(JsonPrimitive("IT"))))
                }
            }
            executeWebRemixPost("browse", chartsPayload)?.let { root ->
                collectMusicResponsiveListItems(root, trending, onlyAudioSongs = true)
                collectTwoRowItemTracks(root, trending)
            }
        }

        // 4. Fallback ricerca Tendenze Italia
        if (trending.isEmpty()) {
            trending.addAll(searchSongs("Tendenze Italia").take(25))
        }

        trending.distinctBy { it.id }
    }

    /**
     * Sincronizza Like / Dislike con l'account YouTube Music tramite endpoint autenticati InnerTube.
     */
    suspend fun syncLikeStatus(videoId: String, liked: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext false
        val endpoint = if (liked) "like/like" else "like/removelike"
        val payload = buildWebRemixPayload {
            putJsonObject("target") {
                put("videoId", videoId)
            }
        }
        executeWebRemixPost(endpoint, payload) != null
    }

    suspend fun createRemotePlaylist(title: String): String? = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext null
        val payload = buildWebRemixPayload {
            put("title", title)
            put("privacyStatus", "PRIVATE")
        }
        val resp = executeWebRemixPost("playlist/create", payload) ?: return@withContext null
        resp["playlistId"]?.jsonPrimitive?.contentOrNull
    }

    suspend fun renameRemotePlaylist(remotePlaylistId: String, newTitle: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!authCredentialStore.hasValidCookie()) return@withContext false
            val cleanId = remotePlaylistId.removePrefix("VL")
            val payload = buildWebRemixPayload {
                put("playlistId", cleanId)
                put(
                    "actions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("action", "ACTION_SET_PLAYLIST_NAME")
                                put("playlistName", newTitle)
                            }
                        )
                    )
                )
            }
            executeWebRemixPost("browse/edit_playlist", payload) != null
        }

    suspend fun deleteRemotePlaylist(remotePlaylistId: String): Boolean = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext false
        val cleanId = remotePlaylistId.removePrefix("VL")
        val payload = buildWebRemixPayload {
            put("playlistId", cleanId)
        }
        executeWebRemixPost("playlist/delete", payload) != null
    }

    suspend fun addTrackToRemotePlaylist(remotePlaylistId: String, videoId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!authCredentialStore.hasValidCookie()) return@withContext false
            val cleanId = remotePlaylistId.removePrefix("VL")
            val payload = buildWebRemixPayload {
                put("playlistId", cleanId)
                put(
                    "actions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("action", "ACTION_ADD_VIDEO")
                                put("addedVideoId", videoId)
                            }
                        )
                    )
                )
            }
            executeWebRemixPost("browse/edit_playlist", payload) != null
        }

    suspend fun removeTrackFromRemotePlaylist(remotePlaylistId: String, videoId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!authCredentialStore.hasValidCookie()) return@withContext false
            val cleanId = remotePlaylistId.removePrefix("VL")
            val payload = buildWebRemixPayload {
                put("playlistId", cleanId)
                put(
                    "actions",
                    JsonArray(
                        listOf(
                            buildJsonObject {
                                put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID")
                                put("removedVideoId", videoId)
                            }
                        )
                    )
                )
            }
            executeWebRemixPost("browse/edit_playlist", payload) != null
        }

    /**
     * Registra l'ascolto del brano nella Cronologia ufficiale dell'account YouTube Music (Watch Time & Playback Tracking).
     */
    suspend fun reportPlayback(videoId: String): Boolean = withContext(Dispatchers.IO) {
        if (!authCredentialStore.hasValidCookie()) return@withContext false
        try {
            val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_"
            val cpn = (1..16).map { chars.random() }.joinToString("")
            val signatureTimestamp = (System.currentTimeMillis() / 1000 / 86400).toInt() - 1
            val playerPayload = buildWebRemixPayload {
                put("videoId", videoId)
                put("video_id", videoId)
                put("cpn", cpn)
                putJsonObject("playbackContext") {
                    putJsonObject("contentPlaybackContext") {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", signatureTimestamp)
                    }
                }
            }
            val resp = executeWebRemixPost("player", playerPayload)
            val tracking = resp?.get("playbackTracking")?.jsonObject
            val playbackUrl = tracking?.get("videostatsPlaybackUrl")?.jsonObject?.get("baseUrl")?.jsonPrimitive?.contentOrNull
            val watchtimeUrl = tracking?.get("videostatsWatchtimeUrl")?.jsonObject?.get("baseUrl")?.jsonPrimitive?.contentOrNull

            val cookie = authCredentialStore.getRawCookies().orEmpty()
            val authHeader = authCredentialStore.buildSapisidHashHeader("https://music.youtube.com")

            val targetPlayback = if (!playbackUrl.isNullOrBlank()) {
                val sep = if (playbackUrl.contains("?")) "&" else "?"
                if (playbackUrl.contains("cpn=")) playbackUrl else "$playbackUrl${sep}ver=2&c=WEB_REMIX&cpn=$cpn"
            } else {
                "https://s.youtube.com/api/stats/playback?docid=$videoId&ns=yt&el=detailpage&ver=2&c=WEB_REMIX&cpn=$cpn"
            }

            val targetWatchtime = if (!watchtimeUrl.isNullOrBlank()) {
                val sep = if (watchtimeUrl.contains("?")) "&" else "?"
                if (watchtimeUrl.contains("cpn=")) watchtimeUrl else "$watchtimeUrl${sep}ver=2&c=WEB_REMIX&cpn=$cpn&cmt=15.0&state=playing"
            } else {
                "https://s.youtube.com/api/stats/watchtime?docid=$videoId&ns=yt&el=detailpage&ver=2&c=WEB_REMIX&cpn=$cpn&cmt=15.0&state=playing"
            }

            val targets = listOf(targetPlayback, targetWatchtime)
            var anySuccess = false
            for (target in targets) {
                val reqBuilder = Request.Builder()
                    .url(target)
                    .header("User-Agent", ClientProfile.WEB_REMIX_AUTH.userAgent)
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                if (cookie.isNotBlank()) {
                    reqBuilder.header("Cookie", cookie)
                }
                if (!authHeader.isNullOrBlank()) {
                    reqBuilder.header("Authorization", authHeader)
                }
                val callResp = okHttpClient.newCall(reqBuilder.build()).execute()
                val code = callResp.code
                callResp.close()
                Log.d("GMusicAudio", "reportPlayback ping HTTP $code per $target")
                if (code in 200..299) {
                    anySuccess = true
                }
            }
            Log.i("GMusicAudio", "Cronologia YouTube Music sincronizzata per brano $videoId (success: $anySuccess)")
            anySuccess
        } catch (e: Exception) {
            Log.w("GMusicAudio", "Impossibile sincronizzare ascolto su YouTube Music: ${e.message}")
            false
        }
    }

    private fun buildWebRemixPayload(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject =
        buildJsonObject {
            builder()
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20250120.01.00")
                    put("hl", "it")
                    put("gl", "IT")
                    authCredentialStore.getVisitorData()?.let { put("visitorData", it) }
                }
            }
        }

    private fun executeWebRemixPost(endpoint: String, payload: JsonObject): JsonObject? {
        return try {
            val reqBuilder = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
                .header("User-Agent", ClientProfile.WEB_REMIX_AUTH.userAgent)
                .header("X-YouTube-Client-Name", ClientProfile.WEB_REMIX_AUTH.clientIdHeader)
                .header("X-YouTube-Client-Version", ClientProfile.WEB_REMIX_AUTH.clientVersion)
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-Goog-AuthUser", "0")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))

            if (authCredentialStore.hasValidCookie()) {
                authCredentialStore.getRawCookies()?.let { reqBuilder.header("Cookie", it) }
                authCredentialStore.buildSapisidHashHeader("https://music.youtube.com")?.let {
                    reqBuilder.header("Authorization", it)
                }
            }

            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("GMusicAudio", "InnerTube $endpoint HTTP ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                json.parseToJsonElement(body).jsonObject
            }
        } catch (e: Exception) {
            Log.w("GMusicAudio", "InnerTube $endpoint error: ${e.message}")
            null
        }
    }

    private fun collectSearchSuggestions(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonObject -> {
                val suggestionRenderer = element["searchSuggestionRenderer"]?.jsonObject
                if (suggestionRenderer != null) {
                    val query = suggestionRenderer["navigationEndpoint"]
                        ?.jsonObject
                        ?.get("searchEndpoint")
                        ?.jsonObject
                        ?.get("query")
                        ?.jsonPrimitive
                        ?.contentOrNull
                    if (!query.isNullOrBlank()) {
                        out.add(query)
                    }
                } else {
                    for ((_, child) in element) {
                        collectSearchSuggestions(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectSearchSuggestions(it, out) }
            else -> Unit
        }
    }

    private fun collectPlaylistPanelVideoRenderers(element: JsonElement, out: MutableList<Track>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["playlistPanelVideoRenderer"]?.jsonObject
                if (renderer != null) {
                    // Verifica che non sia un video musicale OMV/UGC se specificato nel musicVideoType
                    val videoType = extractMusicVideoType(renderer)
                    if (videoType != null && videoType != "MUSIC_VIDEO_TYPE_ATV" && videoType != "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK") {
                        return
                    }
                    val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull ?: return
                    val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: return
                    val artistRuns = renderer["longBylineText"]?.jsonObject?.get("runs")?.jsonArray
                        ?: renderer["shortBylineText"]?.jsonObject?.get("runs")?.jsonArray
                    val artistName = (artistRuns?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: "Artista").removeSuffix(" - Topic").trim()
                    val artistId = artistRuns?.firstOrNull()?.jsonObject
                        ?.get("navigationEndpoint")?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    var albumTitle: String? = null
                    var albumId: String? = null
                    artistRuns?.forEach { rEl ->
                        val rObj = rEl.jsonObject
                        val bId = rObj["navigationEndpoint"]?.jsonObject
                            ?.get("browseEndpoint")?.jsonObject
                            ?.get("browseId")?.jsonPrimitive?.contentOrNull
                        if (bId != null && (bId.startsWith("MPREb_") || bId.startsWith("OLAK5uy_"))) {
                            albumId = bId
                            albumTitle = rObj["text"]?.jsonPrimitive?.contentOrNull
                        }
                    }
                    val lengthText = renderer["lengthText"]?.jsonObject?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    val durationSec = lengthText?.let { parseDurationText(it) } ?: 0L
                    val thumb = renderer["thumbnail"]?.jsonObject?.get("thumbnails")?.jsonArray
                        ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                        ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    out.add(
                        Track(
                            id = videoId,
                            title = title,
                            artistName = artistName,
                            artistId = artistId,
                            albumTitle = albumTitle,
                            albumId = albumId,
                            thumbnailUrl = thumb,
                            durationSeconds = durationSec
                        )
                    )
                } else {
                    for ((_, child) in element) {
                        collectPlaylistPanelVideoRenderers(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectPlaylistPanelVideoRenderers(it, out) }
            else -> Unit
        }
    }

    private fun collectTwoRowItemTracks(element: JsonElement, out: MutableList<Track>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicTwoRowItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val videoType = extractMusicVideoType(renderer)
                    if (videoType == "MUSIC_VIDEO_TYPE_OMV" || videoType == "MUSIC_VIDEO_TYPE_UGC") {
                        return
                    }
                    val videoId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("watchEndpoint")?.jsonObject
                        ?.get("videoId")?.jsonPrimitive?.contentOrNull
                    if (!videoId.isNullOrBlank()) {
                        val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val subtitleRuns = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                        val subtitle = subtitleRuns
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                            ?.firstOrNull { it != "Brano" && it != "•" && it.isNotBlank() }
                            ?: "Artista"
                        val thumb = renderer["thumbnailRenderer"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                        out.add(
                            Track(
                                id = videoId,
                                title = title,
                                artistName = subtitle,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectTwoRowItemTracks(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectTwoRowItemTracks(it, out) }
            else -> Unit
        }
    }

    private fun collectTwoRowItemAlbums(element: JsonElement, out: MutableList<AlbumSummary>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicTwoRowItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_"))) {
                        val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val subtitleRuns = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                        val artist = subtitleRuns?.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: "Artista"
                        val thumb = renderer["thumbnailRenderer"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            AlbumSummary(
                                id = browseId,
                                title = title,
                                artistName = artist,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectTwoRowItemAlbums(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectTwoRowItemAlbums(it, out) }
            else -> Unit
        }
    }

    private fun collectTwoRowItemPlaylists(
        element: JsonElement,
        out: MutableList<PlaylistSummary>,
        isLocal: Boolean
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicTwoRowItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("RDCLAK"))) {
                        val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val subtitle = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                        val thumb = renderer["thumbnailRenderer"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            PlaylistSummary(
                                id = browseId,
                                title = title,
                                author = subtitle ?: "YouTube Music",
                                trackCount = 0,
                                thumbnailUrls = if (thumb.isNotBlank()) listOf(thumb) else emptyList(),
                                isLocal = isLocal
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectTwoRowItemPlaylists(child, out, isLocal)
                    }
                }
            }
            is JsonArray -> element.forEach { collectTwoRowItemPlaylists(it, out, isLocal) }
            else -> Unit
        }
    }

    private fun collectMixShelfItems(element: JsonElement, out: MutableList<HomeShelfItem>) {
        when (element) {
            is JsonObject -> {
                val carousel = element["musicCarouselShelfRenderer"]?.jsonObject
                if (carousel != null) {
                    val headerTitle = carousel["header"]?.jsonObject
                        ?.get("musicCarouselShelfHeaderRenderer")?.jsonObject
                        ?.get("title")?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                        ?: ""
                    val isMixShelf = headerTitle.contains("mix", ignoreCase = true) ||
                        headerTitle.contains("fatto per te", ignoreCase = true) ||
                        headerTitle.contains("mixed for you", ignoreCase = true) ||
                        headerTitle.contains("per te", ignoreCase = true)

                    val contents = carousel["contents"]?.jsonArray
                    if (contents != null) {
                        for (item in contents) {
                            val renderer = item.jsonObject["musicTwoRowItemRenderer"]?.jsonObject ?: continue
                            val title = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                                ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                                ?: continue
                            val isMixItem = isMixShelf || title.contains("mix", ignoreCase = true) || title.contains("supermix", ignoreCase = true)
                            if (!isMixItem) continue

                            val subtitle = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                                ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                                ?: "YouTube Music"

                            val thumb = renderer["thumbnailRenderer"]?.jsonObject
                                ?.get("musicThumbnailRenderer")?.jsonObject
                                ?.get("thumbnail")?.jsonObject
                                ?.get("thumbnails")?.jsonArray
                                ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                                ?: ""

                            val navEndpoint = renderer["navigationEndpoint"]?.jsonObject
                            val playlistId = navEndpoint?.get("watchEndpoint")?.jsonObject
                                ?.get("playlistId")?.jsonPrimitive?.contentOrNull
                                ?: navEndpoint?.get("watchPlaylistEndpoint")?.jsonObject
                                    ?.get("playlistId")?.jsonPrimitive?.contentOrNull
                                ?: navEndpoint?.get("browseEndpoint")?.jsonObject
                                    ?.get("browseId")?.jsonPrimitive?.contentOrNull
                                ?: "ytm_mix_${out.size}"

                            out.add(
                                HomeShelfItem(
                                    id = playlistId,
                                    title = title,
                                    subtitle = subtitle,
                                    thumbnailUrl = thumb,
                                    type = HomeItemType.MIX
                                )
                            )
                        }
                    }
                } else {
                    for ((_, child) in element) {
                        collectMixShelfItems(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectMixShelfItems(it, out) }
            else -> Unit
        }
    }

    private fun collectTwoRowItemArtists(element: JsonElement, out: MutableList<ArtistSummary>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicTwoRowItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("UC") || browseId.startsWith("MPLAUC"))) {
                        val cleanId = browseId.removePrefix("MPLA")
                        val name = renderer["title"]?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val sub = renderer["subtitle"]?.jsonObject?.get("runs")?.jsonArray
                            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                        val thumb = renderer["thumbnailRenderer"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            ArtistSummary(
                                id = cleanId,
                                name = name,
                                subscribersText = sub,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectTwoRowItemArtists(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectTwoRowItemArtists(it, out) }
            else -> Unit
        }
    }

    private fun findAccountPhotoUrl(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                val photoObj = element["accountPhoto"]?.jsonObject ?: element["avatar"]?.jsonObject
                if (photoObj != null) {
                    val url = photoObj["thumbnails"]?.jsonArray
                        ?.lastOrNull()?.jsonObject
                        ?.get("url")?.jsonPrimitive?.contentOrNull
                    if (!url.isNullOrBlank()) return url
                }
                for ((_, child) in element) {
                    findAccountPhotoUrl(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findAccountPhotoUrl(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun collectArtistItems(element: JsonElement, out: MutableList<ArtistSummary>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("UC") || browseId.startsWith("MPLAUC"))) {
                        val cleanId = browseId.removePrefix("MPLA")
                        val flexColumns = renderer["flexColumns"]?.jsonArray
                        val name = flexColumns?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val sub = flexColumns.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        val thumb = renderer["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            ArtistSummary(
                                id = cleanId,
                                name = name,
                                subscribersText = sub,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectArtistItems(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectArtistItems(it, out) }
            else -> Unit
        }
    }

    private fun collectAlbumItems(element: JsonElement, out: MutableList<AlbumSummary>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_"))) {
                        val flexColumns = renderer["flexColumns"]?.jsonArray
                        val title = flexColumns?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val secondRuns = flexColumns.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        val artist = secondRuns
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                            ?.filter { it != "•" && it != "Album" && it != "Singolo" && it != "EP" }
                            ?.firstOrNull()
                            ?: "Artista"
                        val year = secondRuns
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                            ?.firstOrNull { it.matches(Regex("\\d{4}")) }
                        val thumb = renderer["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            AlbumSummary(
                                id = browseId,
                                title = title,
                                artistName = artist,
                                year = year,
                                thumbnailUrl = thumb
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectAlbumItems(child, out)
                    }
                }
            }
            is JsonArray -> element.forEach { collectAlbumItems(it, out) }
            else -> Unit
        }
    }

    private fun collectPlaylistItems(
        element: JsonElement,
        out: MutableList<PlaylistSummary>,
        isLocal: Boolean
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                if (renderer != null) {
                    val browseId = renderer["navigationEndpoint"]?.jsonObject
                        ?.get("browseEndpoint")?.jsonObject
                        ?.get("browseId")?.jsonPrimitive?.contentOrNull
                    if (browseId != null && (browseId.startsWith("VL") || browseId.startsWith("RDCLAK"))) {
                        val flexColumns = renderer["flexColumns"]?.jsonArray
                        val title = flexColumns?.getOrNull(0)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                            ?: return
                        val secondRuns = flexColumns.getOrNull(1)?.jsonObject
                            ?.get("musicResponsiveListItemFlexColumnRenderer")?.jsonObject
                            ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                        val author = secondRuns
                            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
                            ?.filter { it != "•" && it != "Playlist" }
                            ?.firstOrNull()
                        val thumb = renderer["thumbnail"]?.jsonObject
                            ?.get("musicThumbnailRenderer")?.jsonObject
                            ?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: ""
                        out.add(
                            PlaylistSummary(
                                id = browseId,
                                title = title,
                                author = author ?: "YouTube Music",
                                trackCount = 0,
                                thumbnailUrls = if (thumb.isNotBlank()) listOf(thumb) else emptyList(),
                                isLocal = isLocal
                            )
                        )
                    }
                } else {
                    for ((_, child) in element) {
                        collectPlaylistItems(child, out, isLocal)
                    }
                }
            }
            is JsonArray -> element.forEach { collectPlaylistItems(it, out, isLocal) }
            else -> Unit
        }
    }

    private fun findArtistMoreSongsBrowseEndpoint(element: JsonElement): Pair<String, String?>? {
        when (element) {
            is JsonObject -> {
                val shelfRenderer = element["musicShelfRenderer"]?.jsonObject
                    ?: element["musicCarouselShelfRenderer"]?.jsonObject
                if (shelfRenderer != null) {
                    val headerTitle = shelfRenderer["header"]?.jsonObject
                        ?.get("musicShelfHeaderRenderer")?.jsonObject
                        ?.get("title")?.jsonObject?.get("runs")?.jsonArray
                        ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                        ?: shelfRenderer["header"]?.jsonObject
                            ?.get("musicCarouselShelfHeaderRenderer")?.jsonObject
                            ?.get("title")?.jsonObject?.get("runs")?.jsonArray
                            ?.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull ?: "" }
                        ?: ""

                    val isSongsShelf = headerTitle.contains("brani", ignoreCase = true) ||
                        headerTitle.contains("canzoni", ignoreCase = true) ||
                        headerTitle.contains("songs", ignoreCase = true)

                    val bottomEndpoint = shelfRenderer["bottomEndpoint"]?.jsonObject?.get("browseEndpoint")?.jsonObject
                    val headerButtonEndpoint = shelfRenderer["header"]?.jsonObject
                        ?.get("musicShelfHeaderRenderer")?.jsonObject
                        ?.get("title")?.jsonObject?.get("runs")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject
                        ?: shelfRenderer["header"]?.jsonObject
                            ?.get("musicCarouselShelfHeaderRenderer")?.jsonObject
                            ?.get("moreContentButton")?.jsonObject?.get("buttonRenderer")?.jsonObject
                            ?.get("navigationEndpoint")?.jsonObject?.get("browseEndpoint")?.jsonObject

                    val targetEndpoint = bottomEndpoint ?: (if (isSongsShelf) headerButtonEndpoint else null)
                    if (targetEndpoint != null) {
                        val bId = targetEndpoint["browseId"]?.jsonPrimitive?.contentOrNull
                        val params = targetEndpoint["params"]?.jsonPrimitive?.contentOrNull
                        if (bId != null && (bId == "FEmusic_artist_songs" || bId.startsWith("VL") || isSongsShelf)) {
                            return bId to params
                        }
                    }
                }

                for ((_, child) in element) {
                    findArtistMoreSongsBrowseEndpoint(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (child in element) {
                    findArtistMoreSongsBrowseEndpoint(child)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun collectMusicResponsiveListItems(
        element: JsonElement,
        out: MutableList<Track>,
        onlyAudioSongs: Boolean = true
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = element["musicResponsiveListItemRenderer"]?.jsonObject
                if (renderer != null) {
                    parseTrackFromRenderer(renderer, onlyAudioSongs)?.let { out.add(it) }
                } else {
                    for ((_, child) in element) {
                        collectMusicResponsiveListItems(child, out, onlyAudioSongs)
                    }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    collectMusicResponsiveListItems(item, out, onlyAudioSongs)
                }
            }
            else -> Unit
        }
    }

    private fun parseTrackFromRenderer(renderer: JsonObject, onlyAudioSongs: Boolean): Track? {
        if (onlyAudioSongs) {
            val videoType = extractMusicVideoType(renderer)
            // Scarta rigorosamente Video Musicali Ufficiali (OMV) e Video Utente (UGC)
            if (videoType == "MUSIC_VIDEO_TYPE_OMV" || videoType == "MUSIC_VIDEO_TYPE_UGC") {
                return null
            }
        }

        val videoId = renderer["playlistItemData"]
            ?.jsonObject
            ?.get("videoId")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: findWatchVideoId(renderer)
            ?: return null

        val flexColumns = renderer["flexColumns"]?.jsonArray ?: return null
        val title = flexColumns.getOrNull(0)
            ?.jsonObject
            ?.get("musicResponsiveListItemFlexColumnRenderer")
            ?.jsonObject
            ?.get("text")
            ?.jsonObject
            ?.get("runs")
            ?.jsonArray
            ?.firstOrNull()
            ?.jsonObject
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: return null

        // Raccogli i runs da tutte le flexColumns successive alla 0 (in YT Music l'artista è spesso in col 1 e l'album in col 2)
        val allSecondaryRuns = mutableListOf<JsonObject>()
        for (i in 1 until flexColumns.size) {
            val colRuns = flexColumns.getOrNull(i)
                ?.jsonObject
                ?.get("musicResponsiveListItemFlexColumnRenderer")
                ?.jsonObject
                ?.get("text")
                ?.jsonObject
                ?.get("runs")
                ?.jsonArray
            colRuns?.forEach { rEl ->
                rEl.jsonObject.let { allSecondaryRuns.add(it) }
            }
        }

        // In YT Music il primo run può essere l'etichetta "Brano" o "Video" seguita da " • " e poi l'Artista
        val firstRunText = allSecondaryRuns.firstOrNull()?.get("text")?.jsonPrimitive?.contentOrNull
        if (onlyAudioSongs && firstRunText != null && firstRunText.equals("Video", ignoreCase = true)) {
            return null
        }

        val meaningfulRuns = allSecondaryRuns.filter { rObj ->
            val txt = rObj["text"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
            txt.isNotEmpty() && txt != "•" && !txt.equals("Brano", ignoreCase = true) && !txt.equals("Song", ignoreCase = true)
        }

        var albumTitle: String? = null
        var albumId: String? = null
        meaningfulRuns.forEach { rObj ->
            val browseId = rObj["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull
            if (browseId != null && (browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_"))) {
                albumId = browseId
                albumTitle = rObj["text"]?.jsonPrimitive?.contentOrNull
            }
        }

        val artistRunObj = meaningfulRuns.firstOrNull { rObj ->
            val browseId = rObj["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull
            browseId != null && (browseId.startsWith("UC") || browseId.startsWith("MPLAUC"))
        } ?: meaningfulRuns.firstOrNull { rObj ->
            val browseId = rObj["navigationEndpoint"]?.jsonObject
                ?.get("browseEndpoint")?.jsonObject
                ?.get("browseId")?.jsonPrimitive?.contentOrNull
            val txt = rObj["text"]?.jsonPrimitive?.contentOrNull ?: ""
            browseId == null && !txt.contains(":") && !txt.endsWith("visualizzazioni", ignoreCase = true) && !txt.endsWith("views", ignoreCase = true) && !txt.endsWith("riproduzioni", ignoreCase = true)
        } ?: meaningfulRuns.firstOrNull()

        val artistName = (artistRunObj
            ?.get("text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "Artista").removeSuffix(" - Topic").trim()

        val artistId = artistRunObj
            ?.get("navigationEndpoint")
            ?.jsonObject
            ?.get("browseEndpoint")
            ?.jsonObject
            ?.get("browseId")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.removePrefix("MPLA")

        var durationSec = 0L
        meaningfulRuns.lastOrNull()?.get("text")?.jsonPrimitive?.contentOrNull?.let { lastText ->
            if (lastText.contains(":")) {
                durationSec = parseDurationText(lastText)
            }
        }
        if (durationSec == 0L) {
            val fixedCols = renderer["fixedColumns"]?.jsonArray
            val fixedText = fixedCols?.firstOrNull()?.jsonObject
                ?.get("musicResponsiveListItemFixedColumnRenderer")?.jsonObject
                ?.get("text")?.jsonObject?.get("runs")?.jsonArray
                ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
            if (fixedText != null && fixedText.contains(":")) {
                durationSec = parseDurationText(fixedText)
            }
        }

        val thumbnails = renderer["thumbnail"]
            ?.jsonObject
            ?.get("musicThumbnailRenderer")
            ?.jsonObject
            ?.get("thumbnail")
            ?.jsonObject
            ?.get("thumbnails")
            ?.jsonArray

        val rawThumb = thumbnails?.lastOrNull()
            ?.jsonObject
            ?.get("url")
            ?.jsonPrimitive
            ?.contentOrNull
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        if (isAudiobookOrPodcast(title) || isAudiobookOrPodcast(artistName)) {
            return null
        }

        return Track(
            id = videoId,
            title = title,
            artistName = artistName,
            artistId = artistId,
            albumTitle = albumTitle,
            albumId = albumId,
            thumbnailUrl = rawThumb,
            durationSeconds = durationSec
        )
    }

    private fun extractMusicVideoType(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                element["musicVideoType"]?.jsonPrimitive?.contentOrNull?.let { return it }
                for ((_, child) in element) {
                    extractMusicVideoType(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    extractMusicVideoType(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun findWatchVideoId(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                element["watchEndpoint"]?.jsonObject?.get("videoId")?.jsonPrimitive?.contentOrNull?.let { return it }
                for ((_, child) in element) {
                    findWatchVideoId(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findWatchVideoId(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun findFirstHeaderTitle(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                for (key in listOf("musicDetailHeaderRenderer", "musicImmersiveHeaderRenderer", "musicResponsiveHeaderRenderer", "musicEditablePlaylistDetailHeaderRenderer")) {
                    val header = element[key]?.jsonObject ?: continue
                    val title = header["title"]?.jsonObject?.get("runs")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        ?: header["header"]?.jsonObject?.get("musicResponsiveHeaderRenderer")?.jsonObject
                            ?.get("title")?.jsonObject?.get("runs")?.jsonArray
                            ?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                    if (!title.isNullOrBlank()) return title
                }
                for ((_, child) in element) {
                    findFirstHeaderTitle(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findFirstHeaderTitle(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun findFirstHeaderThumbnail(element: JsonElement): String? {
        when (element) {
            is JsonObject -> {
                for (key in listOf("musicDetailHeaderRenderer", "musicImmersiveHeaderRenderer", "musicResponsiveHeaderRenderer")) {
                    val header = element[key]?.jsonObject ?: continue
                    val thumb = header["thumbnail"]?.jsonObject
                        ?.let { th ->
                            th["croppedSquareThumbnailRenderer"]?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                                ?: th["musicThumbnailRenderer"]?.jsonObject?.get("thumbnail")?.jsonObject?.get("thumbnails")?.jsonArray
                        }?.lastOrNull()?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    if (!thumb.isNullOrBlank()) return thumb
                }
                for ((_, child) in element) {
                    findFirstHeaderThumbnail(child)?.let { return it }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    findFirstHeaderThumbnail(item)?.let { return it }
                }
            }
            else -> Unit
        }
        return null
    }

    private fun parseDurationText(text: String): Long {
        val parts = text.trim().split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60L + parts[1]
            3 -> parts[0] * 3600L + parts[1] * 60L + parts[2]
            else -> 0L
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val FIVE_HOURS_MILLIS = 5L * 60L * 60L * 1000L
        // Filtri protobuf ufficiali di YouTube Music (RAW base64, NON URL-encoded!)
        private const val PARAMS_SONGS_ONLY = "EgWKAQIIAWoKEAkQBRAKEAMQBA=="
        private const val PARAMS_ALBUMS_ONLY = "EgWKAQIYAWoKEAkQBRAKEAMQBA=="
        private const val PARAMS_ARTISTS_ONLY = "EgWKAQIgAWoKEAkQBRAKEAMQBA=="
        private const val PARAMS_PLAYLISTS_ONLY = "EgWKAQIoAWoKEAkQBRAKEAMQBA=="

        val ALLOWED_AUDIO_ITAGS = setOf(251, 250, 249, 140, 139)

        fun isAudiobookOrPodcast(text: String?): Boolean {
            if (text.isNullOrBlank()) return false
            val lower = text.lowercase()
            return lower.contains("audiolibro") ||
                lower.contains("audiolibri") ||
                lower.contains("audiobook") ||
                lower.contains("audiobooks") ||
                lower.contains("podcast") ||
                lower.contains("capitolo ") ||
                lower.contains("chapter ") ||
                lower.contains("storie della buonanotte") ||
                lower.contains("favole") ||
                lower.contains("racconti audio")
        }

        fun formatPriority(itag: Int, mimeType: String): Int = when {
            itag == 251 -> 100
            itag == 250 -> 90
            itag == 140 -> 80
            itag == 249 -> 70
            mimeType.contains("opus", ignoreCase = true) -> 60
            mimeType.contains("mp4a", ignoreCase = true) -> 50
            else -> 10
        }
    }

    private enum class ClientProfile(
        val clientName: String,
        val clientVersion: String,
        val clientIdHeader: String,
        val userAgent: String,
        val androidSdkVersion: Int?,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val sendAuth: Boolean
    ) {
        ANDROID_VR(
            clientName = "ANDROID_VR",
            clientVersion = "1.62.27",
            clientIdHeader = "28",
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.62.27 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            androidSdkVersion = 32,
            osName = "Android",
            osVersion = "12L",
            deviceMake = "Oculus",
            deviceModel = "Quest 3",
            sendAuth = false
        ),
        IOS(
            clientName = "IOS",
            clientVersion = "20.03.02",
            clientIdHeader = "5",
            userAgent = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)",
            androidSdkVersion = null,
            osName = "iPhone",
            osVersion = "18.2.1.22C161",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
            sendAuth = false
        ),
        ANDROID_TESTSUITE(
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientIdHeader = "30",
            userAgent = "com.google.android.youtube/1.9 (Linux; U; Android 14) gzip",
            androidSdkVersion = 34,
            osName = "Android",
            osVersion = "14",
            sendAuth = false
        ),
        WEB_REMIX_AUTH(
            clientName = "WEB_REMIX",
            clientVersion = "1.20250120.01.00",
            clientIdHeader = "67",
            userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
            androidSdkVersion = null,
            sendAuth = true
        )
    }
}
