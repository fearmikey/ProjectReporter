package com.fearmikey.projectreporter.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.entity.ProjectEntity
import com.fearmikey.projectreporter.ui.component.EditProjectDialog
import com.fearmikey.projectreporter.ui.viewmodel.MainViewModel
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardScreen(
    onProjectClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onRecycleBinClick: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val themeSettings by settingsViewModel.themeSettings.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var projectToEdit by remember { mutableStateOf<ProjectEntity?>(null) }

    var selectedProjectIds by remember { mutableStateOf(setOf<String>()) }
    val isSelectionMode = selectedProjectIds.isNotEmpty()

    BackHandler(enabled = isSelectionMode) {
        selectedProjectIds = emptySet()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedProjectIds.size} selected")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (themeSettings.companyLogoUri != null) {
                                AsyncImage(
                                    model = themeSettings.companyLogoUri,
                                    contentDescription = "Company Logo",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(MaterialTheme.shapes.small),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            Text("Site Service Reports", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = { selectedProjectIds = emptySet() }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear selection")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        if (selectedProjectIds.size == 1) {
                            IconButton(onClick = {
                                projectToEdit = projects.find { it.projectId == selectedProjectIds.first() }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                        }
                        IconButton(onClick = {
                            selectedProjectIds.forEach { id ->
                                projects.find { it.projectId == id }?.let { viewModel.softDeleteProject(it) }
                            }
                            selectedProjectIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    } else {
                        IconButton(onClick = {
                            if (projects.isNotEmpty()) {
                                selectedProjectIds = setOf(projects.first().projectId)
                            }
                        }) {
                            Icon(Icons.Default.Checklist, contentDescription = "Select")
                        }
                        IconButton(onClick = onRecycleBinClick) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Recycle Bin")
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(28.dp)) },
                text = { Text("New Report", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            )
        }
    ) { padding ->
        if (projects.isEmpty()) {
            EmptyDashboard(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onCreateClick = { showDialog = true }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(projects, key = { it.projectId }) { project ->
                    val isSelected = selectedProjectIds.contains(project.projectId)
                    ProjectCard(
                        project = project,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                selectedProjectIds = if (isSelected) selectedProjectIds - project.projectId else selectedProjectIds + project.projectId
                            } else {
                                onProjectClick(project.projectId)
                            }
                        },
                        onLongClick = {
                            selectedProjectIds = selectedProjectIds + project.projectId
                        },
                        onEditClick = {
                            projectToEdit = project
                        }
                    )
                }
            }
        }

        if (showDialog) {
            CreateProjectDialog(
                initialEngineerName = profile?.engineerName ?: "",
                onDismiss = { showDialog = false },
                onCheckExists = { checkId, callback -> viewModel.checkProjectExists(checkId, callback) },
                onConfirm = { id, name, engineer ->
                    viewModel.createProject(id, name, engineer)
                    showDialog = false
                    onProjectClick(id)
                }
            )
        }

        projectToEdit?.let { project ->
            EditProjectDialog(
                project = project,
                onDismiss = { projectToEdit = null },
                onConfirm = { oldId, newId, newName, newEngineer, onResult ->
                    viewModel.updateProject(oldId, newId, newName, newEngineer) { success ->
                        if (success) {
                            if (selectedProjectIds.contains(oldId)) {
                                selectedProjectIds = (selectedProjectIds - oldId) + newId
                            }
                            projectToEdit = null
                        }
                        onResult(success)
                    }
                }
            )
        }
    }
}

@Composable
fun EmptyDashboard(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Assignment,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Reports Yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "It looks like you haven't documented any site visits. Create your first report to get started.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onCreateClick,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Create First Report")
        }
    }
}

@Composable
fun ProjectCard(
    project: ProjectEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 8.dp else 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = project.projectName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (!isSelected) {
                        IconButton(
                            onClick = onEditClick,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Project",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                project.displayProjectNumber?.let { projectNumber ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ID: $projectNumber",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Eng: ${project.engineerName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(project.timestamp))
                    Text(
                        text = dateStr,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
fun CreateProjectDialog(
    initialEngineerName: String,
    onDismiss: () -> Unit,
    onCheckExists: (String, (Boolean) -> Unit) -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var id by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var engineer by remember { mutableStateOf(initialEngineerName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Site Report") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = id,
                    onValueChange = {
                        id = it
                        errorMessage = null
                    },
                    label = { Text("Project Number (Optional)") },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = engineer,
                    onValueChange = { engineer = it },
                    label = { Text("Engineer Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmedId = id.trim()
                    val trimmedName = name.trim()
                    val trimmedEngineer = engineer.trim()
                    if (trimmedName.isNotBlank()) {
                        if (trimmedId.isNotBlank()) {
                            onCheckExists(trimmedId) { exists ->
                                if (exists) {
                                    errorMessage = "Project Number already exists"
                                } else {
                                    onConfirm(trimmedId, trimmedName, trimmedEngineer)
                                }
                            }
                        } else {
                            val autoId = "proj_${UUID.randomUUID()}"
                            onConfirm(autoId, trimmedName, trimmedEngineer)
                        }
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
