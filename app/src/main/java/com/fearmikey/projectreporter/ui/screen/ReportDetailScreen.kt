package com.fearmikey.projectreporter.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.entity.NoteEntity
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.ui.component.CameraPreview
import com.fearmikey.projectreporter.ui.viewmodel.ReportViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    viewModel: ReportViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val context = LocalContext.current

    val reportItems = remember(photos, notes) {
        (photos.map { ReportItem.Photo(it) } + notes.map { ReportItem.Note(it) })
            .sortedByDescending { it.timestamp }
    }

    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedIds.isNotEmpty()

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
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var photoToView by remember { mutableStateOf<PhotoEntity?>(null) }
    val gridState = rememberLazyGridState()

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
                        IconButton(onClick = { 
                            // Enter selection mode manually
                            if (reportItems.isNotEmpty()) {
                                selectedIds = setOf(reportItems.first().id)
                            }
                        }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select")
                        }
                        IconButton(onClick = { viewModel.exportPdf() }) {
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
            if (!showCamera) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = { showCamera = true },
                        icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                        text = { Text("Take Photo") },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.width(160.dp)
                    )
                    ExtendedFloatingActionButton(
                        onClick = { showNoteDialog = true },
                        icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
                        text = { Text("Take Note") },
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                        modifier = Modifier.width(160.dp)
                    )
                }
            }
        }
    ) { padding ->
        if (showCamera) {
            Dialog(
                onDismissRequest = { showCamera = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CameraScreen(
                        onPhotoCaptured = { uri ->
                            viewModel.addPhoto(uri.toString())
                            showCamera = false
                        },
                        onClose = { showCamera = false }
                    )
                }
            }
        } else {
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
    }
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
    var content by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialContent.isEmpty()) "Add Note" else "Edit Note") },
        text = {
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("Note content") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp)
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(content) }) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoDetailDialog(
    photo: PhotoEntity,
    onDismiss: () -> Unit,
    onUpdateAnnotation: (String) -> Unit
) {
    var annotation by remember { mutableStateOf(photo.annotation) }

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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding() // Automatically shrinks the column when keyboard appears
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

                // Photo Area (Flexible weight)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = photo.imageUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }

                // Bottom Annotation Area
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .navigationBarsPadding(),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.7f)),
                    shape = MaterialTheme.shapes.large,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Captured: ${photo.timestampOverlay}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = annotation,
                            onValueChange = {
                                annotation = it
                                onUpdateAnnotation(it)
                            },
                            label = { Text("Site Observations", color = Color.White) },
                            placeholder = { Text("Add site notes here...", color = Color.White.copy(alpha = 0.4f)) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = Color.White.copy(alpha = 0.6f)
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CameraScreen(onPhotoCaptured: (Uri) -> Unit, onClose: () -> Unit) {
    CameraPreview(onPhotoCaptured = onPhotoCaptured, onClose = onClose)
}
