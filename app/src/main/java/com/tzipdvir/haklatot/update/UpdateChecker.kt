package com.tzipdvir.haklatot.update

import android.content.Context
import com.tzipdvir.haklatot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Information about a published version, read from version.json on GitHub Pages. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val notes: String,
)

sealed interface CheckResult {
    data class Available(val info: UpdateInfo) : CheckResult
    data object UpToDate : CheckResult
    data object Failed : CheckResult
}

/**
 * Checks GitHub Pages for a newer version.
 * The ONLY network request the app makes: a plain GET of a small public file. Nothing is sent.
 */
object UpdateChecker {
    const val PAGE_URL = "https://tzipdvir-afk.github.io/haklatot/"
    private const val VERSION_URL = PAGE_URL + "version.json"
    private const val MIN_INTERVAL_MS = 3L * 60 * 60 * 1000 // automatic checks at most every 3 hours

    private const val PREFS = "update"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LAST_JSON = "last_json"

    /**
     * @param force true when the user tapped "check for updates" (ignores the 3-hour limit).
     */
    suspend fun check(context: Context, force: Boolean): CheckResult = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cachedJson = prefs.getString(KEY_LAST_JSON, null)

        val json: String? = if (!force && cachedJson != null && now - prefs.getLong(KEY_LAST_CHECK, 0) < MIN_INTERVAL_MS) {
            cachedJson
        } else {
            fetch()?.also { prefs.edit().putLong(KEY_LAST_CHECK, now).putString(KEY_LAST_JSON, it).apply() }
        }

        if (json == null) return@withContext CheckResult.Failed
        val info = parse(json) ?: return@withContext CheckResult.Failed
        if (info.versionCode > BuildConfig.VERSION_CODE) CheckResult.Available(info) else CheckResult.UpToDate
    }

    private fun fetch(): String? = try {
        // The timestamp avoids getting an old cached copy of the file.
        val conn = URL("$VERSION_URL?t=${System.currentTimeMillis()}").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        try {
            if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    } catch (e: Exception) {
        null
    }

    private fun parse(json: String): UpdateInfo? = try {
        val o = JSONObject(json)
        UpdateInfo(
            versionCode = o.getInt("versionCode"),
            versionName = o.getString("versionName"),
            apkUrl = o.getString("apkUrl"),
            notes = o.optString("notes", ""),
        )
    } catch (e: Exception) {
        null
    }
}
