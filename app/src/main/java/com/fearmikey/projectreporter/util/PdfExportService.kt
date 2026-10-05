package com.fearmikey.projectreporter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.model.ExportOptions
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
    suspend fun exportToPdf(
        project: ProjectEntity,
        photos: List<PhotoEntity>,
        notes: List<NoteEntity>,
        options: ExportOptions
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val qualityMultiplier = 3f
            val pdfDocument = PdfDocument()
            val paint = Paint()
            val textPaint = Paint().apply {
                textSize = 14f
            }
            val boldTextPaint = Paint().apply {
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = 14f
            }

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create() // A4 size in points
            var page = pdfDocument.startPage(pageInfo)
            var canvas = page.canvas

            // Initial Header
            drawPageFrame(canvas, pageInfo, project, pageNumber)

            var yPos = 140f
            val margin = 40f
            val contentWidth = pageInfo.pageWidth - 2 * margin

            // Draw Notes
            if (options.includeNotes && notes.isNotEmpty()) {
                canvas.drawText("General Notes:", margin, yPos, boldTextPaint)
                yPos += 24f
                for (note in notes) {
                    val wrappedLines = wrapText(note.content, textPaint, contentWidth - 15f)
                    
                    for (line in wrappedLines) {
                        if (yPos + 20f > pageInfo.pageHeight - 60f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawPageFrame(canvas, pageInfo, project, pageNumber)
                            yPos = 100f
                        }
                        canvas.drawText("• $line", margin, yPos, textPaint)
                        yPos += 18f
                    }
                    yPos += 8f
                }
                yPos += 20f
            }

            // Draw Photos
            if (photos.isNotEmpty()) {
                // Ensure some space for "Photo Observations" label
                if (yPos + 50f > pageInfo.pageHeight - 60f) {
                    pdfDocument.finishPage(page)
                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas
                    drawPageFrame(canvas, pageInfo, project, pageNumber)
                    yPos = 100f
                }

                canvas.drawText("Photo Observations:", margin, yPos, boldTextPaint)
                yPos += 24f

                if (options.photosPerPage == 1) {
                    // Flowing layout
                    for (photo in photos) {
                        val bitmap = loadBitmapFromUri(photo.imageUri) ?: continue
                        val rotatedBitmap = rotateBitmapIfNecessary(bitmap, photo.imageUri)
                        val scaledBitmap = scaleBitmap(rotatedBitmap, (contentWidth * qualityMultiplier).toInt(), (450 * qualityMultiplier).toInt())
                        val logicalWidth = scaledBitmap.width / qualityMultiplier
                        val logicalHeight = scaledBitmap.height / qualityMultiplier
                        
                        val wrappedAnnotations = if (photo.annotation.isNotEmpty()) {
                            wrapText(photo.annotation, textPaint, contentWidth)
                        } else emptyList()
                        
                        var textHeight = 0f
                        if (options.includeTimestamp) textHeight += 20f
                        textHeight += wrappedAnnotations.size * 18f
                        
                        val requiredHeight = logicalHeight + textHeight + 40f
                        if (yPos + requiredHeight > pageInfo.pageHeight - 60f) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawPageFrame(canvas, pageInfo, project, pageNumber)
                            yPos = 100f
                        }

                        canvas.drawBitmap(scaledBitmap, null, RectF(margin, yPos, margin + logicalWidth, yPos + logicalHeight), paint)
                        yPos += logicalHeight + 12f

                        if (options.includeTimestamp) {
                            canvas.drawText("Captured: ${photo.timestampOverlay}", margin, yPos, textPaint)
                            yPos += 20f
                        }
                        for (line in wrappedAnnotations) {
                            canvas.drawText(line, margin, yPos, textPaint)
                            yPos += 18f
                        }
                        yPos += 30f // Spacing between photo entries
                    }
                } else {
                    // Grid layout (2 or 4 per page)
                    val cols = if (options.photosPerPage == 4) 2 else 1
                    val rows = 2
                    val cellWidth = (contentWidth - (cols - 1) * 20f) / cols
                    val cellHeight = (pageInfo.pageHeight - 160f) / rows
                    
                    // Start photos on a new page if we are too far down
                    if (yPos > 200f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawPageFrame(canvas, pageInfo, project, pageNumber)
                        yPos = 100f
                    }

                    var photoIndex = 0
                    for (photo in photos) {
                        val bitmap = loadBitmapFromUri(photo.imageUri) ?: continue
                        
                        if (photoIndex > 0 && photoIndex % options.photosPerPage == 0) {
                            pdfDocument.finishPage(page)
                            pageNumber++
                            pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                            page = pdfDocument.startPage(pageInfo)
                            canvas = page.canvas
                            drawPageFrame(canvas, pageInfo, project, pageNumber)
                            yPos = 100f
                        }

                        val localIndex = photoIndex % options.photosPerPage
                        val col = localIndex % cols
                        val row = localIndex / cols
                        
                        val x = margin + col * (cellWidth + 20f)
                        val y = 100f + row * cellHeight
                        
                        val rotatedBitmap = rotateBitmapIfNecessary(bitmap, photo.imageUri)
                        
                        // Metadata height estimation
                        val smallTextPaint = Paint(textPaint).apply { textSize = 10f }
                        val wrappedAnnotations = if (photo.annotation.isNotEmpty()) {
                            wrapText(photo.annotation, smallTextPaint, cellWidth)
                        } else emptyList()
                        
                        var metadataHeight = 0f
                        if (options.includeTimestamp) metadataHeight += 14f
                        metadataHeight += Math.min(wrappedAnnotations.size, 8) * 14f
                        
                        val maxImageHeight = cellHeight - metadataHeight - 20f
                        val scaledBitmap = scaleBitmap(rotatedBitmap, (cellWidth * qualityMultiplier).toInt(), (maxImageHeight * qualityMultiplier).toInt())
                        val logicalWidth = scaledBitmap.width / qualityMultiplier
                        val logicalHeight = scaledBitmap.height / qualityMultiplier
                        
                        canvas.drawBitmap(scaledBitmap, null, RectF(x, y, x + logicalWidth, y + logicalHeight), paint)
                        var currentY = y + logicalHeight + 10f
                        
                        if (options.includeTimestamp) {
                            canvas.drawText("Captured: ${photo.timestampOverlay}", x, currentY, smallTextPaint)
                            currentY += 14f
                        }
                        
                        for (i in 0 until Math.min(wrappedAnnotations.size, 8)) {
                            canvas.drawText(wrappedAnnotations[i], x, currentY, smallTextPaint)
                            currentY += 14f
                        }
                        
                        photoIndex++
                    }
                }
            }

            pdfDocument.finishPage(page)

            // Save to MediaStore
            val idPart = project.displayProjectNumber?.let { "_$it" } ?: ""
            val fileName = "Report${idPart}_${System.currentTimeMillis()}.pdf"
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

    private fun drawPageFrame(
        canvas: Canvas,
        pageInfo: PdfDocument.PageInfo,
        project: ProjectEntity,
        pageNumber: Int
    ) {
        val margin = 40f
        val paint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val headerPaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.GRAY
        }
        val titlePaint = Paint().apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 20f
        }
        val subTitlePaint = Paint().apply {
            textSize = 12f
            color = Color.DKGRAY
        }

        // Header Line
        canvas.drawLine(margin, 30f, pageInfo.pageWidth - margin, 30f, paint)
        canvas.drawText("Site Service Report", margin, 25f, headerPaint)
        canvas.drawText("Project: ${project.projectName}", pageInfo.pageWidth - margin - headerPaint.measureText("Project: ${project.projectName}"), 25f, headerPaint)

        // Footer Line
        canvas.drawLine(margin, pageInfo.pageHeight - 30f, pageInfo.pageWidth - margin, pageInfo.pageHeight - 30f, paint)
        canvas.drawText("Generated by Project Reporter", margin, pageInfo.pageHeight - 15f, headerPaint)
        canvas.drawText("Page $pageNumber", pageInfo.pageWidth - margin - headerPaint.measureText("Page $pageNumber"), pageInfo.pageHeight - 15f, headerPaint)

        // Project Info (only on page 1)
        if (pageNumber == 1) {
            canvas.drawText("SITE SERVICE REPORT", margin, 70f, titlePaint)
            val projectLabel = project.displayProjectNumber?.let { "Project: ${project.projectName} ($it)" } ?: "Project: ${project.projectName}"
            canvas.drawText(projectLabel, margin, 95f, subTitlePaint)
            canvas.drawText("Engineer: ${project.engineerName}", margin, 115f, subTitlePaint)
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.timestamp))
            canvas.drawText("Date: $dateStr", pageInfo.pageWidth - margin - subTitlePaint.measureText("Date: $dateStr"), 115f, subTitlePaint)
        }
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")
        
        for (paragraph in paragraphs) {
            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }
            
            val words = paragraph.split(" ")
            var currentLine = StringBuilder()
            
            for (word in words) {
                val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine.append(if (currentLine.isEmpty()) word else " $word")
                } else {
                    if (currentLine.isNotEmpty()) {
                        lines.add(currentLine.toString())
                    }
                    currentLine = StringBuilder(word)
                    
                    // Handle very long words that exceed maxWidth on their own
                    while (paint.measureText(currentLine.toString()) > maxWidth && currentLine.length > 1) {
                        // This is a simple fallback for words longer than the line
                        // In a real scenario, you might want hyphenation or hard breaks
                        val part = currentLine.substring(0, currentLine.length - 1)
                        lines.add(part)
                        currentLine = StringBuilder(currentLine.substring(currentLine.length - 1))
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        return lines
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

    private fun rotateBitmapIfNecessary(bitmap: Bitmap, uriStr: String): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uriStr.toUri())
            val exifInterface = inputStream?.use { ExifInterface(it) }
            val orientation = exifInterface?.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            ) ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return bitmap
            }

            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
}
