package com.fearmikey.projectreporter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.ThemeSettings

/** Seed palette for a built-in [ColorSchemeOption]. */
fun paletteFor(option: ColorSchemeOption, dark: Boolean): SchemePalette = when (option) {
    ColorSchemeOption.INDUSTRIAL -> if (dark) {
        SchemePalette(IndustrialSecondary, VibrantBlue, Color(0xFF1ABC9C), IndustrialDarkBackground, IndustrialDarkSurface)
    } else {
        SchemePalette(IndustrialPrimary, IndustrialSecondary, IndustrialTertiary, IndustrialBackground, IndustrialSurface)
    }
    ColorSchemeOption.MIDNIGHT -> if (dark) {
        SchemePalette(MidnightSecondary, VibrantBlue, Color(0xFF1ABC9C), MidnightDarkBackground, MidnightDarkSurface)
    } else {
        SchemePalette(MidnightPrimary, MidnightSecondary, MidnightTertiary, MidnightBackground, MidnightSurface)
    }
    ColorSchemeOption.OCEAN -> if (dark) {
        SchemePalette(OceanSecondary, VibrantBlue, Color(0xFFF39C12), OceanDarkBackground, OceanDarkSurface)
    } else {
        SchemePalette(OceanPrimary, OceanSecondary, OceanTertiary, OceanBackground, OceanSurface)
    }
    ColorSchemeOption.FOREST -> if (dark) {
        SchemePalette(ForestSecondary, Color(0xFF2ECC71), Color(0xFFBCAAA4), ForestDarkBackground, ForestDarkSurface)
    } else {
        SchemePalette(ForestPrimary, ForestSecondary, ForestTertiary, ForestBackground, ForestSurface)
    }
}

/** Fully resolved, contrast-checked scheme for a built-in option (used for settings previews). */
fun previewColorScheme(option: ColorSchemeOption, dark: Boolean, highContrast: Boolean = false): ColorScheme =
    buildAccessibleColorScheme(paletteFor(option, dark), dark, amoled = false, highContrast = highContrast)

@Composable
fun ProjectReporterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val dynamicColor = themeSettings?.useDynamicColor ?: false
    val amoledMode = themeSettings?.amoledMode ?: false
    val highContrast = themeSettings?.highContrast ?: false
    val colorSchemeOption = themeSettings?.colorSchemeOption ?: ColorSchemeOption.INDUSTRIAL
    val logoPrimary = themeSettings?.logoPrimaryColor
    val logoSecondary = themeSettings?.logoSecondaryColor

    val dynamicScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    val colorScheme = remember(
        dynamicScheme, darkTheme, amoledMode, highContrast, colorSchemeOption, logoPrimary, logoSecondary
    ) {
        val basePalette = dynamicScheme?.let {
            SchemePalette(it.primary, it.secondary, it.tertiary, it.background, it.surface)
        } ?: paletteFor(colorSchemeOption, darkTheme)

        // Company branding overrides accent seeds; contrast is enforced by the builder, so
        // very light/dark logo colors are automatically adjusted to stay legible.
        val palette = if (logoPrimary != null) {
            val brandPrimary = Color(logoPrimary)
            basePalette.copy(
                primary = brandPrimary,
                secondary = distinctSecondary(brandPrimary, logoSecondary?.let { Color(it) })
            )
        } else {
            basePalette
        }

        if (dynamicScheme != null && logoPrimary == null && !highContrast) {
            // System dynamic schemes are already tonally balanced; only apply AMOLED.
            if (darkTheme && amoledMode) {
                dynamicScheme.copy(
                    background = Color.Black,
                    surface = Color.Black,
                    surfaceDim = Color.Black,
                    surfaceContainerLowest = Color.Black
                )
            } else {
                dynamicScheme
            }
        } else {
            buildAccessibleColorScheme(palette, darkTheme, amoledMode, highContrast)
        }
    }

    val view = LocalView.current
    val statusBarController = remember(view) {
        StatusBarAppearanceController { lightAppearance ->
            if (!view.isInEditMode) {
                val window = (view.context as Activity).window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = lightAppearance
            }
        }
    }
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            statusBarController.defaultLightAppearance = !darkTheme
        }
    }

    CompositionLocalProvider(LocalStatusBarAppearance provides statusBarController) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

/**
 * Tracks which screen currently owns the status bar so its icons stay legible on whatever
 * colour is drawn behind them (e.g. a primary-coloured top app bar), independent of dark mode.
 * Requests are stacked so overlapping navigation transitions resolve to the newest screen.
 */
class StatusBarAppearanceController(private val apply: (lightAppearance: Boolean) -> Unit) {
    private val requests = mutableListOf<Pair<Any, Boolean>>()

    var defaultLightAppearance: Boolean = false
        set(value) {
            field = value
            update()
        }

    fun request(token: Any, lightAppearance: Boolean) {
        requests.removeAll { it.first === token }
        requests.add(token to lightAppearance)
        update()
    }

    fun release(token: Any) {
        requests.removeAll { it.first === token }
        update()
    }

    private fun update() = apply(requests.lastOrNull()?.second ?: defaultLightAppearance)
}

val LocalStatusBarAppearance = staticCompositionLocalOf<StatusBarAppearanceController?> { null }

/**
 * Call from a screen whose top bar is not the default background colour (e.g. `primary`), so the
 * status bar icons switch to black or white for maximum contrast against [background].
 */
@Composable
fun StatusBarContentFor(background: Color) {
    val controller = LocalStatusBarAppearance.current ?: return
    // "Light appearance" means dark icons, used when black content is more legible.
    val lightAppearance = bestContentColor(background) == Color.Black
    val token = remember { Any() }
    DisposableEffect(controller, lightAppearance) {
        controller.request(token, lightAppearance)
        onDispose { controller.release(token) }
    }
}
