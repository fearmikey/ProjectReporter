package com.fearmikey.projectreporter.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.fearmikey.projectreporter.data.model.ExportFormat

@Singleton
class NotificationService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "report_export_channel"

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Report Exports",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed report exports"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showExportCompleteNotification(uri: Uri, format: ExportFormat) {
        val formatName = if (format == ExportFormat.PDF) "PDF" else "DOCX"
        
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, if (format == ExportFormat.PDF) "application/pdf" else "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Routed through a trampoline that tries several device-specific file manager intents,
        // since no single intent reliably opens the Downloads folder on all OEMs.
        val folderIntent = Intent(context, OpenDownloadsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val folderPendingIntent = PendingIntent.getActivity(
            context,
            1,
            folderIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("$formatName Exported")
            .setContentText("Report saved successfully. Tap to view.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_menu_view, "Open Folder", folderPendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
