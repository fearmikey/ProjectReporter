package com.fearmikey.projectreporter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

    fun setProjectId(id: String) {
        _projectId.value = id
    }

    fun addPhoto(uri: String) {
        val id = _projectId.value ?: return
        viewModelScope.launch {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val photo = PhotoEntity(
                projectId = id,
                imageUri = uri,
                timestampOverlay = timestamp,
                annotation = ""
            )
            repository.insertPhoto(photo)
        }
    }

    fun updateAnnotation(photo: PhotoEntity, annotation: String) {
        viewModelScope.launch {
            repository.updatePhoto(photo.copy(annotation = annotation))
        }
    }

    fun exportPdf() {
        val currentProject = project.value ?: return
        val currentPhotos = photos.value
        viewModelScope.launch {
            val result = pdfExportService.exportToPdf(currentProject, currentPhotos)
            _pdfExportResult.emit(result)
        }
    }
}
