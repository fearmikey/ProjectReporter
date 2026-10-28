package com.fearmikey.projectreporter.util

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.widget.Toast

/**
 * Invisible trampoline launched from the export notification's "Open Folder" action.
 *
 * There is no single intent that opens the Downloads folder on every device, and a
 * notification PendingIntent with no matching handler fails silently. This activity
 * tries the known intents in order, launches the first one that works, then finishes.
 */
class OpenDownloadsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val opened = candidateIntents().any { tryStart(it) }
        if (!opened) {
            Toast.makeText(
                this,
                "No file manager found. Reports are saved in your Downloads folder.",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }

    private fun candidateIntents(): List<Intent> {
        val downloadsPath = Environment
            .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            .absolutePath

        return listOf(
            // AOSP / Google Files (DocumentsUI): Downloads root
            Intent(Intent.ACTION_VIEW).setDataAndType(
                DocumentsContract.buildRootUri(DOWNLOADS_AUTHORITY, "downloads"),
                ROOT_MIME_TYPE,
            ),
            // AOSP / Google Files (DocumentsUI): Download directory on primary storage
            Intent(Intent.ACTION_VIEW).setDataAndType(
                DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, "primary:Download"),
                DocumentsContract.Document.MIME_TYPE_DIR,
            ),
            // Samsung My Files
            Intent("samsung.myfiles.intent.action.LAUNCH_MY_FILES")
                .setPackage("com.sec.android.app.myfiles")
                .putExtra("samsung.myfiles.intent.extra.START_PATH", downloadsPath),
            // Generic system "Downloads" screen
            Intent(DownloadManager.ACTION_VIEW_DOWNLOADS),
        )
    }

    private fun tryStart(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private companion object {
        const val DOWNLOADS_AUTHORITY = "com.android.providers.downloads.documents"
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
        const val ROOT_MIME_TYPE = "vnd.android.document/root"
    }
}
