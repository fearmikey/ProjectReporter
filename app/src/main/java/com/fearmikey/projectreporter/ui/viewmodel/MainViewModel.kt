package com.fearmikey.projectreporter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.ProfileEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ProfileState {
    object Loading : ProfileState()
    data class Success(val profile: ProfileEntity?) : ProfileState()
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    init {
        purgeOldItems()
    }

    val projects: StateFlow<List<ProjectEntity>> = repository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val profileState: StateFlow<ProfileState> = repository.getProfileFlow()
        .map { ProfileState.Success(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ProfileState.Loading
        )

    val profile: StateFlow<ProfileEntity?> = repository.getProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun saveProfile(name: String) {
        viewModelScope.launch {
            repository.insertProfile(ProfileEntity(engineerName = name))
        }
    }

    fun createProject(id: String, name: String, engineer: String) {
        viewModelScope.launch {
            val project = ProjectEntity(
                projectId = id,
                projectName = name,
                timestamp = System.currentTimeMillis(),
                engineerName = engineer
            )
            repository.insertProject(project)
        }
    }

    fun softDeleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.softDeleteProject(project)
        }
    }

    private fun purgeOldItems() {
        viewModelScope.launch {
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
            val threshold = System.currentTimeMillis() - thirtyDaysMillis
            repository.purgeOldDeletedItems(threshold)
        }
    }
}
