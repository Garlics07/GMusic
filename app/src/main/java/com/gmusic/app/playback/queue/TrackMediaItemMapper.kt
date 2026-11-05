package com.gmusic.app.playback.queue

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.gmusic.app.domain.model.Track

object TrackMediaItemMapper {
    const val SCHEME_GMUSIC = "gmusic"
    const val HOST_TRACK = "track"
    const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
    const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
    const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
    const val CONTENT_STYLE_GROUP_TITLE_HINT = "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
    const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
    const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2
    const val EXTRA_ARTIST_CONTEXT_NAME = "artist_context_name"
    const val EXTRA_ARTIST_CONTEXT_ID = "artist_context_id"

    private const val EXTRA_ARTIST_ID = "artist_id"
    private const val EXTRA_ALBUM_ID = "album_id"
    private const val EXTRA_DURATION_SEC = "duration_sec"
    private const val EXTRA_THUMBNAIL_URL = "thumbnail_url"

    fun toMediaItem(
        track: Track,
        artworkBytes: ByteArray? = null,
        groupTitle: String? = null,
        artistContextName: String? = null,
        artistContextId: String? = null
    ): MediaItem {
        val extras = Bundle().apply {
            track.artistId?.let { putString(EXTRA_ARTIST_ID, it) }
            track.albumId?.let { putString(EXTRA_ALBUM_ID, it) }
            putLong(EXTRA_DURATION_SEC, track.durationSeconds)
            putString(EXTRA_THUMBNAIL_URL, track.thumbnailUrl)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM_HINT_VALUE)
            if (!groupTitle.isNullOrBlank()) {
                putString(CONTENT_STYLE_GROUP_TITLE_HINT, groupTitle)
            }
            if (!artistContextName.isNullOrBlank()) {
                putString(EXTRA_ARTIST_CONTEXT_NAME, artistContextName)
            }
            if (!artistContextId.isNullOrBlank()) {
                putString(EXTRA_ARTIST_CONTEXT_ID, artistContextId)
            }
        }

        val artworkUrl = track.hdArtworkUrl.ifBlank { track.thumbnailUrl }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title)
            .setDisplayTitle(track.title)
            .setArtist(track.artistName)
            .setSubtitle(track.artistName)
            .setAlbumTitle(track.albumTitle ?: track.artistName)
            .setAlbumArtist(track.artistName)
            .setArtworkUri(Uri.parse(artworkUrl))
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setExtras(extras)

        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            metadataBuilder.setArtworkData(artworkBytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(Uri.parse("$SCHEME_GMUSIC://$HOST_TRACK/${track.id}"))
            .setCustomCacheKey(track.id)
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    fun fromMediaItem(item: MediaItem): Track {
        val meta = item.mediaMetadata
        val extras = meta.extras
        val thumb = extras?.getString(EXTRA_THUMBNAIL_URL)
            ?: meta.artworkUri?.toString()
            ?: "https://i.ytimg.com/vi/${item.mediaId}/hqdefault.jpg"
        return Track(
            id = item.mediaId,
            title = meta.title?.toString() ?: "Brano sconosciuto",
            artistName = meta.artist?.toString() ?: "Artista sconosciuto",
            artistId = extras?.getString(EXTRA_ARTIST_ID),
            albumTitle = meta.albumTitle?.toString(),
            albumId = extras?.getString(EXTRA_ALBUM_ID),
            thumbnailUrl = thumb,
            durationSeconds = extras?.getLong(EXTRA_DURATION_SEC) ?: 0L
        )
    }
}
