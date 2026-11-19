package com.tzipdvir.haklatot.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One recording as shown in the list. */
data class Recording(
    val id: Long,
    val uri: Uri,
    val title: String,
    val dateMillis: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val recovered: Boolean,
    /** Background noise in dBFS (temporary diagnostic for choosing the silence threshold). */
    val noiseDb: Int? = null,
    val stopReason: StopReason? = null,
)

/**
 * Recordings are real MP3 files in the public folder "Recordings/ההקלטות שלי"
 * (visible in Samsung "My Files"), managed through Android's MediaStore.
 * While a recording is in progress the file is marked "pending" (hidden from other apps).
 */
class RecordingsStore(private val context: Context) {

    private val resolver = context.contentResolver
    private val collection: Uri = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    private val prefs = context.getSharedPreferences("recordings", Context.MODE_PRIVATE)

    /** Creates a new, pending MP3 entry and returns its Uri. */
    fun createPending(startMillis: Long): Uri {
        val stamp = SimpleDateFormat("dd.MM.yyyy – HH.mm", Locale.US).format(Date(startMillis))
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "הקלטה – $stamp.mp3")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Audio.Media.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
        prefs.edit().putString(KEY_ACTIVE, uri.toString()).commit()
        return uri
    }

    /** Marks the recording as finished: visible to other apps, duration gets indexed. */
    fun finish(uri: Uri) {
        val values = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
        resolver.update(uri, values, null, null)
        prefs.edit().remove(KEY_ACTIVE).commit()
    }

    /**
     * After a crash / forced stop: finds recordings that were never closed and finishes them,
     * so the part that was written stays playable and visible. Returns how many were recovered.
     * Must only be called when no recording is running.
     */
    fun recoverUnfinished(): Int {
        val pendingIds = mutableListOf<Long>()
        queryPending()?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            while (c.moveToNext()) pendingIds += c.getLong(idCol)
        }
        // Also the one remembered as "active" (in case the query above missed it).
        prefs.getString(KEY_ACTIVE, null)?.let { s ->
            runCatching { ContentUris.parseId(Uri.parse(s)) }.getOrNull()?.let { if (it !in pendingIds) pendingIds += it }
        }
        var recovered = 0
        val recoveredSet = prefs.getStringSet(KEY_RECOVERED, emptySet())!!.toMutableSet()
        for (id in pendingIds) {
            val uri = ContentUris.withAppendedId(collection, id)
            val ok = runCatching {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            }.getOrDefault(0) > 0
            if (ok) {
                recovered++
                recoveredSet += id.toString()
            }
        }
        prefs.edit().putStringSet(KEY_RECOVERED, recoveredSet).remove(KEY_ACTIVE).commit()
        return recovered
    }

    /** All finished recordings in our folder, newest first. */
    fun list(): List<Recording> {
        val recoveredSet = prefs.getStringSet(KEY_RECOVERED, emptySet())!!
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
        )
        val selection = "${MediaStore.Audio.Media.RELATIVE_PATH} = ?"
        val result = mutableListOf<Recording>()
        resolver.query(
            collection, projection, selection, arrayOf(RELATIVE_PATH),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val metaStore = RecordingMetaStore(context)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val size = c.getLong(sizeCol)
                val meta = metaStore.get(id)
                // Constant-bitrate MP3: exact duration from file size (also right after a crash).
                val duration = when {
                    meta != null -> size * 8 / meta.bitrateKbps
                    c.getLong(durCol) > 0 -> c.getLong(durCol)
                    else -> size * 8 / DEFAULT_BITRATE_KBPS
                }
                result += Recording(
                    noiseDb = meta?.noiseDb,
                    stopReason = meta?.stopReason,
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    title = c.getString(nameCol).removeSuffix(".mp3"),
                    dateMillis = c.getLong(dateCol) * 1000,
                    durationMs = duration,
                    sizeBytes = size,
                    recovered = id.toString() in recoveredSet,
                )
            }
        }
        return result
    }

    private fun queryPending() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val args = Bundle().apply {
            putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, "${MediaStore.Audio.Media.RELATIVE_PATH} = ?")
            putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf(RELATIVE_PATH))
            putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_ONLY)
        }
        resolver.query(collection, arrayOf(MediaStore.Audio.Media._ID), args, null)
    } else {
        @Suppress("DEPRECATION")
        resolver.query(
            MediaStore.setIncludePending(collection), arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.IS_PENDING} = 1",
            arrayOf(RELATIVE_PATH), null,
        )
    }

    companion object {
        const val DEFAULT_BITRATE_KBPS = 64
        private const val KEY_ACTIVE = "active_uri"
        private const val KEY_RECOVERED = "recovered_ids"

        // "Recordings/" is allowed for audio from Android 12; older versions use "Music/".
        val RELATIVE_PATH: String =
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Environment.DIRECTORY_RECORDINGS else Environment.DIRECTORY_MUSIC) +
                "/ההקלטות שלי/"
    }
}
