package com.fearmikey.projectreporter.util

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
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
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** A deferred drawing operation recorded during layout and replayed at render time. */
private typealias DrawOp = (Canvas) -> Unit

@Singleton
class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val PAGE_W = 595 // A4 in points
        const val PAGE_H = 842
        const val MARGIN = 40f
        const val CONTENT_W = PAGE_W - 2 * MARGIN
        const val CONTENT_TOP = 78f
        const val CONTENT_BOTTOM = PAGE_H - 58f
        const val RENDER_SCALE = 3f // Render photos at ~216 dpi for crisp output

        val TEXT_DARK = Color.rgb(0x21, 0x25, 0x29)
        val TEXT_MUTED = Color.rgb(0x6B, 0x72, 0x80)
        val BORDER = Color.rgb(0xDE, 0xE2, 0xE6)
        val IMAGE_BG = Color.rgb(0xF4, 0xF5, 0xF7)
    }

    private data class ImageInfo(val width: Int, val height: Int, val rotation: Int)

    /** Collects pages of draw operations so total page count is known before rendering. */
    private class Layout {
        val pages = mutableListOf<MutableList<DrawOp>>(mutableListOf())
        var y = CONTENT_TOP
        val remaining get() = CONTENT_BOTTOM - y
        val isAtPageTop get() = y <= CONTENT_TOP + 0.5f

        fun add(op: DrawOp) {
            pages.last().add(op)
        }

        fun newPage() {
            pages.add(mutableListOf())
            y = CONTENT_TOP
        }

        fun ensure(height: Float) {
            if (y + height > CONTENT_BOTTOM && !isAtPageTop) newPage()
        }
    }

    suspend fun exportToPdf(
        project: ProjectEntity,
        photos: List<PhotoEntity>,
        notes: List<NoteEntity>,
        options: ExportOptions,
        branding: ReportBranding = ReportBranding()
    ): Result<Uri> = withContext(Dispatchers.IO) {
        var logo: Bitmap? = null
        val pdfDocument = PdfDocument()
        try {
            logo = branding.logoUri?.let { loadLogo(it) }
            val layout = Layout()

            layoutCoverHeader(layout, project, photos.size, branding, logo)

            var hasContent = false

            if (options.includeNotes && project.notes.isNotBlank()) {
                hasContent = true
                layoutSection(layout, "Project Summary", branding)
                layoutParagraph(layout, project.notes.trim())
                layout.y += 12f
            }

            if (options.includeNotes && notes.isNotEmpty()) {
                hasContent = true
                layoutSection(layout, "General Notes", branding)
                layoutNotes(layout, notes, branding)
                layout.y += 12f
            }

            if (photos.isNotEmpty()) {
                hasContent = true
                if (options.photosPerPage <= 1) {
                    layoutSection(layout, "Photo Observations", branding)
                    layoutPhotosFlowing(layout, photos, options, branding)
                } else {
                    layoutPhotosGrid(layout, photos, options, branding)
                }
            }

            if (!hasContent) {
                val p = textPaint(10.5f, TEXT_MUTED)
                val yy = layout.y + 14f
                layout.add { c -> c.drawText("No notes or photos have been recorded for this project.", MARGIN, yy, p) }
            }

            // Render pass
            val totalPages = layout.pages.size
            layout.pages.forEachIndexed { index, ops ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                drawPageFrame(canvas, index + 1, totalPages, project, branding, logo)
                ops.forEach { op -> op(canvas) }
                pdfDocument.finishPage(page)
            }

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
                Result.success(uri)
            } else {
                Result.failure(Exception("Failed to create document URI"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        } finally {
            pdfDocument.close()
            logo?.recycle()
        }
    }

    // ---------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------

    private fun layoutCoverHeader(
        layout: Layout,
        project: ProjectEntity,
        photoCount: Int,
        branding: ReportBranding,
        logo: Bitmap?
    ) {
        val brand = branding.brandColor
        val company = branding.companyName.trim()
        val hasLeftBlock = logo != null || company.isNotEmpty()
        val dateStr = formatDate(project.timestamp)

        val titlePaint = textPaint(20f, brand, bold = true).apply { letterSpacing = 0.04f }
        val metaPaint = textPaint(9.5f, TEXT_MUTED)
        val companyLargePaint = textPaint(18f, brand, bold = true)
        val companySmallPaint = textPaint(11f, TEXT_DARK, bold = true)

        val headerBottom = if (logo != null && company.isNotEmpty()) 112f else 98f

        layout.add { c ->
            // Brand strip across the top of the cover page
            c.drawRect(0f, 0f, PAGE_W.toFloat(), 7f, fillPaint(brand))
            c.drawRect(PAGE_W * 0.72f, 0f, PAGE_W.toFloat(), 7f, fillPaint(branding.accentColor))

            // Left block: logo and/or company name
            if (logo != null) {
                val box = RectF(MARGIN, 26f, MARGIN + 160f, 80f)
                c.drawBitmap(logo, null, fitRect(logo.width, logo.height, box, alignLeft = true), bitmapPaint())
                if (company.isNotEmpty()) {
                    c.drawText(ellipsize(company, companySmallPaint, 240f), MARGIN, 98f, companySmallPaint)
                }
            } else if (company.isNotEmpty()) {
                c.drawText(ellipsize(company, companyLargePaint, 260f), MARGIN, 58f, companyLargePaint)
            }

            // Title block
            val title = "SITE SERVICE REPORT"
            val reportNo = project.displayProjectNumber?.let { "Report No. $it" }
            val issued = "Issued $dateStr"
            if (hasLeftBlock) {
                val right = PAGE_W - MARGIN
                drawRight(c, title, right, 52f, titlePaint)
                var ty = 70f
                reportNo?.let { drawRight(c, it, right, ty, metaPaint); ty += 14f }
                drawRight(c, issued, right, ty, metaPaint)
            } else {
                c.drawText(title, MARGIN, 52f, titlePaint)
                var ty = 70f
                reportNo?.let { c.drawText(it, MARGIN, ty, metaPaint); ty += 14f }
                c.drawText(issued, MARGIN, ty, metaPaint)
            }

            // Divider
            c.drawRect(MARGIN, headerBottom, PAGE_W - MARGIN, headerBottom + 1.5f, fillPaint(brand))
        }

        layout.y = headerBottom + 18f

        // Project details card
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
            "Photos" to photoCount.toString()
        ).filter { it.second.isNotBlank() }

        val cols = 2
        val rowH = 34f
        val padX = 18f
        val rows = ceil(fields.size / cols.toFloat()).toInt()
        val cardTop = layout.y
        val cardH = 10f + rows * rowH + 4f
        val colW = (CONTENT_W - padX * 2) / cols
        val labelPaint = textPaint(7.5f, TEXT_MUTED, bold = true).apply { letterSpacing = 0.08f }
        val valuePaint = textPaint(11f, TEXT_DARK)

        layout.add { c ->
            val card = RectF(MARGIN, cardTop, PAGE_W - MARGIN, cardTop + cardH)
            c.save()
            c.clipPath(Path().apply { addRoundRect(card, 6f, 6f, Path.Direction.CW) })
            c.drawRect(card, fillPaint(branding.brandTint))
            c.drawRect(card.left, card.top, card.left + 4f, card.bottom, fillPaint(branding.brandColor))
            c.restore()

            fields.forEachIndexed { i, (label, value) ->
                val col = i % cols
                val row = i / cols
                val x = MARGIN + padX + col * colW
                val rowTop = cardTop + 10f + row * rowH
                c.drawText(label.uppercase(Locale.getDefault()), x, rowTop + 10f, labelPaint)
                c.drawText(ellipsize(value, valuePaint, colW - 12f), x, rowTop + 25f, valuePaint)
            }
        }
        layout.y = cardTop + cardH + 24f
    }

    private fun layoutSection(layout: Layout, title: String, branding: ReportBranding) {
        layout.ensure(26f + 48f) // keep heading with at least some content
        val baseline = layout.y + 12f
        val headingPaint = textPaint(13f, branding.brandColor, bold = true)
        layout.add { c ->
            c.drawRect(MARGIN, baseline - 11f, MARGIN + 3.5f, baseline + 3f, fillPaint(branding.accentColor))
            c.drawText(title, MARGIN + 11f, baseline, headingPaint)
            val lineStart = MARGIN + 11f + headingPaint.measureText(title) + 10f
            c.drawRect(lineStart, baseline - 4.5f, PAGE_W - MARGIN, baseline - 4f, fillPaint(BORDER))
        }
        layout.y += 28f
    }

    private fun layoutParagraph(layout: Layout, text: String) {
        val paint = textPaint(10.5f, TEXT_DARK)
        for (line in wrapText(text, paint, CONTENT_W)) {
            layout.ensure(15f)
            val yy = layout.y + 11f
            layout.add { c -> c.drawText(line, MARGIN, yy, paint) }
            layout.y += 15f
        }
    }

    private fun layoutNotes(layout: Layout, notes: List<NoteEntity>, branding: ReportBranding) {
        val paint = textPaint(10.5f, TEXT_DARK)
        val bulletPaint = fillPaint(branding.brandColor)
        val indent = 16f
        for (note in notes) {
            val lines = wrapText(note.content.trim(), paint, CONTENT_W - indent)
            lines.forEachIndexed { i, line ->
                layout.ensure(15f)
                val yy = layout.y + 11f
                val first = i == 0
                layout.add { c ->
                    if (first) c.drawCircle(MARGIN + 5f, yy - 3.5f, 2.2f, bulletPaint)
                    c.drawText(line, MARGIN + indent, yy, paint)
                }
                layout.y += 15f
            }
            layout.y += 5f
        }
    }

    private fun layoutPhotosFlowing(
        layout: Layout,
        photos: List<PhotoEntity>,
        options: ExportOptions,
        branding: ReportBranding
    ) {
        val pad = 10f
        val captionH = 18f
        val annLineH = 14f
        val imgMaxW = CONTENT_W - pad * 2
        val imgMaxH = 400f
        val captionPaint = textPaint(10f, branding.brandColor, bold = true)
        val tsPaint = textPaint(8.5f, TEXT_MUTED)
        val annPaint = textPaint(10f, TEXT_DARK)

        var number = 0
        for (photo in photos) {
            val info = readImageInfo(photo.imageUri) ?: continue
            number++
            val scale = min(imgMaxW / info.width, imgMaxH / info.height)
            val imgH = info.height * scale

            val fixedH = pad + imgH + 8f + captionH + pad
            val maxAnnLines = max(0, ((CONTENT_BOTTOM - CONTENT_TOP - fixedH) / annLineH).toInt())
            val annLines = truncateLines(
                if (photo.annotation.isNotBlank()) wrapText(photo.annotation.trim(), annPaint, imgMaxW) else emptyList(),
                maxAnnLines, annPaint, imgMaxW
            )
            val cardH = fixedH + annLines.size * annLineH

            layout.ensure(cardH)
            val top = layout.y
            val label = "Photo $number"
            val timestamp = if (options.includeTimestamp) photo.timestampOverlay else null

            layout.add { c ->
                val card = RectF(MARGIN, top, PAGE_W - MARGIN, top + cardH)
                c.drawRoundRect(card, 6f, 6f, fillPaint(Color.WHITE))
                c.drawRoundRect(card, 6f, 6f, strokePaint(BORDER, 0.8f))

                val imgArea = RectF(MARGIN + pad, top + pad, PAGE_W - MARGIN - pad, top + pad + imgH)
                c.drawRect(imgArea, fillPaint(IMAGE_BG))
                drawImage(c, photo.imageUri, info, imgArea)

                var yy = imgArea.bottom + 8f + 12f
                c.drawText(label, imgArea.left, yy, captionPaint)
                timestamp?.let { drawRight(c, "Captured $it", imgArea.right, yy, tsPaint) }
                yy += captionH - 12f + 12f
                for (line in annLines) {
                    c.drawText(line, imgArea.left, yy, annPaint)
                    yy += annLineH
                }
            }
            layout.y = top + cardH + 14f
        }
    }

    private fun layoutPhotosGrid(
        layout: Layout,
        photos: List<PhotoEntity>,
        options: ExportOptions,
        branding: ReportBranding
    ) {
        val perPage = options.photosPerPage
        val cols = if (perPage >= 4) 2 else 1
        val rows = 2
        val gap = 14f
        val pad = 8f
        val maxAnnLines = if (cols == 2) 3 else 4
        val annLineH = 12f
        val metaH = 16f + maxAnnLines * annLineH

        // Give the grid a (near) full page so photo cells stay a consistent size
        val fullHeight = CONTENT_BOTTOM - CONTENT_TOP
        if (!layout.isAtPageTop && layout.remaining - 28f < fullHeight * 0.8f) layout.newPage()
        layoutSection(layout, "Photo Observations", branding)

        val captionPaint = textPaint(9f, branding.brandColor, bold = true)
        val tsPaint = textPaint(7.5f, TEXT_MUTED)
        val annPaint = textPaint(9f, TEXT_DARK)
        val cellW = (CONTENT_W - gap * (cols - 1)) / cols

        val valid = photos.mapNotNull { p -> readImageInfo(p.imageUri)?.let { p to it } }
        valid.chunked(perPage).forEachIndexed { pageIdx, group ->
            if (pageIdx > 0) layout.newPage()
            val areaTop = layout.y
            val cellH = (CONTENT_BOTTOM - areaTop - gap * (rows - 1)) / rows

            group.forEachIndexed { i, (photo, info) ->
                val number = pageIdx * perPage + i + 1
                val col = i % cols
                val row = i / cols
                val x = MARGIN + col * (cellW + gap)
                val y = areaTop + row * (cellH + gap)
                val textW = cellW - pad * 2
                val annLines = truncateLines(
                    if (photo.annotation.isNotBlank()) wrapText(photo.annotation.trim(), annPaint, textW) else emptyList(),
                    maxAnnLines, annPaint, textW
                )
                val timestamp = if (options.includeTimestamp) photo.timestampOverlay else null

                layout.add { c ->
                    val card = RectF(x, y, x + cellW, y + cellH)
                    c.drawRoundRect(card, 6f, 6f, fillPaint(Color.WHITE))
                    c.drawRoundRect(card, 6f, 6f, strokePaint(BORDER, 0.8f))

                    val imgArea = RectF(x + pad, y + pad, x + cellW - pad, y + cellH - pad - metaH)
                    c.drawRect(imgArea, fillPaint(IMAGE_BG))
                    drawImage(c, photo.imageUri, info, imgArea)

                    var yy = imgArea.bottom + 14f
                    c.drawText("Photo $number", imgArea.left, yy, captionPaint)
                    timestamp?.let { drawRight(c, it, imgArea.right, yy, tsPaint) }
                    yy += 14f
                    for (line in annLines) {
                        c.drawText(line, imgArea.left, yy, annPaint)
                        yy += annLineH
                    }
                }
            }
            layout.y = CONTENT_BOTTOM
        }
    }

    // ---------------------------------------------------------------------
    // Page frame (header / footer)
    // ---------------------------------------------------------------------

    private fun drawPageFrame(
        canvas: Canvas,
        pageNumber: Int,
        totalPages: Int,
        project: ProjectEntity,
        branding: ReportBranding,
        logo: Bitmap?
    ) {
        val company = branding.companyName.trim()
        val projectLabel = project.displayProjectNumber?.let { "${project.projectName} · #$it" } ?: project.projectName

        // Running header on continuation pages (cover page has its own header)
        if (pageNumber > 1) {
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 3f, fillPaint(branding.brandColor))

            var textX = MARGIN
            if (logo != null) {
                val rect = fitRect(logo.width, logo.height, RectF(MARGIN, 18f, MARGIN + 90f, 44f), alignLeft = true)
                canvas.drawBitmap(logo, null, rect, bitmapPaint())
                textX = rect.right + 10f
            }
            if (company.isNotEmpty()) {
                val p = textPaint(10f, TEXT_DARK, bold = true)
                canvas.drawText(ellipsize(company, p, 200f), textX, 35f, p)
            }

            val right = PAGE_W - MARGIN
            drawRight(canvas, "Site Service Report", right, 30f, textPaint(9.5f, branding.brandColor, bold = true))
            val sub = textPaint(8.5f, TEXT_MUTED)
            drawRight(canvas, ellipsize(projectLabel, sub, 240f), right, 43f, sub)

            canvas.drawRect(MARGIN, 54f, PAGE_W - MARGIN, 55f, fillPaint(branding.brandColor))
        }

        // Footer on every page
        val footerLineY = PAGE_H - 42f
        val footerY = PAGE_H - 26f
        canvas.drawRect(MARGIN, footerLineY, PAGE_W - MARGIN, footerLineY + 0.6f, fillPaint(BORDER))

        val leftPaint = textPaint(8f, TEXT_MUTED)
        val leftText = if (company.isNotEmpty()) "$company  ·  $projectLabel" else projectLabel
        canvas.drawText(ellipsize(leftText, leftPaint, 210f), MARGIN, footerY, leftPaint)

        val centerPaint = textPaint(7.5f, TEXT_MUTED)
        val centerText = "Generated by Project Reporter"
        canvas.drawText(centerText, (PAGE_W - centerPaint.measureText(centerText)) / 2f, footerY, centerPaint)

        drawRight(canvas, "Page $pageNumber of $totalPages", PAGE_W - MARGIN, footerY, textPaint(8f, TEXT_DARK, bold = true))
    }

    // ---------------------------------------------------------------------
    // Drawing helpers
    // ---------------------------------------------------------------------

    private fun textPaint(size: Float, color: Int, bold: Boolean = false) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            textSize = size
            this.color = color
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    private fun strokePaint(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = width
    }

    private fun bitmapPaint() = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    private fun drawRight(canvas: Canvas, text: String, right: Float, y: Float, paint: Paint) {
        canvas.drawText(text, right - paint.measureText(text), y, paint)
    }

    /** Fits a source size into [box] preserving aspect ratio; centered unless [alignLeft]. */
    private fun fitRect(srcW: Int, srcH: Int, box: RectF, alignLeft: Boolean = false): RectF {
        val scale = min(box.width() / srcW, box.height() / srcH)
        val w = srcW * scale
        val h = srcH * scale
        val left = if (alignLeft) box.left else box.left + (box.width() - w) / 2f
        val top = box.top + (box.height() - h) / 2f
        return RectF(left, top, left + w, top + h)
    }

    private fun drawImage(canvas: Canvas, uri: String, info: ImageInfo, area: RectF) {
        val dst = fitRect(info.width, info.height, area)
        val bmp = loadBitmap(uri, info, (dst.width() * RENDER_SCALE).toInt(), (dst.height() * RENDER_SCALE).toInt()) ?: return
        canvas.drawBitmap(bmp, null, dst, bitmapPaint())
        bmp.recycle()
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
        return text.substring(0, end).trimEnd() + "…"
    }

    private fun truncateLines(lines: List<String>, maxLines: Int, paint: Paint, maxWidth: Float): List<String> {
        if (lines.size <= maxLines) return lines
        if (maxLines <= 0) return emptyList()
        val kept = lines.take(maxLines).toMutableList()
        kept[kept.lastIndex] = ellipsize(kept.last() + "…", paint, maxWidth)
        return kept
    }

    private fun formatDate(timestamp: Long): String =
        SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            if (paragraph.isBlank()) {
                lines.add("")
                continue
            }
            var currentLine = StringBuilder()
            for (word in paragraph.split(" ")) {
                val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (paint.measureText(testLine) <= maxWidth) {
                    currentLine = StringBuilder(testLine)
                } else {
                    if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                    // Hard-break words that are wider than the line on their own
                    while (paint.measureText(currentLine.toString()) > maxWidth && currentLine.length > 1) {
                        var cut = currentLine.length - 1
                        while (cut > 1 && paint.measureText(currentLine.substring(0, cut)) > maxWidth) cut--
                        lines.add(currentLine.substring(0, cut))
                        currentLine = StringBuilder(currentLine.substring(cut))
                    }
                }
            }
            if (currentLine.isNotEmpty()) lines.add(currentLine.toString())
        }
        return lines
    }

    // ---------------------------------------------------------------------
    // Image loading
    // ---------------------------------------------------------------------

    private fun readImageInfo(uriStr: String): ImageInfo? = try {
        val uri = uriStr.toUri()
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        if (opts.outWidth <= 0 || opts.outHeight <= 0) {
            null
        } else {
            val rotation = readRotation(uri)
            if (rotation == 90 || rotation == 270) ImageInfo(opts.outHeight, opts.outWidth, rotation)
            else ImageInfo(opts.outWidth, opts.outHeight, rotation)
        }
    } catch (_: Exception) {
        null
    }

    private fun readRotation(uri: Uri): Int = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (_: Exception) {
        0
    }

    /** Decodes a photo downsampled to roughly the target size and applies EXIF rotation. */
    private fun loadBitmap(uriStr: String, info: ImageInfo, targetW: Int, targetH: Int): Bitmap? = try {
        var sample = 1
        while (info.width / (sample * 2) >= targetW && info.height / (sample * 2) >= targetH) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = context.contentResolver.openInputStream(uriStr.toUri())?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
        if (decoded == null || info.rotation == 0) {
            decoded
        } else {
            val matrix = Matrix().apply { postRotate(info.rotation.toFloat()) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        }
    } catch (_: Throwable) {
        null
    }

    private fun loadLogo(uriStr: String): Bitmap? = try {
        val uri = uriStr.toUri()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 600 && bounds.outHeight / (sample * 2) >= 200) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    } catch (_: Exception) {
        null
    }
}
