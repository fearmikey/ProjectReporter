package com.fearmikey.projectreporter.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.repository.ReportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class DeletedItem {
    data class Photo(val photo: PhotoEntity) : DeletedItem()
    data class Note(val note: NoteEntity) : DeletedItem()
    data class Project(val project: ProjectEntity) : DeletedItem()

    val id: String
        get() = when (this) {
            is Photo -> "photo_${photo.photoId}"
            is Note -> "note_${note.noteId}"
            is Project -> "project_${project.projectId}"
        }

    val deletedTimestamp: Long
        get() = when (this) {
            is Photo -> photo.deletedTimestamp ?: 0L
            is Note -> note.deletedTimestamp ?: 0L
            is Project -> project.deletedTimestamp ?: 0L
        }

    /** Project the item belongs to (for photos/notes) or represents (for projects). */
    val projectId: String
        get() = when (this) {
            is Photo -> photo.projectId
            is Note -> note.projectId
            is Project -> project.projectId
        }
}

/**
 * Backs both the global Recycle Bin (showing deleted reports) and a per-project bin
 * (showing deleted photos and notes for that report).
 */
@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: ReportRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val projectId: String? = savedStateHandle.get<String>(PROJECT_ID_ARG)?.takeIf { it.isNotBlank() }

    val projectName: StateFlow<String?> =
        (projectId?.let { id -> repository.getProjectById(id).map { it?.projectName } } ?: flowOf(null))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deletedItems: StateFlow<List<DeletedItem>> = if (projectId != null) {
        combine(
            repository.getDeletedPhotosForProject(projectId),
            repository.getDeletedNotesForProject(projectId)
        ) { photos, notes ->
            (photos.map { DeletedItem.Photo(it) } + notes.map { DeletedItem.Note(it) })
                .sortedByDescending { it.deletedTimestamp }
        }
    } else {
        repository.getDeletedProjects().map { projects ->
            projects.map { DeletedItem.Project(it) }
                .sortedByDescending { it.deletedTimestamp }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreItem(item: DeletedItem) {
        viewModelScope.launch { restoreItemNow(item) }
    }

    fun restoreAll() {
        val items = deletedItems.value
        viewModelScope.launch { items.forEach { restoreItemNow(it) } }
    }

    private suspend fun restoreItemNow(item: DeletedItem) {
        when (item) {
            is DeletedItem.Photo -> repository.restorePhoto(item.photo)
            is DeletedItem.Note -> repository.restoreNote(item.note)
            is DeletedItem.Project -> repository.restoreProject(item.project)
        }
    }

    fun deletePermanently(item: DeletedItem) {
        viewModelScope.launch {
            when (item) {
                is DeletedItem.Photo -> repository.deletePhotoPermanently(item.photo)
                is DeletedItem.Note -> repository.deleteNotePermanently(item.note)
                is DeletedItem.Project -> repository.deleteProjectPermanently(item.project)
            }
        }
    }

    fun emptyBin() {
        viewModelScope.launch {
            if (projectId != null) {
                repository.purgeDeletedItemsForProject(projectId)
            } else {
                repository.purgeOldDeletedItems(System.currentTimeMillis() + 1000)
            }
        }
    }

    fun purgeOldItems() {
        viewModelScope.launch {
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
            val threshold = System.currentTimeMillis() - thirtyDaysMillis
            repository.purgeOldDeletedItems(threshold)
        }
    }

    companion object {
        const val PROJECT_ID_ARG = "projectId"
    }
}
