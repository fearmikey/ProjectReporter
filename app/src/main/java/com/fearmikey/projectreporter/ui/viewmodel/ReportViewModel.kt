package com.fearmikey.projectreporter.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.data.model.ExportFormat
import com.fearmikey.projectreporter.data.model.ExportOptions
import com.fearmikey.projectreporter.data.model.ReportBranding
import com.fearmikey.projectreporter.data.repository.ReportRepository
import com.fearmikey.projectreporter.data.repository.SettingsRepository
import com.fearmikey.projectreporter.util.PdfExportService
import com.fearmikey.projectreporter.util.WordExportService
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
    private val settingsRepository: SettingsRepository,
    private val pdfExportService: PdfExportService,
    private val wordExportService: WordExportService,
    private val notificationService: com.fearmikey.projectreporter.util.NotificationService
) : ViewModel() {

    private val _projectId = MutableStateFlow<String?>(null)
    
    private val _pdfExportResult = MutableSharedFlow<Result<Unit>>()
    val pdfExportResult = _pdfExportResult.asSharedFlow()
    
    val project: StateFlow<ProjectEntity?> = _projectId
        .flatMapLatest { id ->
            if (id == null) flowOf(null)
            else repository.getProjectById(id)
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

    /** Number of this project's photos/notes currently in the Recycle Bin. */
    val deletedItemCount: StateFlow<Int> = _projectId
        .flatMapLatest { id ->
            if (id == null) flowOf(0)
            else combine(
                repository.getDeletedPhotosForProject(id),
                repository.getDeletedNotesForProject(id)
            ) { photos, notes -> photos.size + notes.size }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun setProjectId(id: String) {
        _projectId.value = id
    }

    fun updateProject(newId: String, newName: String, newEngineer: String, engineerPhone: String = "", clientCompany: String = "", poc: String = "", phoneNumber: String = "", notes: String = "", onResult: (Boolean) -> Unit) {
        val oldId = _projectId.value ?: return
        viewModelScope.launch {
            val success = repository.updateProjectDetails(oldId, newId, newName, newEngineer, engineerPhone, clientCompany, poc, phoneNumber, notes)
            if (success && oldId != newId) {
                _projectId.value = newId
            }
            onResult(success)
        }
    }

    fun addPhoto(uri: String, annotation: String = "") {
        val id = _projectId.value ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(now))
            val photo = PhotoEntity(
                projectId = id,
                imageUri = uri,
                timestampOverlay = timestampStr,
                timestamp = now,
                annotation = annotation
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

    /** Moves the given photos and notes to the Recycle Bin. */
    fun softDeleteItems(photos: List<PhotoEntity>, notes: List<NoteEntity>) {
        viewModelScope.launch {
            photos.forEach { repository.softDeletePhoto(it) }
            notes.forEach { repository.softDeleteNote(it) }
        }
    }

    /** Restores items previously moved to the Recycle Bin (used by the "Undo" snackbar). */
    fun restoreItems(photos: List<PhotoEntity>, notes: List<NoteEntity>) {
        viewModelScope.launch {
            photos.forEach { repository.restorePhoto(it) }
            notes.forEach { repository.restoreNote(it) }
        }
    }

    fun exportReport(options: ExportOptions = ExportOptions()) {
        val currentProject = project.value ?: return
        val currentPhotos = photos.value
        val currentNotes = notes.value
        viewModelScope.launch {
            val branding = loadBranding()
            val result = if (options.format == ExportFormat.PDF) {
                pdfExportService.exportToPdf(currentProject, currentPhotos, currentNotes, options, branding)
            } else {
                wordExportService.exportToDocx(currentProject, currentPhotos, currentNotes, options, branding)
            }
            
            result.onSuccess { uri ->
                notificationService.showExportCompleteNotification(uri, options.format)
            }
            
            _pdfExportResult.emit(result.map { })
        }
    }

    private suspend fun loadBranding(): ReportBranding {
        val settings = runCatching { settingsRepository.themeSettings.first() }.getOrNull()
        val profile = runCatching { repository.getProfile() }.getOrNull()
        return ReportBranding(
            companyName = profile?.company.orEmpty(),
            logoUri = settings?.companyLogoUri ?: profile?.logoUri,
            primaryColor = settings?.logoPrimaryColor,
            secondaryColor = settings?.logoSecondaryColor,
            preparedByRole = profile?.role.orEmpty()
        )
    }
}
