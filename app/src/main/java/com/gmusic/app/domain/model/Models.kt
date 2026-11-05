package com.gmusic.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val albumTitle: String? = null,
    val albumId: String? = null,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0L,
    val isLiked: Boolean = false,
    val isDownloaded: Boolean = false
) {
    /**
     * Restituisce l'URL della copertina in alta definizione per il Player (w800-h800 / maxresdefault).
     */
    val hdArtworkUrl: String
        get() = upgradeThumbnailUrl(thumbnailUrl)

    companion object {
        fun upgradeThumbnailUrl(raw: String): String = when {
            raw.contains("=w") -> raw.replace(Regex("=w\\d+-h\\d+[^&]*"), "=w1200-h1200-l90-rj")
            raw.contains("=s") -> raw.replace(Regex("=s\\d+[^&]*"), "=w1200-h1200-l90-rj")
            raw.contains("/default.jpg") -> raw.replace("/default.jpg", "/hqdefault.jpg")
            raw.contains("/mqdefault.jpg") -> raw.replace("/mqdefault.jpg", "/hqdefault.jpg")
            else -> raw
        }
    }
}

@Serializable
data class AlbumSummary(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val year: String? = null,
    val thumbnailUrl: String
) {
    val hdArtworkUrl: String get() = Track.upgradeThumbnailUrl(thumbnailUrl)
}

@Serializable
data class ArtistSummary(
    val id: String,
    val name: String,
    val subscribersText: String? = null,
    val thumbnailUrl: String
) {
    val hdArtworkUrl: String get() = Track.upgradeThumbnailUrl(thumbnailUrl)
}

@Serializable
data class PlaylistSummary(
    val id: String,
    val title: String,
    val author: String? = null,
    val trackCount: Int = 0,
    val thumbnailUrls: List<String> = emptyList(),
    val isLocal: Boolean = true
)

@Serializable
data class AlbumDetail(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String? = null,
    val year: String? = null,
    val thumbnailUrl: String,
    val tracks: List<Track> = emptyList()
) {
    val hdArtworkUrl: String get() = Track.upgradeThumbnailUrl(thumbnailUrl)
}

@Serializable
data class ArtistDetail(
    val id: String,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String,
    val popularTracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList(),
    val singles: List<AlbumSummary> = emptyList(),
    val similarArtists: List<ArtistSummary> = emptyList(),
    val discography: List<Track> = emptyList()
) {
    val hdArtworkUrl: String get() = Track.upgradeThumbnailUrl(thumbnailUrl)
}

@Serializable
enum class HomeItemType {
    SONG,
    MIX,
    ALBUM,
    PLAYLIST,
    ARTIST
}

@Serializable
data class HomeShelfItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val type: HomeItemType,
    val track: Track? = null,
    val tracks: List<Track> = emptyList()
) {
    val hdArtworkUrl: String get() = Track.upgradeThumbnailUrl(thumbnailUrl)
}

fun Track.toSongShelfItem(): HomeShelfItem = HomeShelfItem(
    id = id,
    title = title,
    subtitle = artistName,
    thumbnailUrl = hdArtworkUrl,
    type = HomeItemType.SONG,
    track = this,
    tracks = listOf(this)
)

@Serializable
data class HomeSection(
    val id: String = "",
    val title: String,
    val subtitle: String? = null,
    val isSongShelf: Boolean = true,
    val items: List<HomeShelfItem> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val albums: List<AlbumSummary> = emptyList()
)

@Serializable
data class MoodGenreChip(
    val title: String,
    val query: String,
    val colorHex: Long
)
