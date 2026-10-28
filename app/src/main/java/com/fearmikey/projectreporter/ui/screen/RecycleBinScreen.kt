package com.fearmikey.projectreporter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.ui.component.rememberAppHaptics
import com.fearmikey.projectreporter.ui.viewmodel.DeletedItem
import com.fearmikey.projectreporter.ui.viewmodel.RecycleBinViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val deletedItems by viewModel.deletedItems.collectAsStateWithLifecycle()
    val projectName by viewModel.projectName.collectAsStateWithLifecycle()
    val isProjectBin = viewModel.projectId != null

    var showEmptyBinDialog by remember { mutableStateOf(false) }
    var itemToDeleteForever by remember { mutableStateOf<DeletedItem?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptics = rememberAppHaptics()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Recycle Bin")
                        if (isProjectBin) {
                            Text(
                                text = projectName ?: "",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (deletedItems.isNotEmpty()) {
                        IconButton(onClick = {
                            val count = deletedItems.size
                            viewModel.restoreAll()
                            haptics.restored()
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (count == 1) "1 item restored" else "$count items restored"
                                )
                            }
                        }) {
                            Icon(Icons.Default.RestorePage, contentDescription = "Restore All")
                        }
                        IconButton(onClick = { showEmptyBinDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Empty Bin")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (deletedItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Bin is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isProjectBin) {
                            "Photos and notes deleted from this report will appear here."
                        } else {
                            "Reports deleted from your dashboard will appear here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Items are permanently deleted after 30 days.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                items(deletedItems, key = { it.id }) { item ->
                    DeletedItemCard(
                        item = item,
                        onRestore = {
                            viewModel.restoreItem(item)
                            haptics.restored()
                            scope.launch { snackbarHostState.showSnackbar("${item.label} restored") }
                        },
                        onDeletePermanently = { itemToDeleteForever = item }
                    )
                }
            }
        }

        if (showEmptyBinDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyBinDialog = false },
                icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                title = { Text(if (isProjectBin) "Empty this project's bin?" else "Empty Recycle Bin?") },
                text = {
                    Text(
                        if (isProjectBin) {
                            "This will permanently delete all ${deletedItems.size} deleted items from this report. This can't be undone."
                        } else {
                            "This will permanently delete all reports currently in the bin. This can't be undone."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.emptyBin()
                            haptics.deletedPermanently()
                            showEmptyBinDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyBinDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        itemToDeleteForever?.let { item ->
            AlertDialog(
                onDismissRequest = { itemToDeleteForever = null },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                title = { Text("Delete permanently?") },
                text = {
                    Text(
                        if (item is DeletedItem.Project) {
                            "\"${item.project.projectName}\" and all its photos and notes will be permanently deleted. This can't be undone."
                        } else {
                            "This ${item.label.lowercase()} will be permanently deleted. This can't be undone."
                        }
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.deletePermanently(item)
                            haptics.deletedPermanently()
                            itemToDeleteForever = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { itemToDeleteForever = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private val DeletedItem.label: String
    get() = when (this) {
        is DeletedItem.Photo -> "Photo"
        is DeletedItem.Note -> "Note"
        is DeletedItem.Project -> "Project"
    }

@Composable
fun DeletedItemCard(
    item: DeletedItem,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Leading preview
            val previewModifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
            when (item) {
                is DeletedItem.Photo -> AsyncImage(
                    model = item.photo.imageUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = previewModifier
                )
                is DeletedItem.Note -> Box(
                    modifier = previewModifier.background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Notes,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                is DeletedItem.Project -> Box(
                    modifier = previewModifier.background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val title = when (item) {
                    is DeletedItem.Photo ->
                        item.photo.annotation.lineSequence().firstOrNull { it.isNotBlank() }?.trim()
                            ?: "Photo · ${item.photo.timestampOverlay}"
                    is DeletedItem.Note ->
                        item.note.content.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Empty note"
                    is DeletedItem.Project -> item.project.projectName
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = when (item) {
                    is DeletedItem.Project ->
                        listOfNotNull("Report", item.project.displayProjectNumber?.let { "ID $it" }, item.project.clientCompany.takeIf { it.isNotBlank() })
                            .joinToString(" · ")
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val daysPassed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - item.deletedTimestamp)
                val daysLeft = (30 - daysPassed).coerceAtLeast(0)
                Text(
                    text = if (daysLeft == 1L) "Permanently deleting in 1 day" else "Permanently deleting in $daysLeft days",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysLeft < 5) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDeletePermanently) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Permanently", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
