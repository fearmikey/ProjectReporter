package com.fearmikey.projectreporter.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import com.fearmikey.projectreporter.ui.viewmodel.ReportViewModel
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
    viewModel: ReportViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val themeSettings by settingsViewModel.themeSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val reportItems = remember(photos, notes) {
        (photos.map { ReportItem.Photo(it) } + notes.map { ReportItem.Note(it) })
            .sortedByDescending { it.timestamp }
    }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedIds = emptySet()
    }

    LaunchedEffect(Unit) {
        viewModel.pdfExportResult.collect { result ->
            val message = if (result.isSuccess) "PDF exported to Downloads" else "Export failed: ${result.exceptionOrNull()?.message}"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(projectId) {
        viewModel.setProjectId(projectId)
    }

    var showCamera by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showEditProjectDialog by remember { mutableStateOf(false) }
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var photoToView by remember { mutableStateOf<PhotoEntity?>(null) }
    val gridState = rememberLazyGridState()

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
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
                            if (isSelectionMode) selectedIds = emptySet()
                            else onBack()
                        }) {
                            Icon(
                                if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    actions = {
                        if (isSelectionMode) {
                            IconButton(onClick = {
                                selectedIds.forEach { id ->
                                    if (id.startsWith("photo_")) {
                                        val photoId = id.removePrefix("photo_").toLong()
                                        photos.find { it.photoId == photoId }?.let { viewModel.softDeletePhoto(it) }
                                    } else {
                                        val noteId = id.removePrefix("note_").toLong()
                                        notes.find { it.noteId == noteId }?.let { viewModel.softDeleteNote(it) }
                                    }
                                }
                                selectedIds = emptySet()
                            }) {
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
                                // Enter selection mode manually
                                if (reportItems.isNotEmpty()) {
                                    selectedIds = setOf(reportItems.first().id)
                                }
                            }) {
                                Icon(Icons.Default.Checklist, contentDescription = "Select")
                            }
                            IconButton(onClick = { showExportDialog = true }) {
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
                                onToggleSelect = {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                    } else {
                                        photoToView = item.photo
                                    }
                                }
                            )
                            is ReportItem.Note -> NoteGridItem(
                                note = item.note,
                                isSelected = isSelected,
                                onToggleSelect = {
                                    selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
                                },
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedIds = if (isSelected) selectedIds - item.id else selectedIds + item.id
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
                    onPhotoCaptured = { uri, annotation ->
                        viewModel.addPhoto(uri.toString(), annotation)
                        showCamera = false
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
        AddNoteDialog(
            initialContent = noteToEdit?.content ?: "",
            onDismiss = {
                showNoteDialog = false
                noteToEdit = null
            },
            onConfirm = { content ->
                if (noteToEdit != null) {
                    viewModel.updateNote(noteToEdit!!, content)
                } else {
                    viewModel.addNote(content)
                }
                showNoteDialog = false
                noteToEdit = null
            }
        )
    }

    if (photoToView != null) {
        PhotoDetailDialog(
            photo = photoToView!!,
            onDismiss = { photoToView = null },
            onUpdateAnnotation = { viewModel.updateAnnotation(photoToView!!, it) }
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
            onDismiss = { showEditProjectDialog = false },
            onConfirm = { _, newId, newName, newEngineer, onResult ->
                viewModel.updateProject(newId, newName, newEngineer) { success ->
                    if (success) {
                        showEditProjectDialog = false
                    }
                    onResult(success)
                }
            }
        )
    }
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
                onConfirm(ExportOptions(format, photosPerPage, includeNotes, includeTimestamp))
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
            Icon(
                Icons.AutoMirrored.Filled.Notes,
                contentDescription = "Has Note",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(2.dp)
                    .size(16.dp)
            )
        } else {
            Icon(
                Icons.Default.EditNote,
                contentDescription = "Add Note",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(16.dp)
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.4f))
            )
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .background(Color.White, CircleShape)
                    .clip(CircleShape)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteGridItem(
    note: NoteEntity,
    isSelected: Boolean,
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
        Column(modifier = Modifier.padding(12.dp)) {
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
    }
}

@Composable
fun AddNoteDialog(
    initialContent: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textFieldValue by remember { 
        mutableStateOf(TextFieldValue(text = initialContent, selection = TextRange(initialContent.length)))
    }

    val insertMarkdown = { prefix: String ->
        val text = textFieldValue.text
        val selectionStart = textFieldValue.selection.start
        
        // Find the start of the current line
        val lineStart = text.lastIndexOf('\n', selectionStart - 1).let { if (it == -1) 0 else it + 1 }
        
        val newText = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val newCursor = selectionStart + prefix.length
        
        textFieldValue = TextFieldValue(text = newText, selection = TextRange(newCursor))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialContent.isEmpty()) "Add Note" else "Edit Note") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Formatting toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { insertMarkdown("- ") },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.FormatListBulleted, contentDescription = "Bullet List", modifier = Modifier.size(20.dp))
                    }
                    IconButton(
                        onClick = { insertMarkdown("1. ") },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape).size(36.dp)
                    ) {
                        Icon(Icons.Default.FormatListNumbered, contentDescription = "Numbered List", modifier = Modifier.size(20.dp))
                    }
                }
                
                OutlinedTextField(
                    value = textFieldValue,
                    onValueChange = { textFieldValue = it },
                    label = { Text("Note content") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(textFieldValue.text) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PhotoDetailDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onUpdateAnnotation: (String) -> Unit
) {
    var annotation by remember { mutableStateOf(photo.annotation) }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    // Auto-scroll to bottom when keyboard opens or text grows
    LaunchedEffect(annotation) {
        if (scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .verticalScroll(scrollState)
                ) {
                    // Top Controls
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                        Text(
                            "Photo Detail",
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 48.dp)
                        )
                    }

                    // Photo Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = this@BoxWithConstraints.maxHeight * 0.65f), // Ensure notes section "peeks" on tall images
                        contentAlignment = Alignment.TopCenter
                    ) {
                        AsyncImage(
                            model = photo.imageUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    }

                    // Bottom Annotation Area
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1C)),
                        shape = MaterialTheme.shapes.large,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Captured: ${photo.timestampOverlay}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            TextField(
                                value = annotation,
                                onValueChange = {
                                    annotation = it
                                    onUpdateAnnotation(it)
                                },
                                label = { Text("Site Observations", color = Color.White.copy(alpha = 0.7f)) },
                                placeholder = { Text("Add site notes here...", color = Color.White.copy(alpha = 0.4f)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .onFocusEvent { focusState ->
                                        if (focusState.isFocused) {
                                            coroutineScope.launch {
                                                bringIntoViewRequester.bringIntoView()
                                            }
                                        }
                                    },
                                minLines = 3,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                colors = TextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                                    cursorColor = MaterialTheme.colorScheme.primary,
                                    focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.3f),
                                    focusedLabelColor = MaterialTheme.colorScheme.primary,
                                    unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                                )
                            )
                        }
                    }
                    // Add extra padding at the bottom to ensure the card isn't cut off by the keyboard
                    Spacer(modifier = Modifier.height(24.dp).navigationBarsPadding())
                }
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
