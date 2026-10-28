package com.fearmikey.projectreporter.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.data.model.ExportFormat
import com.fearmikey.projectreporter.data.model.ExportOptions
import com.fearmikey.projectreporter.ui.component.CameraPreview
import com.fearmikey.projectreporter.ui.component.EditProjectDialog
import com.fearmikey.projectreporter.ui.component.NoteEditorDialog
import com.fearmikey.projectreporter.ui.component.NoteFormatter
import com.fearmikey.projectreporter.ui.component.rememberAppHaptics
import com.fearmikey.projectreporter.ui.component.PhotoDetailDialog
import com.fearmikey.projectreporter.ui.theme.StatusBarContentFor
import com.fearmikey.projectreporter.ui.viewmodel.ReportViewModel
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.launch

sealed class ReportItem {
    data class Photo(val photo: PhotoEntity) : ReportItem()
    data class Note(val note: NoteEntity) : ReportItem()

    val id: String
        get() = when (this) {
            is Photo -> "photo_${photo.photoId}"
            is Note -> "note_${note.noteId}"
        }

    val timestamp: Long
        get() = when (this) {
            is Photo -> photo.timestamp
            is Note -> note.timestamp
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onRecycleBinClick: (projectId: String) -> Unit = {},
    viewModel: ReportViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val deletedItemCount by viewModel.deletedItemCount.collectAsStateWithLifecycle()
    val themeSettings by settingsViewModel.themeSettings.collectAsStateWithLifecycle()
    val profile by settingsViewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val reportItems = remember(photos, notes) {
        (photos.map { ReportItem.Photo(it) } + notes.map { ReportItem.Note(it) })
            .sortedByDescending { it.timestamp }
    }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    BackHandler(enabled = isSelectionMode) {
        isSelectionMode = false
        selectedIds = emptySet()
    }

    // Entities behind the current selection (ids look like "photo_12" / "note_3").
    val selectedPhotos = photos.filter { "photo_${it.photoId}" in selectedIds }
    val selectedNotes = notes.filter { "note_${it.noteId}" in selectedIds }

    /** Soft-deletes items into this project's Recycle Bin and offers an Undo snackbar. */
    fun moveToRecycleBin(delPhotos: List<PhotoEntity>, delNotes: List<NoteEntity>) {
        if (delPhotos.isEmpty() && delNotes.isEmpty()) return
        viewModel.softDeleteItems(delPhotos, delNotes)
        haptics.movedToBin()
        val message = "${describeItems(delPhotos.size, delNotes.size).replaceFirstChar { it.uppercase() }} moved to Recycle Bin"
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                withDismissAction = true,
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreItems(delPhotos, delNotes)
                haptics.restored()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.pdfExportResult.collect { result ->
            // Let the NotificationService handle the notification now
            // But we can optionally show an error Toast if it fails
            if (result.isFailure) {
                Toast.makeText(context, "Export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            } else if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                // Notification can't be shown, so confirm the export another way.
                Toast.makeText(context, "Report saved to your Downloads folder", Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(projectId) {
        viewModel.setProjectId(projectId)
    }

    var showCamera by rememberSaveable { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    // Android 13+ requires runtime permission to post the "export complete" notification.
    // Ask when the user starts an export; open the dialog regardless of the answer.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { showExportDialog = true }
    val onExportClick = {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            showExportDialog = true
        }
    }
    var showEditProjectDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    // Tracked by id so the viewer always shows the latest saved note.
    var photoIdToView by rememberSaveable { mutableStateOf<Long?>(null) }
    val photoToView = photoIdToView?.let { id -> photos.find { it.photoId == id } }
    val gridState = rememberLazyGridState()

    StatusBarContentFor(
        if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        if (isSelectionMode) {
                            Text("${selectedIds.size} selected")
                        } else {
                            Text(project?.projectName ?: "Loading...", fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedIds = emptySet()
                            } else onBack()
                        }) {
                            Icon(
                                if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = if (isSelectionMode) "Clear selection" else "Back"
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                selectedIds = reportItems.map { it.id }.toSet()
                                haptics.selectionChanged()
                            }) {
                                Icon(Icons.Default.SelectAll, contentDescription = "Select all")
                            }
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                enabled = selectedIds.isNotEmpty()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        } else {
                            IconButton(
                                onClick = { showEditProjectDialog = true },
                                enabled = project != null
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Project")
                            }
                            IconButton(onClick = { 
                                if (reportItems.isNotEmpty()) {
                                    isSelectionMode = true
                                    selectedIds = emptySet()
                                    haptics.selectionChanged()
                                }
                            }) {
                                Icon(Icons.Default.Checklist, contentDescription = "Select")
                            }
                            IconButton(onClick = { onRecycleBinClick(project?.projectId ?: projectId) }) {
                                BadgedBox(
                                    badge = {
                                        if (deletedItemCount > 0) {
                                            Badge { Text(if (deletedItemCount > 99) "99+" else deletedItemCount.toString()) }
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin")
                                }
                            }
                            IconButton(onClick = onExportClick) {
                                Icon(Icons.Default.Share, contentDescription = "Export PDF")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        titleContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        actionIconContentColor = if (isSelectionMode) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showCamera = true },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        text = { Text("Take Photo", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp),
                        modifier = Modifier.width(160.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showNoteDialog = true },
                        icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null, modifier = Modifier.size(24.dp)) },
                        text = { Text("Take Note", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        elevation = FloatingActionButtonDefaults.elevation(8.dp),
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(reportItems, key = { it.id }, span = { item ->
                        GridItemSpan(if (item is ReportItem.Note) 3 else 1)
                    }) { item ->
                        val isSelected = selectedIds.contains(item.id)
                        when (item) {
                            is ReportItem.Photo -> PhotoGridItem(
                                photo = item.photo,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onToggleSelect = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(item.id)
                                    } else {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        haptics.selectionToggled(!isSelected)
                                    } else {
                                        photoIdToView = item.photo.photoId
                                    }
                                }
                            )
                            is ReportItem.Note -> NoteGridItem(
                                note = item.note,
                                isSelected = isSelected,
                                isSelectionMode = isSelectionMode,
                                onToggleSelect = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedIds = setOf(item.id)
                                    } else {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    }
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                        haptics.selectionToggled(!isSelected)
                                    } else {
                                        noteToEdit = item.note
                                        showNoteDialog = true
                                    }
                                }
                            )
                        }
                    }
                }

                if (reportItems.isNotEmpty()) {
                    VerticalScrollbar(
                        gridState = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(padding)
                            .padding(end = 4.dp)
                    )
                }
            }
        }

        // Camera Overlay
        if (showCamera) {
            BackHandler {
                showCamera = false
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(1f)
            ) {
                CameraScreen(
                    // Only saves; the camera closes itself after a reviewed photo and
                    // stays open in fast-capture mode.
                    onPhotoCaptured = { uri, annotation ->
                        viewModel.addPhoto(uri.toString(), annotation)
                    },
                    onClose = { showCamera = false },
                    defaultFlashMode = themeSettings.defaultFlashMode,
                    watermarkTimestamp = themeSettings.watermarkTimestamp,
                    watermarkGps = themeSettings.watermarkGps,
                    watermarkProjectDetails = themeSettings.watermarkProjectDetails,
                    projectName = project?.projectName ?: ""
                )
            }
        }
    }

    if (showNoteDialog) {
        val editingNote = noteToEdit
        val closeNoteDialog = {
            showNoteDialog = false
            noteToEdit = null
        }
        NoteEditorDialog(
            initialContent = editingNote?.content ?: "",
            createdAt = editingNote?.timestamp,
            onDismiss = closeNoteDialog,
            onSave = { content ->
                if (editingNote != null) {
                    viewModel.updateNote(editingNote, content)
                } else {
                    viewModel.addNote(content)
                }
                closeNoteDialog()
            },
            onDelete = editingNote?.let { note ->
                {
                    closeNoteDialog()
                    moveToRecycleBin(emptyList(), listOf(note))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        val description = describeItems(selectedPhotos.size, selectedNotes.size)
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Delete $description?") },
            text = {
                val single = selectedPhotos.size + selectedNotes.size == 1
                Text(
                    if (single) "It'll be moved to this report's Recycle Bin, where you can restore it for 30 days."
                    else "They'll be moved to this report's Recycle Bin, where you can restore them for 30 days."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        moveToRecycleBin(selectedPhotos, selectedNotes)
                        selectedIds = emptySet()
                        isSelectionMode = false
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    photoToView?.let { photo ->
        PhotoDetailDialog(
            photo = photo,
            onDismiss = { photoIdToView = null },
            onSaveNote = { viewModel.updateAnnotation(photo, it) },
            onDelete = {
                photoIdToView = null
                moveToRecycleBin(listOf(photo), emptyList())
            }
        )
    }

    if (showExportDialog) {
        ExportOptionsDialog(
            onDismiss = { showExportDialog = false },
            onConfirm = { options ->
                viewModel.exportReport(options)
                showExportDialog = false
            }
        )
    }

    if (showEditProjectDialog && project != null) {
        EditProjectDialog(
            project = project!!,
            role = profile?.role ?: "",
            onDismiss = { showEditProjectDialog = false },
            onConfirm = { _, newId, newName, newEngineer, newEngineerPhone, newClientCompany, newPoc, newPhoneNumber, newNotes, onResult ->
                viewModel.updateProject(newId, newName, newEngineer, newEngineerPhone, newClientCompany, newPoc, newPhoneNumber, newNotes) { success ->
                    if (success) {
                        showEditProjectDialog = false
                    }
                    onResult(success)
                }
            }
        )
    }
}

/** e.g. "2 photos and 1 note", "1 photo", "3 notes". */
private fun describeItems(photoCount: Int, noteCount: Int): String {
    val parts = buildList {
        if (photoCount > 0) add(if (photoCount == 1) "1 photo" else "$photoCount photos")
        if (noteCount > 0) add(if (noteCount == 1) "1 note" else "$noteCount notes")
    }
    return parts.joinToString(" and ").ifEmpty { "0 items" }
}

@Composable
fun ExportOptionsDialog(
    onDismiss: () -> Unit,
    onConfirm: (ExportOptions) -> Unit
) {
    var format by remember { mutableStateOf(ExportFormat.PDF) }
    var photosPerPage by remember { mutableIntStateOf(1) }
    var includeNotes by remember { mutableStateOf(true) }
    var includeTimestamp by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Report Options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Export Format", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExportFormat.values().forEach { option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = format == option,
                                onClick = { format = option }
                            )
                            Text(text = if (option == ExportFormat.PDF) "PDF" else "Word (.docx)")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (format == ExportFormat.PDF) {
                    Text("Photos Per Page", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(1, 2, 4).forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = photosPerPage == option,
                                    onClick = { photosPerPage = option }
                                )
                                Text(text = option.toString())
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeNotes, onCheckedChange = { includeNotes = it })
                    Text("Include Notes")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeTimestamp, onCheckedChange = { includeTimestamp = it })
                    Text("Include Timestamps")
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Photos-per-page is a PDF-only option (hidden for Word), so don't leak a stale choice
                val perPage = if (format == ExportFormat.PDF) photosPerPage else 1
                onConfirm(ExportOptions(format, perPage, includeNotes, includeTimestamp))
            }) {
                Text("Export")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VerticalScrollbar(
    gridState: LazyGridState,
    modifier: Modifier = Modifier
) {
    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (gridState.isScrollInProgress) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxHeight(0.5f)
            .width(4.dp)
            .alpha(scrollbarAlpha)
            .background(Color.Gray.copy(alpha = 0.5f), CircleShape)
    ) {
        val layoutInfo = gridState.layoutInfo
        val totalItems = layoutInfo.totalItemsCount
        val visibleItems = layoutInfo.visibleItemsInfo.size
        if (totalItems > visibleItems && visibleItems > 0) {
            val scrollFraction = gridState.firstVisibleItemIndex.toFloat() / (totalItems - visibleItems)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(visibleItems.toFloat() / totalItems)
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        translationY = 100.dp.toPx() * scrollFraction // Rough adjustment
                    }
                    .background(Color.DarkGray, CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoGridItem(
    photo: PhotoEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleSelect
            )
    ) {
        AsyncImage(
            model = photo.imageUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        
        if (photo.annotation.isNotBlank()) {
            PhotoNoteIndicator(
                note = photo.annotation,
                modifier = Modifier.matchParentSize()
            )
        }

        if (isSelectionMode) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = 0.4f))
                )
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.White, CircleShape)
                        .clip(CircleShape)
                )
            } else {
                Icon(
                    Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Not selected",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                        .padding(2.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

/**
 * High-visibility marker for photos that carry a note: a bold badge in the top-end corner
 * plus a one-line preview of the note over a dark gradient along the bottom edge.
 */
@Composable
private fun PhotoNoteIndicator(note: String, modifier: Modifier = Modifier) {
    val preview = remember(note) { notePreview(note) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))
                )
                .padding(start = 6.dp, end = 6.dp, top = 18.dp, bottom = 5.dp)
        ) {
            Text(
                text = preview,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            border = BorderStroke(1.5.dp, Color.White),
            shadowElevation = 4.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(28.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Has note",
                modifier = Modifier.padding(5.dp)
            )
        }
    }
}

/** First non-blank line of a note with any list marker ("- ", "1. ", "☐ ") stripped. */
private fun notePreview(note: String): String {
    val line = note.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    val prefix = NoteFormatter.parsePrefix(line)
    return if (prefix != null) line.substring(prefix.length).trim() else line
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteGridItem(
    note: NoteEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleSelect
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val dateStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(note.timestamp))
                Text(
                    text = "Note • $dateStr",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            if (isSelectionMode) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (isSelected) "Selected" else "Not selected",
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun CameraScreen(
    onPhotoCaptured: (Uri, String) -> Unit,
    onClose: () -> Unit,
    defaultFlashMode: com.fearmikey.projectreporter.data.repository.FlashModeOption,
    watermarkTimestamp: Boolean = true,
    watermarkGps: Boolean = false,
    watermarkProjectDetails: Boolean = true,
    projectName: String = ""
) {
    CameraPreview(
        onPhotoCaptured = onPhotoCaptured,
        onClose = onClose,
        defaultFlashMode = defaultFlashMode,
        watermarkTimestamp = watermarkTimestamp,
        watermarkGps = watermarkGps,
        watermarkProjectDetails = watermarkProjectDetails,
        projectName = projectName
    )
}
