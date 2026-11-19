package com.tzipdvir.haklatot.data

import android.content.Context
import org.json.JSONObject

/** Why a recording ended. */
enum class StopReason(val label: String?) {
    USER(null),
    SILENCE("נעצרה אוטומטית אחרי שקט ממושך"),
    BATTERY("נעצרה כי הסוללה כמעט נגמרה"),
    STORAGE("נעצרה כי נגמר המקום בטלפון"),
    SHUTDOWN("נעצרה כי הטלפון נכבה"),
    ERROR("נעצרה בגלל תקלה"),
}

/** Extra information we keep about each recording (by MediaStore id). */
data class RecordingMeta(
    val bitrateKbps: Int,
    /** Background noise level (dBFS, 10th percentile of per-second levels). Null if unknown. */
    val noiseDb: Int? = null,
    val stopReason: StopReason? = null,
)

/** Small key-value store for [RecordingMeta] (SharedPreferences, one JSON per recording). */
class RecordingMetaStore(context: Context) {
    private val prefs = context.getSharedPreferences("recording_meta", Context.MODE_PRIVATE)

    fun get(id: Long): RecordingMeta? = prefs.getString(id.toString(), null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            RecordingMeta(
                bitrateKbps = o.getInt("bitrate"),
                noiseDb = if (o.has("noise")) o.getInt("noise") else null,
                stopReason = o.optString("reason").takeIf { it.isNotEmpty() }
                    ?.let { r -> runCatching { StopReason.valueOf(r) }.getOrNull() },
            )
        }.getOrNull()
    }

    fun put(id: Long, meta: RecordingMeta) {
        val o = JSONObject().put("bitrate", meta.bitrateKbps)
        meta.noiseDb?.let { o.put("noise", it) }
        meta.stopReason?.let { o.put("reason", it.name) }
        prefs.edit().putString(id.toString(), o.toString()).commit()
    }
}
