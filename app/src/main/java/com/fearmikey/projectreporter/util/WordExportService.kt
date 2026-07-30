package com.fearmikey.projectreporter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.model.ExportOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WordExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun exportToDocx(
        project: ProjectEntity,
        photos: List<PhotoEntity>,
        notes: List<NoteEntity>,
        options: ExportOptions
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val document = XWPFDocument()

            // Header
            val title = document.createParagraph()
            title.alignment = ParagraphAlignment.CENTER
            val titleRun = title.createRun()
            titleRun.isBold = true
            titleRun.fontSize = 24
            titleRun.setText("SITE SERVICE REPORT")
            titleRun.addBreak()

            val info = document.createParagraph()
            info.alignment = ParagraphAlignment.LEFT
            val infoRun = info.createRun()
            infoRun.fontSize = 12
            infoRun.setText("Project: ${project.projectName} (${project.projectId})")
            infoRun.addBreak()
            infoRun.setText("Engineer: ${project.engineerName}")
            infoRun.addBreak()
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.timestamp))
            infoRun.setText("Date: $dateStr")
            infoRun.addBreak()
            
            // Horizontal line (sort of, using a border)
            info.borderBottom = Borders.SINGLE

            // General Notes
            if (options.includeNotes && notes.isNotEmpty()) {
                val notesHeader = document.createParagraph()
                notesHeader.spacingBefore = 400
                val notesHeaderRun = notesHeader.createRun()
                notesHeaderRun.isBold = true
                notesHeaderRun.fontSize = 16
                notesHeaderRun.setText("General Notes")

                for (note in notes) {
                    val p = document.createParagraph()
                    p.style = "ListBullet"
                    val r = p.createRun()
                    r.setText("• ${note.content}")
                }
            }

            // Photo Observations
            if (photos.isNotEmpty()) {
                val photosHeader = document.createParagraph()
                photosHeader.spacingBefore = 400
                val photosHeaderRun = photosHeader.createRun()
                photosHeaderRun.isBold = true
                photosHeaderRun.fontSize = 16
                photosHeaderRun.setText("Photo Observations")

                for (photo in photos) {
                    // New page for each photo to keep it clean if desired, 
                    // or just a break. Let's do a break.
                    val p = document.createParagraph()
                    p.spacingBefore = 200
                    val r = p.createRun()
                    
                    // Load and insert image
                    try {
                        val inputStream = context.contentResolver.openInputStream(photo.imageUri.toUri())
                        if (inputStream != null) {
                            val bitmap = BitmapFactory.decodeStream(inputStream)
                            if (bitmap != null) {
                                // Word points are 1/72 inch. EMU is 1/914400 inch.
                                // A4 width is ~6.5 inches with margins.
                                val maxWidthPx = 450
                                val ratio = maxWidthPx.toDouble() / bitmap.width
                                val widthPx = maxWidthPx
                                val heightPx = (bitmap.height * ratio).toInt()

                                val bos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, bos)
                                val bis = ByteArrayInputStream(bos.toByteArray())

                                r.addPicture(bis, XWPFDocument.PICTURE_TYPE_JPEG, "photo_${photo.photoId}.jpg", Units.toEMU(widthPx.toDouble()), Units.toEMU(heightPx.toDouble()))
                                r.addBreak()
                            }
                            inputStream.close()
                        }
                    } catch (e: Exception) {
                        r.setText("[Image Error: ${e.message}]")
                        r.addBreak()
                    }

                    if (options.includeTimestamp) {
                        val tsRun = p.createRun()
                        tsRun.isItalic = true
                        tsRun.fontSize = 10
                        tsRun.color = "666666"
                        tsRun.setText("Captured: ${photo.timestampOverlay}")
                        tsRun.addBreak()
                    }

                    if (photo.annotation.isNotEmpty()) {
                        val annRun = p.createRun()
                        annRun.fontSize = 12
                        annRun.setText(photo.annotation)
                    }
                    
                    // Add a thin line between photos
                    p.borderBottom = Borders.THIN_THICK_SMALL_GAP
                }
            }

            // Save to MediaStore
            val fileName = "Report_${project.projectId}_${System.currentTimeMillis()}.docx"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    document.write(outputStream)
                }
            }

            document.close()
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
