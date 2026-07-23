package com.fearmikey.projectreporter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToPdf(project: ProjectEntity, photos: List<PhotoEntity>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val titlePaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 24f
            }
            val textPaint = Paint().apply {
                textSize = 14f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create() // A4 size in points
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Header
            canvas.drawText("Site Service Report", 40f, 50f, titlePaint)
            canvas.drawText("Project: ${project.projectName} (${project.projectId})", 40f, 80f, textPaint)
            canvas.drawText("Engineer: ${project.engineerName}", 40f, 100f, textPaint)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.timestamp))
            canvas.drawText("Date: $dateStr", 40f, 120f, textPaint)

            var yPos = 160f
            val margin = 40f
            val contentWidth = pageInfo.pageWidth - 2 * margin

            for (photo in photos) {
                // Check if we need a new page
                if (yPos + 300f > pageInfo.pageHeight - margin) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    yPos = margin
                }

                // Draw Photo
                val bitmap = loadBitmapFromUri(photo.imageUri)
                if (bitmap != null) {
                    val scaledBitmap = scaleBitmap(bitmap, contentWidth.toInt(), 200)
                    canvas.drawBitmap(scaledBitmap, margin, yPos, paint)
                    yPos += scaledBitmap.height + 10f
                }

                // Draw Timestamp and Annotation
                canvas.drawText("Time: ${photo.timestampOverlay}", margin, yPos, textPaint)
                yPos += 20f
                if (photo.annotation.isNotEmpty()) {
                    val lines = photo.annotation.split("\n")
                    for (line in lines) {
                        canvas.drawText(line, margin, yPos, textPaint)
                        yPos += 18f
                    }
                }
                yPos += 20f // Spacing between photo entries
            }

            pdfDocument.finishPage(page)

            // Save to MediaStore
            val fileName = "Report_${project.projectId}_${System.currentTimeMillis()}.pdf"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }

            pdfDocument.close()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun loadBitmapFromUri(uriStr: String): Bitmap? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uriStr.toUri())
            BitmapFactory.decodeStream(inputStream)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleBitmap(source: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = Math.min(maxWidth.toFloat() / source.width, maxHeight.toFloat() / source.height)
        val width = (source.width * ratio).toInt()
        val height = (source.height * ratio).toInt()
        return source.scale(width, height, true)
    }
}
