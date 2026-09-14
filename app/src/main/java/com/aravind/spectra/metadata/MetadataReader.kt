package com.aravind.spectra.metadata

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Reads what Android's built-in MediaMetadataRetriever exposes: title,
 * artist, album, genre, year, track/disc numbers, bitrate, duration, and
 * embedded cover art. This is a phase-1 feature set.
 *
 * NOT covered (MediaMetadataRetriever doesn't expose these): ISRC,
 * composer/copyright on some containers, freeform comment fields. Getting
 * full parity with desktop tag readers (like the ISRC/comment rows in
 * the reference screenshots) would need a dedicated tag-parsing library
 * (e.g. jaudiotagger) layered on top of this — worth adding later if
 * those fields matter to you, but left out of this first pass rather
 * than guessing at binary tag-parsing code that can't be verified here.
 */
object MetadataReader {

    data class Tags(
        val title: String?,
        val artist: String?,
        val album: String?,
        val genre: String?,
        val year: String?,
        val trackNumber: String?,
        val discNumber: String?,
        val bitrateBps: Long?,
        val durationMs: Long?,
        val mimeType: String?,
        val coverArt: ByteArray?
    )

    fun read(context: Context, uri: Uri): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            fun get(key: Int): String? = retriever.extractMetadata(key)
            Tags(
                title = get(MediaMetadataRetriever.METADATA_KEY_TITLE),
                artist = get(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST),
                album = get(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                genre = get(MediaMetadataRetriever.METADATA_KEY_GENRE),
                year = get(MediaMetadataRetriever.METADATA_KEY_YEAR)
                    ?: get(MediaMetadataRetriever.METADATA_KEY_DATE),
                trackNumber = get(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                discNumber = get(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER),
                bitrateBps = get(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull(),
                durationMs = get(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                mimeType = get(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                coverArt = retriever.embeddedPicture
            )
        } finally {
            retriever.release()
        }
    }
}
