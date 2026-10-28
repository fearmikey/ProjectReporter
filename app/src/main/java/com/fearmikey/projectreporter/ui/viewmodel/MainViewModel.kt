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

    /** Total number of reports currently in the Recycle Bin. */
    val deletedItemCount: StateFlow<Int> = repository.getDeletedProjects()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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

    fun saveProfile(name: String, phone: String = "", company: String = "", role: String = "") {
        viewModelScope.launch {
            repository.insertProfile(ProfileEntity(engineerName = name, engineerPhone = phone, company = company, role = role))
        }
    }

    fun createProject(id: String, name: String, engineer: String, engineerPhone: String = "", clientCompany: String = "", poc: String = "", phoneNumber: String = "", notes: String = "") {
        viewModelScope.launch {
            val project = ProjectEntity(
                projectId = id,
                projectName = name,
                timestamp = System.currentTimeMillis(),
                engineerName = engineer,
                engineerPhone = engineerPhone,
                clientCompany = clientCompany,
                poc = poc,
                phoneNumber = phoneNumber,
                notes = notes
            )
            repository.insertProject(project)
        }
    }

    fun updateProject(oldId: String, newId: String, newName: String, newEngineer: String, engineerPhone: String = "", clientCompany: String = "", poc: String = "", phoneNumber: String = "", notes: String = "", onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.updateProjectDetails(oldId, newId, newName, newEngineer, engineerPhone, clientCompany, poc, phoneNumber, notes)
            onResult(success)
        }
    }

    fun checkProjectExists(projectId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            // Must include projects in the Recycle Bin: inserting over one (REPLACE) would
            // cascade-delete its photos and notes.
            onResult(repository.isProjectIdInUse(projectId))
        }
    }

    fun softDeleteProject(project: ProjectEntity) {
        viewModelScope.launch {
            repository.softDeleteProject(project)
        }
    }

    /** Moves projects (with their photos and notes) to the Recycle Bin. */
    fun softDeleteProjects(projects: List<ProjectEntity>) {
        viewModelScope.launch {
            projects.forEach { repository.softDeleteProject(it) }
        }
    }

    /** Restores projects previously moved to the Recycle Bin (used by the "Undo" snackbar). */
    fun restoreProjects(projects: List<ProjectEntity>) {
        viewModelScope.launch {
            projects.forEach { repository.restoreProject(it) }
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
