package com.fearmikey.projectreporter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.util.PdfExportService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportViewModel @Inject constructor(
    private val repository: ReportRepository,
    private val pdfExportService: PdfExportService
) : ViewModel() {

    private val _projectId = MutableStateFlow<String?>(null)
    
    private val _pdfExportResult = MutableSharedFlow<Result<Unit>>()
    val pdfExportResult = _pdfExportResult.asSharedFlow()
    
    val project: StateFlow<ProjectEntity?> = _projectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else flow<ProjectEntity?> { emit(repository.getProjectById(id)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val photos: StateFlow<List<PhotoEntity>> = _projectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getPhotosForProject(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<NoteEntity>> = _projectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getNotesForProject(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setProjectId(id: String) {
        _projectId.value = id
    }

    fun addPhoto(uri: String) {
        val id = _projectId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))
            val photo = PhotoEntity(
                projectId = id,
                imageUri = uri,
                timestampOverlay = timestampStr,
                timestamp = now,
                annotation = ""
            )
            repository.insertPhoto(photo)
        }
    }

    fun addNote(content: String) {
        val id = _projectId.value ?: return
        viewModelScope.launch {
            repository.insertNote(NoteEntity(projectId = id, content = content))
        }
    }

    fun updateAnnotation(photo: PhotoEntity, annotation: String) {
        viewModelScope.launch {
            repository.updatePhoto(photo.copy(annotation = annotation))
        }
    }

    fun updateNote(note: NoteEntity, content: String) {
        viewModelScope.launch {
            repository.updateNote(note.copy(content = content))
        }
    }
    
    fun softDeletePhoto(photo: PhotoEntity) {
        viewModelScope.launch {
            repository.softDeletePhoto(photo)
        }
    }

    fun softDeleteNote(note: NoteEntity) {
        viewModelScope.launch {
            repository.softDeleteNote(note)
        }
    }

    fun exportPdf() {
        val currentProject = project.value ?: return
        val currentPhotos = photos.value
        val currentNotes = notes.value
        viewModelScope.launch {
            val result = pdfExportService.exportToPdf(currentProject, currentPhotos, currentNotes)
            _pdfExportResult.emit(result)
        }
    }
}
