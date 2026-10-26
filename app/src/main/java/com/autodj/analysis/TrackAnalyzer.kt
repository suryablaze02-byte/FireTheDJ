package com.autodj.analysis

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.provider.OpenableColumns
import com.autodj.model.TrackInfo
import com.autodj.remote.RemoteTrack

object TrackAnalyzer {
    /**
     * Analyses one track. [known] carries the names and genre a streaming provider supplied;
     * a stream has no file tags to read (and fetching them would download it twice). [audio] is
     * where to read the sound from when it isn't [uri] itself (a stream's stored copy).
     */
    fun analyze(context: Context, uri: Uri, known: RemoteTrack? = null, audio: Uri = uri): TrackInfo {
        val (tagTitle, tagArtist, tagGenre) = if (known != null) Triple(known.title, known.artist, known.genre) else readTags(context, uri)
        // Untagged files are often named "Artist - Title".
        val fileName = displayName(context, uri)
        val split = fileName.split(" - ", limit = 2).takeIf { it.size == 2 }
        val title = tagTitle?.takeIf { it.isNotBlank() } ?: split?.get(1)?.trim() ?: fileName
        val artist = tagArtist?.takeIf { it.isNotBlank() } ?: split?.get(0)?.trim().orEmpty()

        val t0 = SystemClock.elapsedRealtime()
        val pcm = PcmDecoder.decodeMono(context, audio)
        val t1 = SystemClock.elapsedRealtime()
        val beat = BeatAnalyzer.analyze(pcm.samples, pcm.sampleRate)
        val h = beat.harmony
        Log.i(
            "AutoDJ",
            "analysed \"$title\" (${pcm.durationSec.toInt()} s): decode ${t1 - t0} ms, analysis ${SystemClock.elapsedRealtime() - t1} ms · " +
                "${"%.1f".format(beat.bpm)} BPM · key ${Camelot.code(h.keyPc, h.minor)} (${"%.2f".format(h.keyConfidence)}) · " +
                "hook bar ${h.hookBar} · vocal bars ${beat.vocalness.count { it >= 0.55f }}/${beat.vocalness.size} · " +
                "tempo map ${beat.tempoMap.joinToString(" ") { if (it > 0f) "%.0f".format(it) else "-" }}",
        )
        val genre = GenreClassifier.classify(beat.features, beat.bpm, Genres.clean(tagGenre))
        return TrackInfo(
            uri = uri.toString(),
            title = title,
            artist = artist,
            genre = genre.label,
            genreFamily = genre.family,
            genreConfidence = genre.confidence,
            genreSource = genre.source,
            durationSec = pcm.durationSec,
            bpm = beat.bpm,
            downbeatSec = beat.downbeatSec,
            phraseOffsetBars = beat.phraseOffsetBars,
            barEnergy = beat.barEnergy,
            introEndBar = beat.introEndBar,
            outroStartBar = beat.outroStartBar,
            dropBars = beat.dropBars,
            loudnessDb = beat.loudnessDb,
            features = beat.features,
            vocalness = beat.vocalness,
            keyPc = beat.harmony.keyPc,
            keyMinor = beat.harmony.minor,
            keyConfidence = beat.harmony.keyConfidence,
            hookBar = beat.harmony.hookBar,
            tempoMap = beat.tempoMap,
        )
    }

    private fun readTags(context: Context, uri: Uri): Triple<String?, String?, String?> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Triple(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE),
            )
        } catch (e: RuntimeException) {
            Triple(null, null, null)
        } finally {
            retriever.release()
        }
    }

    fun displayName(context: Context, uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0).substringBeforeLast('.')
            }
        } catch (_: Exception) {
            // Permission revoked or file gone; the analysis step will report it.
        }
        return uri.lastPathSegment ?: "Unknown"
    }
}
