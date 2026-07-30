package com.fearmikey.projectreporter.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.ThemeSettings

private val IndustrialDarkColorScheme = darkColorScheme(
    primary = IndustrialSecondary,
    secondary = IndustrialPrimary,
    tertiary = Pink80,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0)
)

private val IndustrialLightColorScheme = lightColorScheme(
    primary = IndustrialPrimary,
    secondary = IndustrialSecondary,
    tertiary = Pink40,
    background = IndustrialBackground,
    surface = IndustrialSurface,
    onBackground = IndustrialOnBackground,
    onSurface = IndustrialOnSurface
)

private val MidnightDarkColorScheme = darkColorScheme(
    primary = MidnightSecondary,
    secondary = MidnightPrimary,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B)
)

private val MidnightLightColorScheme = lightColorScheme(
    primary = MidnightPrimary,
    secondary = MidnightSecondary,
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF)
)

private val OceanDarkColorScheme = darkColorScheme(
    primary = OceanSecondary,
    secondary = OceanPrimary,
    background = Color(0xFF0B1622),
    surface = Color(0xFF15202B)
)

private val OceanLightColorScheme = lightColorScheme(
    primary = OceanPrimary,
    secondary = OceanSecondary,
    background = OceanBackground,
    surface = OceanSurface
)

private val ForestDarkColorScheme = darkColorScheme(
    primary = ForestSecondary,
    secondary = ForestPrimary,
    background = Color(0xFF0D1B0D),
    surface = Color(0xFF1B2B1B)
)

private val ForestLightColorScheme = lightColorScheme(
    primary = ForestPrimary,
    secondary = ForestSecondary,
    background = ForestBackground,
    surface = ForestSurface
)

@Composable
fun ProjectReporterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    themeSettings: ThemeSettings? = null,
    content: @Composable () -> Unit
) {
    val dynamicColor = themeSettings?.useDynamicColor ?: false
    val amoledMode = themeSettings?.amoledMode ?: false
    val colorSchemeOption = themeSettings?.colorSchemeOption ?: ColorSchemeOption.INDUSTRIAL

    var colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> {
            when (colorSchemeOption) {
                ColorSchemeOption.INDUSTRIAL -> IndustrialDarkColorScheme
                ColorSchemeOption.MIDNIGHT -> MidnightDarkColorScheme
                ColorSchemeOption.OCEAN -> OceanDarkColorScheme
                ColorSchemeOption.FOREST -> ForestDarkColorScheme
            }
        }
        else -> {
            when (colorSchemeOption) {
                ColorSchemeOption.INDUSTRIAL -> IndustrialLightColorScheme
                ColorSchemeOption.MIDNIGHT -> MidnightLightColorScheme
                ColorSchemeOption.OCEAN -> OceanLightColorScheme
                ColorSchemeOption.FOREST -> ForestLightColorScheme
            }
        }
    }

    if (darkTheme && amoledMode) {
        colorScheme = colorScheme.copy(
            background = Color.Black,
            surface = Color.Black
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
