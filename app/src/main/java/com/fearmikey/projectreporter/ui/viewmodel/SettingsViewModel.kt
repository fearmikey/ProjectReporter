package com.fearmikey.projectreporter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.data.repository.SettingsRepository
import com.fearmikey.projectreporter.data.repository.ThemeSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }

    val appTheme: StateFlow<AppTheme> = settingsRepository.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.DARK)

    val themeSettings: StateFlow<ThemeSettings> = settingsRepository.themeSettings
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ThemeSettings(AppTheme.DARK, false, false, ColorSchemeOption.INDUSTRIAL, FlashModeOption.OFF)
        )

    val profile: StateFlow<ProfileEntity?> = reportRepository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setDynamicColor(enabled)
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAmoledMode(enabled)
        }
    }

    fun setColorScheme(option: ColorSchemeOption) {
        viewModelScope.launch {
            settingsRepository.setColorScheme(option)
        }
    }

    fun setDefaultFlashMode(option: FlashModeOption) {
        viewModelScope.launch {
            settingsRepository.setDefaultFlashMode(option)
        }
    }

    fun setWatermarkTimestamp(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWatermarkTimestamp(enabled)
        }
    }

    fun setWatermarkGps(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWatermarkGps(enabled)
        }
    }

    fun setWatermarkProjectDetails(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setWatermarkProjectDetails(enabled)
        }
    }

    fun updateCompanyLogo(uri: String?, primaryColor: Int?, secondaryColor: Int?) {
        viewModelScope.launch {
            settingsRepository.setCompanyLogo(uri, primaryColor, secondaryColor)
        }
    }

    fun updateProfile(name: String) {
        viewModelScope.launch {
            reportRepository.insertProfile(ProfileEntity(engineerName = name))
        }
    }
}
