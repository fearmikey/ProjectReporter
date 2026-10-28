package com.fearmikey.projectreporter.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.data.repository.SettingsRepository
import com.fearmikey.projectreporter.data.repository.ThemeSettings
import com.fearmikey.projectreporter.util.BackupImportResult
import com.fearmikey.projectreporter.util.BackupService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class BackupUiState {
    object Idle : BackupUiState()
    object Processing : BackupUiState()
    data class ExportSuccess(val count: Int) : BackupUiState()
    data class ImportSuccess(val result: BackupImportResult) : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val reportRepository: ReportRepository,
    private val backupService: BackupService,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val appVersion: String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown"
    } catch (e: Exception) {
        "Unknown"
    }

    private val _backupUiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val backupUiState: StateFlow<BackupUiState> = _backupUiState.asStateFlow()

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

    fun resetBackupUiState() {
        _backupUiState.value = BackupUiState.Idle
    }

    fun exportBackup(uri: Uri) {
        _backupUiState.value = BackupUiState.Processing
        viewModelScope.launch {
            val outputStream = runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
            if (outputStream == null) {
                _backupUiState.value = BackupUiState.Error("Failed to open output file for writing.")
                return@launch
            }

            outputStream.use { stream ->
                val result = backupService.exportBackup(stream)
                result.fold(
                    onSuccess = { count ->
                        _backupUiState.value = BackupUiState.ExportSuccess(count)
                    },
                    onFailure = { error ->
                        _backupUiState.value = BackupUiState.Error(error.localizedMessage ?: "Failed to export backup.")
                    }
                )
            }
        }
    }

    fun importBackup(uri: Uri) {
        _backupUiState.value = BackupUiState.Processing
        viewModelScope.launch {
            val inputStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (inputStream == null) {
                _backupUiState.value = BackupUiState.Error("Failed to open backup file for reading.")
                return@launch
            }

            inputStream.use { stream ->
                val result = backupService.importBackup(stream)
                result.fold(
                    onSuccess = { importResult ->
                        _backupUiState.value = BackupUiState.ImportSuccess(importResult)
                    },
                    onFailure = { error ->
                        _backupUiState.value = BackupUiState.Error(error.localizedMessage ?: "Failed to import backup.")
                    }
                )
            }
        }
    }

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

    fun setHighContrast(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setHighContrast(enabled)
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

    fun updateProfile(name: String, phone: String, company: String, role: String) {
        viewModelScope.launch {
            val current = profile.value
            if (current != null) {
                reportRepository.insertProfile(current.copy(engineerName = name, engineerPhone = phone, company = company, role = role))
            } else {
                reportRepository.insertProfile(ProfileEntity(engineerName = name, engineerPhone = phone, company = company, role = role))
            }
        }
    }
}
