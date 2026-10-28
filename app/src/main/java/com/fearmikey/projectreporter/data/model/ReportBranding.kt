package com.fearmikey.projectreporter.data.model

import android.graphics.Color
import androidx.core.graphics.ColorUtils

/**
 * Company branding applied to exported reports (PDF / DOCX).
 */
data class ReportBranding(
    val companyName: String = "",
    val logoUri: String? = null,
    val primaryColor: Int? = null,
    val secondaryColor: Int? = null,
    val preparedByRole: String = ""
) {
    /** Brand color guaranteed dark enough to be legible as text on white. */
    val brandColor: Int
        get() {
            val base = primaryColor ?: DEFAULT_BRAND
            return if (ColorUtils.calculateLuminance(base) > 0.35) {
                ColorUtils.blendARGB(base, Color.BLACK, 0.45f)
            } else base
        }

    /** Secondary accent color (falls back to brand color). */
    val accentColor: Int
        get() = secondaryColor ?: brandColor

    /** Very light tint of the brand color for backgrounds. */
    val brandTint: Int
        get() = ColorUtils.blendARGB(brandColor, Color.WHITE, 0.92f)

    companion object {
        val DEFAULT_BRAND: Int = Color.rgb(0x1F, 0x3A, 0x5F)

        fun toHex(color: Int): String = String.format("%06X", 0xFFFFFF and color)
    }
}
