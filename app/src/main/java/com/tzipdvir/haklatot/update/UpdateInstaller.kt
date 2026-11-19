package com.tzipdvir.haklatot.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import java.io.File

/** Progress of downloading an update, shown in the banner. */
sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val percent: Int) : DownloadState
    data object Failed : DownloadState
}

/**
 * Downloads the new APK with Android's DownloadManager into the app's private folder,
 * then hands it to Android's own installer screen. The user always confirms the install.
 */
class UpdateInstaller(private val context: Context) {

    private val dm = context.getSystemService(DownloadManager::class.java)
    private val dir: File get() = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!

    /** Android requires the user to allow this app to install updates (one time). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Fallback: open the download page in the browser. */
    fun openDownloadPage() {
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(UpdateChecker.PAGE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: ActivityNotFoundException) {
        }
    }

    /**
     * Downloads the APK, reporting progress, and opens the installer when done.
     * Returns true if the installer was opened.
     */
    suspend fun downloadAndInstall(info: UpdateInfo, onProgress: (DownloadState) -> Unit): Boolean {
        // Remove leftovers from earlier updates.
        dir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { it.delete() }
        val file = File(dir, "haklatot-${info.versionName}.apk")

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("ההקלטות שלי – גרסה ${info.versionName}")
            .setDescription("מוריד עדכון")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(file))
        val id = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            onProgress(DownloadState.Failed)
            return false
        }

        onProgress(DownloadState.Downloading(0))
        while (true) {
            delay(500)
            val cursor = dm.query(DownloadManager.Query().setFilterById(id)) ?: break
            cursor.use {
                if (!it.moveToFirst()) {
                    onProgress(DownloadState.Failed); return false
                }
                val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val done = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        onProgress(DownloadState.Idle)
                        return openInstaller(file)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        onProgress(DownloadState.Failed); return false
                    }
                    else -> if (total > 0) onProgress(DownloadState.Downloading((done * 100 / total).toInt()))
                }
            }
        }
        onProgress(DownloadState.Failed)
        return false
    }

    private fun openInstaller(file: File): Boolean = try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }
}
