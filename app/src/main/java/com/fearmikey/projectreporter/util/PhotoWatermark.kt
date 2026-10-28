package com.fearmikey.projectreporter.util

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Location
import android.text.TextPaint
import android.text.TextUtils
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Burns a text watermark (timestamp, project, GPS) into a captured JPEG.
 *
 * The photo is decoded once into a single mutable bitmap and kept in its stored (sensor)
 * orientation; the text is drawn through a transform so it reads upright once viewers apply
 * the EXIF orientation. Camera EXIF metadata (orientation, capture time, GPS, exposure…) is
 * copied to the re-encoded file, which atomically replaces the original.
 */
object PhotoWatermark {

    /** EXIF tags carried over when the JPEG is re-encoded. */
    private val PRESERVED_TAGS = arrayOf(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
        ExifInterface.TAG_SUBSEC_TIME,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_GPS_VERSION_ID,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    )

    /** Builds the watermark lines for a photo taken now. Returns an empty list when nothing is enabled. */
    fun buildLines(
        includeTimestamp: Boolean,
        projectName: String?,
        includeGps: Boolean,
        location: Location?,
    ): List<String> = buildList {
        if (includeTimestamp) {
            add(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
        }
        if (!projectName.isNullOrBlank()) add("Project: $projectName")
        if (includeGps) add(location?.let(::formatLocation) ?: "Location unavailable")
    }

    private fun formatLocation(location: Location): String {
        val coords = String.format(Locale.US, "GPS: %.5f, %.5f", location.latitude, location.longitude)
        return if (location.hasAccuracy()) "$coords (±${location.accuracy.roundToInt()} m)" else coords
    }

    /**
     * Draws [lines] onto the JPEG at [file], replacing it in place. Does nothing if [lines] is empty.
     * On failure the original file is left untouched and an exception is thrown.
     */
    @Throws(IOException::class)
    fun apply(file: File, lines: List<String>, quality: Int = 92) {
        if (lines.isEmpty()) return

        val exif = ExifInterface(file)
        val rotation = exif.rotationDegrees
        val preserved = PRESERVED_TAGS.mapNotNull { tag -> exif.getAttribute(tag)?.let { tag to it } }

        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inMutable = true })
            ?: throw IOException("Could not decode ${file.name}")
        val tmp = File(file.parentFile, "${file.name}.tmp")
        try {
            drawLines(Canvas(bitmap), bitmap.width, bitmap.height, rotation, lines)

            FileOutputStream(tmp).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)) {
                    throw IOException("JPEG encoding failed")
                }
            }
            ExifInterface(tmp).apply {
                preserved.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
            if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
        } finally {
            bitmap.recycle()
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun drawLines(canvas: Canvas, width: Int, height: Int, rotation: Int, lines: List<String>) {
        // Map upright (as-displayed) coordinates onto the stored, un-rotated pixels.
        val toUpright = Matrix().apply { postRotate(rotation.toFloat()) }
        val upright = RectF(0f, 0f, width.toFloat(), height.toFloat())
        toUpright.mapRect(upright)
        toUpright.postTranslate(-upright.left, -upright.top)
        val toStored = Matrix().also { toUpright.invert(it) }
        canvas.concat(toStored)

        val w = upright.width()
        val h = upright.height()
        // Size from the short side so text looks the same in portrait and landscape.
        val shortSide = min(w, h)
        val margin = shortSide * 0.03f

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.YELLOW
            textSize = (shortSide * 0.032f).coerceAtLeast(24f)
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(textSize * 0.08f, 1.5f, 1.5f, Color.BLACK)
        }
        val padding = textPaint.textSize * 0.5f
        val maxTextWidth = w - 2 * margin - 2 * padding
        val fitted = lines.map { TextUtils.ellipsize(it, textPaint, maxTextWidth, TextUtils.TruncateAt.END).toString() }

        val lineHeight = textPaint.fontSpacing
        val boxWidth = fitted.maxOf { textPaint.measureText(it) } + 2 * padding
        val boxHeight = lineHeight * fitted.size + 2 * padding
        val left = margin
        val bottom = h - margin
        val top = bottom - boxHeight

        // Translucent backing keeps the text legible over sky, concrete, etc.
        val backing = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(150, 0, 0, 0) }
        canvas.drawRoundRect(left, top, left + boxWidth, bottom, padding, padding, backing)

        var baseline = top + padding - textPaint.ascent()
        fitted.forEach { line ->
            canvas.drawText(line, left + padding, baseline, textPaint)
            baseline += lineHeight
        }
    }
}
