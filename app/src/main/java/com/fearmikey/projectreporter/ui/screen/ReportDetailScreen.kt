package com.fearmikey.projectreporter.ui.screen

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.entity.PhotoEntity
import com.fearmikey.projectreporter.ui.component.CameraPreview
import com.fearmikey.projectreporter.ui.viewmodel.ReportViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val project by viewModel.project.collectAsState()
    val photos by viewModel.photos.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.pdfExportResult.collect { result ->
            val message = if (result.isSuccess) "PDF exported to Downloads" else "Export failed: ${result.exceptionOrNull()?.message}"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(projectId) {
        viewModel.setProjectId(projectId)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.addPhoto(it.toString()) } }
    )

    var showCamera by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(project?.projectName ?: "Loading...", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.exportPdf() }) {
                        Icon(Icons.Default.Share, contentDescription = "Export PDF")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { showCamera = true }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Camera")
                            Text("Camera", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    IconButton(onClick = {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "Gallery")
                            Text("Gallery", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (showCamera) {
            CameraScreen(
                onPhotoCaptured = { uri ->
                    viewModel.addPhoto(uri.toString())
                    showCamera = false
                },
                onClose = { showCamera = false }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(photos, key = { it.photoId }) { photo ->
                    PhotoNoteCard(
                        photo = photo,
                        onAnnotationChange = { viewModel.updateAnnotation(photo, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoNoteCard(photo: PhotoEntity, onAnnotationChange: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            AsyncImage(
                model = photo.imageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Captured: ${photo.timestampOverlay}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = photo.annotation,
                    onValueChange = onAnnotationChange,
                    label = { Text("Observation Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text("Add site notes here...") }
                )
            }
        }
    }
}

@Composable
fun CameraScreen(onPhotoCaptured: (Uri) -> Unit, onClose: () -> Unit) {
    CameraPreview(onPhotoCaptured = onPhotoCaptured, onClose = onClose)
}
