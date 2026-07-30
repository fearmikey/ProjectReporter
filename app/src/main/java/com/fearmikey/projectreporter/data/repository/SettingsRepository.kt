package com.fearmikey.projectreporter.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

enum class ColorSchemeOption {
    INDUSTRIAL, MIDNIGHT, OCEAN, FOREST
}

enum class FlashModeOption {
    OFF, ON, AUTO
}

data class ThemeSettings(
    val appTheme: AppTheme,
    val useDynamicColor: Boolean,
    val amoledMode: Boolean,
    val colorSchemeOption: ColorSchemeOption,
    val defaultFlashMode: FlashModeOption
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("app_theme")
    private val dynamicColorKey = booleanPreferencesKey("use_dynamic_color")
    private val amoledModeKey = booleanPreferencesKey("amoled_mode")
    private val colorSchemeKey = stringPreferencesKey("color_scheme")
    private val flashModeKey = stringPreferencesKey("default_flash_mode")

    val themeSettings: Flow<ThemeSettings> = context.dataStore.data.map { preferences ->
        val themeName = preferences[themeKey] ?: AppTheme.SYSTEM.name
        val appTheme = try {
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }

        val colorSchemeName = preferences[colorSchemeKey] ?: ColorSchemeOption.INDUSTRIAL.name
        val colorSchemeOption = try {
            ColorSchemeOption.valueOf(colorSchemeName)
        } catch (e: Exception) {
            ColorSchemeOption.INDUSTRIAL
        }

        val flashModeName = preferences[flashModeKey] ?: FlashModeOption.AUTO.name
        val flashModeOption = try {
            FlashModeOption.valueOf(flashModeName)
        } catch (e: Exception) {
            FlashModeOption.AUTO
        }

        ThemeSettings(
            appTheme = appTheme,
            useDynamicColor = preferences[dynamicColorKey] ?: false,
            amoledMode = preferences[amoledModeKey] ?: false,
            colorSchemeOption = colorSchemeOption,
            defaultFlashMode = flashModeOption
        )
    }

    val appTheme: Flow<AppTheme> = themeSettings.map { it.appTheme }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences ->
            preferences[themeKey] = theme.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[dynamicColorKey] = enabled
        }
    }

    suspend fun setAmoledMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[amoledModeKey] = enabled
        }
    }

    suspend fun setColorScheme(option: ColorSchemeOption) {
        context.dataStore.edit { preferences ->
            preferences[colorSchemeKey] = option.name
        }
    }

    suspend fun setDefaultFlashMode(option: FlashModeOption) {
        context.dataStore.edit { preferences ->
            preferences[flashModeKey] = option.name
        }
    }
}
