package com.fearmikey.projectreporter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.model.ExportOptions
import com.fearmikey.projectreporter.data.model.ReportBranding
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.common.usermodel.PictureType
import org.apache.poi.util.Units
import org.apache.poi.wp.usermodel.HeaderFooterType
import org.apache.poi.xwpf.usermodel.*
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STBorder
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

@Singleton
class WordExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val FONT = "Calibri"
        const val TEXT_DARK = "212529"
        const val TEXT_MUTED = "6B7280"
        const val BORDER = "DEE2E6"
    }

    private class EncodedImage(val bytes: ByteArray, val width: Int, val height: Int)

    suspend fun exportToDocx(
        project: ProjectEntity,
        photos: List<PhotoEntity>,
        notes: List<NoteEntity>,
        options: ExportOptions,
        branding: ReportBranding = ReportBranding()
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            XWPFDocument().use { document ->
                val brandHex = ReportBranding.toHex(branding.brandColor)
                val accentHex = ReportBranding.toHex(branding.accentColor)
                val tintHex = ReportBranding.toHex(branding.brandTint)
                val dateStr = SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(project.timestamp))
                val projectLabel = project.displayProjectNumber?.let { "${project.projectName} · #$it" } ?: project.projectName
                val company = branding.companyName.trim()

                buildHeader(document, branding, company, projectLabel, brandHex)
                buildFooter(document, company, projectLabel)

                // ---------- Title block ----------
                document.createParagraph().apply {
                    spacingAfter = 40
                    addRun(this, "SITE SERVICE REPORT", 22, bold = true, color = brandHex).characterSpacing = 20
                }
                document.createParagraph().apply {
                    spacingAfter = 240
                    addRun(this, project.projectName, 13, color = TEXT_DARK)
                    project.displayProjectNumber?.let { addRun(this, "   |   Report No. $it", 10, color = TEXT_MUTED) }
                    setBottomBorder(this, accentHex, 12)
                }

                // ---------- Project details table ----------
                val role = branding.preparedByRole.trim()
                val preparedBy = if (role.isNotEmpty()) "${project.engineerName} · $role" else project.engineerName
                val fields = listOf(
                    "Project" to project.projectName,
                    "Project No." to (project.displayProjectNumber ?: ""),
                    "Client" to project.clientCompany,
                    "Point of Contact" to project.poc,
                    "POC Phone" to project.phoneNumber,
                    "Prepared By" to preparedBy,
                    RoleLabels.exportPhoneLabel(role) to project.engineerPhone,
                    "Report Date" to dateStr,
                    "Photos" to photos.size.toString()
                ).filter { it.second.isNotBlank() }

                val rows = ceil(fields.size / 2.0).toInt()
                val infoTable = document.createTable(rows, 4)
                infoTable.setWidth("100%")
                infoTable.setCellMargins(80, 120, 80, 120)
                setAllBorders(infoTable, BORDER)
                val widths = listOf("18%", "32%", "18%", "32%")
                for (r in 0 until rows) {
                    val row = infoTable.getRow(r)
                    for (c in 0 until 4) {
                        val cell = row.getCell(c)
                        cell.setWidth(widths[c])
                        cell.verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
                        val field = fields.getOrNull(r * 2 + c / 2)
                        val p = firstParagraph(cell).apply { spacingAfter = 0 }
                        if (c % 2 == 0) {
                            cell.color = tintHex
                            field?.let { addRun(p, it.first.uppercase(Locale.getDefault()), 8, bold = true, color = TEXT_MUTED) }
                        } else {
                            field?.let { addRun(p, it.second, 10, color = TEXT_DARK) }
                        }
                    }
                }

                // ---------- Project summary ----------
                if (options.includeNotes && project.notes.isNotBlank()) {
                    addSectionHeading(document, "Project Summary", brandHex)
                    project.notes.trim().split("\n").forEach { line ->
                        document.createParagraph().apply {
                            spacingAfter = 80
                            addRun(this, line, 10, color = TEXT_DARK)
                        }
                    }
                }

                // ---------- General notes ----------
                if (options.includeNotes && notes.isNotEmpty()) {
                    addSectionHeading(document, "General Notes", brandHex)
                    for (note in notes) {
                        document.createParagraph().apply {
                            indentationLeft = 360
                            indentationHanging = 240
                            spacingAfter = 100
                            addRun(this, "•", 10, bold = true, color = brandHex).addTab()
                            val lines = note.content.trim().split("\n")
                            val textRun = addRun(this, lines.first(), 10, color = TEXT_DARK)
                            lines.drop(1).forEach { textRun.addBreak(); textRun.setText(it) }
                        }
                    }
                }

                // ---------- Photo observations ----------
                if (photos.isNotEmpty()) {
                    addSectionHeading(document, "Photo Observations", brandHex)

                    val cols = if (options.photosPerPage >= 4) 2 else 1
                    val maxW = if (cols == 1) 430.0 else 205.0
                    val maxH = if (cols == 1) 330.0 else 170.0
                    val photoRows = ceil(photos.size / cols.toDouble()).toInt()

                    val table = document.createTable(photoRows, cols)
                    table.setWidth("100%")
                    table.setCellMargins(120, 120, 120, 120)
                    setAllBorders(table, BORDER)

                    photos.forEachIndexed { index, photo ->
                        val row = table.getRow(index / cols)
                        row.isCantSplitRow = true
                        val cell = row.getCell(index % cols)
                        cell.setWidth(if (cols == 1) "100%" else "50%")

                        // Caption line
                        firstParagraph(cell).apply {
                            spacingAfter = 80
                            addRun(this, "Photo ${index + 1}", 10, bold = true, color = brandHex)
                            if (options.includeTimestamp && photo.timestampOverlay.isNotBlank()) {
                                addRun(this, "    Captured ${photo.timestampOverlay}", 8, color = TEXT_MUTED)
                            }
                        }

                        // Image
                        val imgParagraph = cell.addParagraph().apply {
                            alignment = ParagraphAlignment.CENTER
                            spacingAfter = 80
                        }
                        val image = encodePhoto(photo.imageUri)
                        if (image != null) {
                            val scale = min(maxW / image.width, maxH / image.height)
                            imgParagraph.createRun().addPicture(
                                ByteArrayInputStream(image.bytes),
                                PictureType.JPEG,
                                "photo_${photo.photoId}.jpg",
                                Units.toEMU(image.width * scale),
                                Units.toEMU(image.height * scale)
                            )
                        } else {
                            addRun(imgParagraph, "[Image unavailable]", 9, italic = true, color = TEXT_MUTED)
                        }

                        // Annotation
                        if (photo.annotation.isNotBlank()) {
                            cell.addParagraph().apply {
                                spacingAfter = 0
                                val lines = photo.annotation.trim().split("\n")
                                val r = addRun(this, lines.first(), 10, color = TEXT_DARK)
                                lines.drop(1).forEach { r.addBreak(); r.setText(it) }
                            }
                        }
                    }
                    // Fill the trailing empty cell (odd photo count in 2-column layout)
                    if (cols == 2 && photos.size % 2 == 1) {
                        table.getRow(photoRows - 1).getCell(1).setWidth("50%")
                    }
                }

                if (!(options.includeNotes && (notes.isNotEmpty() || project.notes.isNotBlank())) && photos.isEmpty()) {
                    document.createParagraph().apply {
                        spacingBefore = 360
                        addRun(this, "No notes or photos have been recorded for this project.", 10, italic = true, color = TEXT_MUTED)
                    }
                }

                // ---------- Save to MediaStore ----------
                val idPart = project.displayProjectNumber?.let { "_$it" } ?: ""
                val fileName = "Report${idPart}_${System.currentTimeMillis()}.docx"
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
                    Result.success(uri)
                } else {
                    Result.failure(Exception("Failed to create document URI"))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    // ---------------------------------------------------------------------
    // Header / footer
    // ---------------------------------------------------------------------

    private fun buildHeader(
        document: XWPFDocument,
        branding: ReportBranding,
        company: String,
        projectLabel: String,
        brandHex: String
    ) {
        val header = document.createHeader(HeaderFooterType.DEFAULT)
        val table = header.createTable(1, 2)
        table.setWidth("100%")
        table.setCellMargins(0, 0, 80, 0)
        setBorderless(table, bottomColor = brandHex)

        val left = table.getRow(0).getCell(0).apply {
            setWidth("50%")
            verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
        }
        val leftP = firstParagraph(left).apply { spacingAfter = 0 }
        val logo = branding.logoUri?.let { encodeLogo(it) }
        if (logo != null) {
            val scale = min(110.0 / logo.width, 32.0 / logo.height)
            leftP.createRun().addPicture(
                ByteArrayInputStream(logo.bytes),
                PictureType.PNG,
                "company_logo.png",
                Units.toEMU(logo.width * scale),
                Units.toEMU(logo.height * scale)
            )
            if (company.isNotEmpty()) addRun(leftP, "   $company", 11, bold = true, color = TEXT_DARK)
        } else if (company.isNotEmpty()) {
            addRun(leftP, company, 13, bold = true, color = brandHex)
        }

        val right = table.getRow(0).getCell(1).apply {
            setWidth("50%")
            verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
        }
        firstParagraph(right).apply {
            alignment = ParagraphAlignment.RIGHT
            spacingAfter = 0
            addRun(this, "Site Service Report", 9, bold = true, color = brandHex)
        }
        right.addParagraph().apply {
            alignment = ParagraphAlignment.RIGHT
            spacingAfter = 0
            addRun(this, projectLabel, 8, color = TEXT_MUTED)
        }
    }

    private fun buildFooter(document: XWPFDocument, company: String, projectLabel: String) {
        val footer = document.createFooter(HeaderFooterType.DEFAULT)
        val table = footer.createTable(1, 2)
        table.setWidth("100%")
        table.setCellMargins(80, 0, 0, 0)
        setBorderless(table, topColor = BORDER)

        val left = table.getRow(0).getCell(0).apply { setWidth("65%") }
        firstParagraph(left).apply {
            spacingAfter = 0
            val text = if (company.isNotEmpty()) "$company  ·  $projectLabel" else projectLabel
            addRun(this, text, 8, color = TEXT_MUTED)
        }
        left.addParagraph().apply {
            spacingAfter = 0
            addRun(this, "Generated by Project Reporter", 7, color = TEXT_MUTED)
        }

        val right = table.getRow(0).getCell(1).apply {
            setWidth("35%")
            verticalAlignment = XWPFTableCell.XWPFVertAlign.CENTER
        }
        firstParagraph(right).apply {
            alignment = ParagraphAlignment.RIGHT
            spacingAfter = 0
            addRun(this, "Page ", 8, bold = true, color = TEXT_DARK)
            addField(this, "PAGE", 8, TEXT_DARK)
            addRun(this, " of ", 8, bold = true, color = TEXT_DARK)
            addField(this, "NUMPAGES", 8, TEXT_DARK)
        }
    }

    /** Inserts a Word field (e.g. PAGE / NUMPAGES) that Word updates automatically. */
    private fun addField(p: XWPFParagraph, instruction: String, size: Int, color: String) {
        addRun(p, "", size, bold = true, color = color).ctr.addNewFldChar().fldCharType = STFldCharType.BEGIN
        addRun(p, "", size, bold = true, color = color).ctr.addNewInstrText().stringValue = " $instruction "
        addRun(p, "", size, bold = true, color = color).ctr.addNewFldChar().fldCharType = STFldCharType.SEPARATE
        addRun(p, "1", size, bold = true, color = color)
        addRun(p, "", size, bold = true, color = color).ctr.addNewFldChar().fldCharType = STFldCharType.END
    }

    // ---------------------------------------------------------------------
    // Formatting helpers
    // ---------------------------------------------------------------------

    private fun addRun(
        p: XWPFParagraph,
        text: String,
        size: Int,
        bold: Boolean = false,
        italic: Boolean = false,
        color: String = TEXT_DARK
    ): XWPFRun = p.createRun().apply {
        fontFamily = FONT
        fontSize = size
        isBold = bold
        isItalic = italic
        this.color = color
        if (text.isNotEmpty()) setText(text)
    }

    private fun firstParagraph(cell: XWPFTableCell): XWPFParagraph =
        cell.paragraphs.firstOrNull() ?: cell.addParagraph()

    private fun addSectionHeading(document: XWPFDocument, title: String, brandHex: String) {
        document.createParagraph().apply {
            spacingBefore = 400
            spacingAfter = 160
            addRun(this, title, 14, bold = true, color = brandHex)
            setBottomBorder(this, BORDER, 6)
            val ppr = ctp.pPr ?: ctp.addNewPPr()
            ppr.addNewKeepNext()
        }
    }

    private fun setBottomBorder(p: XWPFParagraph, colorHex: String, size: Int) {
        val ppr = p.ctp.pPr ?: p.ctp.addNewPPr()
        val bdr = ppr.pBdr ?: ppr.addNewPBdr()
        bdr.addNewBottom().apply {
            `val` = STBorder.SINGLE
            sz = BigInteger.valueOf(size.toLong())
            space = BigInteger.valueOf(4)
            color = colorHex
        }
    }

    private fun setAllBorders(table: XWPFTable, colorHex: String) {
        val type = XWPFTable.XWPFBorderType.SINGLE
        table.setTopBorder(type, 4, 0, colorHex)
        table.setBottomBorder(type, 4, 0, colorHex)
        table.setLeftBorder(type, 4, 0, colorHex)
        table.setRightBorder(type, 4, 0, colorHex)
        table.setInsideHBorder(type, 4, 0, colorHex)
        table.setInsideVBorder(type, 4, 0, colorHex)
    }

    private fun setBorderless(table: XWPFTable, topColor: String? = null, bottomColor: String? = null) {
        val none = XWPFTable.XWPFBorderType.NONE
        val single = XWPFTable.XWPFBorderType.SINGLE
        if (topColor != null) table.setTopBorder(single, 6, 0, topColor) else table.setTopBorder(none, 0, 0, "auto")
        if (bottomColor != null) table.setBottomBorder(single, 12, 0, bottomColor) else table.setBottomBorder(none, 0, 0, "auto")
        table.setLeftBorder(none, 0, 0, "auto")
        table.setRightBorder(none, 0, 0, "auto")
        table.setInsideHBorder(none, 0, 0, "auto")
        table.setInsideVBorder(none, 0, 0, "auto")
    }

    // ---------------------------------------------------------------------
    // Image encoding
    // ---------------------------------------------------------------------

    /** Decodes a photo (downsampled, EXIF-rotated) and re-encodes it as JPEG. */
    private fun encodePhoto(uriStr: String, maxSide: Int = 1600): EncodedImage? = try {
        val bitmap = decodeSampled(uriStr.toUri(), maxSide)?.let { rotateIfNeeded(it, uriStr.toUri()) }
        bitmap?.let {
            val bos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 85, bos)
            EncodedImage(bos.toByteArray(), it.width, it.height).also { _ -> it.recycle() }
        }
    } catch (_: Throwable) {
        null
    }

    /** Decodes the company logo and encodes it as PNG to preserve transparency. */
    private fun encodeLogo(uriStr: String): EncodedImage? = try {
        decodeSampled(uriStr.toUri(), 600)?.let {
            val bos = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.PNG, 100, bos)
            EncodedImage(bos.toByteArray(), it.width, it.height).also { _ -> it.recycle() }
        }
    } catch (_: Throwable) {
        null
    }

    private fun decodeSampled(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    private fun rotateIfNeeded(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = try {
            context.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
            if (it !== bitmap) bitmap.recycle()
        }
    }
}
