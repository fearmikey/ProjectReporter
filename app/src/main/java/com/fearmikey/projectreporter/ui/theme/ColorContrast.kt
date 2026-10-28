package com.fearmikey.projectreporter.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlin.math.abs

/** WCAG 2.x contrast thresholds. */
object ContrastLevel {
    /** AA for normal text. */
    const val TEXT = 4.5

    /** AAA for normal text (used by High Contrast mode). */
    const val TEXT_HIGH = 7.0

    /** AA for non-text UI elements (icons, outlines, focus indicators). */
    const val UI = 3.0
}

/** Seed colors from which a complete, contrast-safe [ColorScheme] is generated. */
data class SchemePalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val surface: Color
)

/** WCAG contrast ratio (1.0 – 21.0) between [foreground] and [background]. */
fun contrastRatio(foreground: Color, background: Color): Double {
    val bg = background.copy(alpha = 1f)
    val fg = if (foreground.alpha < 1f) foreground.compositeOver(bg) else foreground
    return ColorUtils.calculateContrast(fg.toArgb(), bg.toArgb())
}

/** Black or white, whichever is more legible on [background]. */
fun bestContentColor(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) {
        Color.White
    } else {
        Color.Black
    }

/** Linear blend: 0f = [from], 1f = [to]. */
fun blend(from: Color, to: Color, ratio: Float): Color =
    Color(ColorUtils.blendARGB(from.toArgb(), to.toArgb(), ratio.coerceIn(0f, 1f)))

/**
 * Returns this color with its lightness nudged (hue and saturation preserved) until it reaches
 * at least [minRatio] contrast against every color in [backgrounds]. Lightens on dark
 * backgrounds and darkens on light ones. Falls back to black/white if the target is unreachable.
 */
fun Color.ensureContrast(backgrounds: List<Color>, minRatio: Double): Color {
    fun worst(c: Color) = backgrounds.minOf { contrastRatio(c, it) }
    if (backgrounds.isEmpty() || worst(this) >= minRatio) return this

    val darkBackgrounds = backgrounds.count { bestContentColor(it) == Color.White }
    val lighten = darkBackgrounds * 2 >= backgrounds.size

    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    var lightness = hsl[2]
    val step = if (lighten) 0.01f else -0.01f
    while (lightness in 0f..1f) {
        lightness += step
        val candidate = Color(ColorUtils.HSLToColor(floatArrayOf(hsl[0], hsl[1], lightness.coerceIn(0f, 1f))))
        if (worst(candidate) >= minRatio) return candidate
    }
    return if (lighten) Color.White else Color.Black
}

fun Color.ensureContrast(background: Color, minRatio: Double): Color =
    ensureContrast(listOf(background), minRatio)

private fun Color.hue(): Float {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    return hsl[0]
}

private fun Color.rotateHue(degrees: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[0] = (hsl[0] + degrees + 360f) % 360f
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Returns [secondary], or an analogous variant of [primary] if the two are visually
 * indistinguishable (so that primary and secondary actions remain distinct).
 */
fun distinctSecondary(primary: Color, secondary: Color?): Color {
    if (secondary == null) return primary.rotateHue(35f)
    val hueDiff = abs(primary.hue() - secondary.hue()).let { if (it > 180f) 360f - it else it }
    val tooSimilar = contrastRatio(primary, secondary) < 1.25 && hueDiff < 20f
    return if (tooSimilar) primary.rotateHue(35f) else secondary
}

/**
 * Accent (primary/secondary/tertiary) and its "on" color. The accent is guaranteed to be
 * legible as text on all [backgrounds], and the "on" color legible on top of the accent.
 */
private fun accentPair(seed: Color, backgrounds: List<Color>, minRatio: Double): Pair<Color, Color> {
    var accent = seed.ensureContrast(backgrounds, minRatio)
    var on = bestContentColor(accent)
    if (contrastRatio(on, accent) < minRatio) {
        accent = accent.ensureContrast(on, minRatio)
        on = bestContentColor(accent)
    }
    return accent to on
}

/** Tonal container derived from [accent] plus a legible tinted "on" color. */
private fun containerPair(accent: Color, surface: Color, dark: Boolean, minRatio: Double): Pair<Color, Color> {
    val container = if (dark) blend(surface, accent, 0.35f) else blend(Color.White, accent, 0.18f)
    val onSeed = if (dark) blend(accent, Color.White, 0.75f) else blend(accent, Color.Black, 0.65f)
    return container to onSeed.ensureContrast(container, minRatio)
}

/**
 * Builds a complete Material 3 [ColorScheme] from [palette], filling in every role so nothing
 * falls back to the default baseline (purple) palette, and validating every content/container
 * pair against WCAG contrast thresholds.
 */
fun buildAccessibleColorScheme(
    palette: SchemePalette,
    dark: Boolean,
    amoled: Boolean = false,
    highContrast: Boolean = false
): ColorScheme {
    val textMin = if (highContrast) ContrastLevel.TEXT_HIGH else ContrastLevel.TEXT
    val uiMin = if (highContrast) ContrastLevel.TEXT else ContrastLevel.UI

    val pureBlack = dark && amoled
    val background = if (pureBlack) Color.Black else palette.background
    val surface = if (pureBlack) Color.Black else palette.surface
    val elevate = if (dark) Color.White else Color.Black

    // Surface hierarchy, subtly tinted with the primary seed so it harmonises with the accent.
    fun tone(amount: Float) = blend(blend(surface, elevate, amount), palette.primary, 0.03f)
    val surfaceContainerLowest = if (dark) blend(surface, Color.Black, 0.4f) else Color.White
    val surfaceContainerLow = tone(if (dark) 0.03f else 0.02f)
    val surfaceContainer = tone(if (dark) 0.05f else 0.04f)
    val surfaceContainerHigh = tone(if (dark) 0.09f else 0.06f)
    val surfaceContainerHighest = tone(if (dark) 0.13f else 0.09f)
    val surfaceVariant = tone(if (dark) 0.12f else 0.07f)
    val surfaceBright = if (dark) tone(0.16f) else surface
    val surfaceDim = if (dark) surface else tone(0.13f)

    val surfaces = listOf(
        background, surface, surfaceContainerLowest, surfaceContainerLow,
        surfaceContainer, surfaceContainerHigh, surfaceContainerHighest, surfaceVariant
    )
    // Accents are used as text/icon colors on backgrounds, cards and other surface containers.
    val accentBackgrounds = surfaces

    val (primary, onPrimary) = accentPair(palette.primary, accentBackgrounds, textMin)
    val (secondary, onSecondary) = accentPair(palette.secondary, accentBackgrounds, textMin)
    val (tertiary, onTertiary) = accentPair(palette.tertiary, accentBackgrounds, textMin)

    val (primaryContainer, onPrimaryContainer) = containerPair(primary, surface, dark, textMin)
    val (secondaryContainer, onSecondaryContainer) = containerPair(secondary, surface, dark, textMin)
    val (tertiaryContainer, onTertiaryContainer) = containerPair(tertiary, surface, dark, textMin)

    // Neutral greys tinted toward the scheme's own primary (never Material's default purple).
    val onSurfaceSeed = when {
        highContrast -> if (dark) Color.White else Color.Black
        dark -> blend(Color(0xFFE6E6E6), palette.primary, 0.04f)
        else -> blend(Color(0xFF1C1C1C), palette.primary, 0.04f)
    }
    // onSurface is also drawn on selected (container-coloured) cards.
    val onSurface = onSurfaceSeed.ensureContrast(
        surfaces + listOf(primaryContainer, secondaryContainer, tertiaryContainer), textMin
    )
    val onSurfaceVariantSeed = when {
        highContrast -> blend(onSurface, surface, 0.1f)
        dark -> blend(Color(0xFFC8C8C8), palette.primary, 0.08f)
        else -> blend(Color(0xFF4A4A4A), palette.primary, 0.08f)
    }
    val onSurfaceVariant = onSurfaceVariantSeed.ensureContrast(surfaces, textMin)

    val outline = blend(if (dark) Color(0xFF939393) else Color(0xFF787878), palette.primary, 0.08f)
        .ensureContrast(surfaces, uiMin)
    val outlineVariant = blend(surface, onSurface, if (highContrast) 0.45f else 0.2f)

    val error = (if (dark) Color(0xFFFFB4AB) else Color(0xFFB3261E)).ensureContrast(surfaces, textMin)
    val onError = bestContentColor(error)
    val errorContainer = if (dark) blend(surface, Color(0xFF93000A), 0.85f) else Color(0xFFF9DEDC)
    val onErrorContainer = (if (dark) Color(0xFFFFDAD6) else Color(0xFF410E0B))
        .ensureContrast(errorContainer, textMin)

    val inverseSurface = if (dark) Color(0xFFE6E1E5) else Color(0xFF313033)
    val inverseOnSurface = bestContentColor(inverseSurface)
    val inversePrimary = palette.primary.ensureContrast(inverseSurface, textMin)

    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        inversePrimary = inversePrimary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = primary,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        outline = outline,
        outlineVariant = outlineVariant,
        scrim = Color.Black,
        surfaceBright = surfaceBright,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceDim = surfaceDim
    )
}
