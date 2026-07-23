package com.fearmikey.projectreporter.ui.viewmodel

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
}

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val repository: ReportRepository
) : ViewModel() {

    val deletedItems: StateFlow<List<DeletedItem>> = combine(
        repository.getDeletedPhotos(),
        repository.getDeletedNotes(),
        repository.getDeletedProjects()
    ) { photos, notes, projects ->
        (photos.map { DeletedItem.Photo(it) } + 
         notes.map { DeletedItem.Note(it) } +
         projects.map { DeletedItem.Project(it) })
            .sortedByDescending { it.deletedTimestamp }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun restoreItem(item: DeletedItem) {
        viewModelScope.launch {
            when (item) {
                is DeletedItem.Photo -> repository.restorePhoto(item.photo)
                is DeletedItem.Note -> repository.restoreNote(item.note)
                is DeletedItem.Project -> repository.restoreProject(item.project)
            }
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
            // Threshold is basically "everything currently in bin" for manual empty
            repository.purgeOldDeletedItems(System.currentTimeMillis() + 1000)
        }
    }
    
    fun purgeOldItems() {
        viewModelScope.launch {
            val thirtyDaysMillis = 30L * 24 * 60 * 60 * 1000
            val threshold = System.currentTimeMillis() - thirtyDaysMillis
            repository.purgeOldDeletedItems(threshold)
        }
    }
}
