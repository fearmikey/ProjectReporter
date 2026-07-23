package com.fearmikey.projectreporter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.data.repository.SettingsRepository
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
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    val profile: StateFlow<ProfileEntity?> = reportRepository.getProfileFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            settingsRepository.setAppTheme(theme)
        }
    }

    fun updateProfile(name: String) {
        viewModelScope.launch {
            reportRepository.insertProfile(ProfileEntity(engineerName = name))
        }
    }
}
