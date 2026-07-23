package com.fearmikey.projectreporter.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fearmikey.projectreporter.ui.viewmodel.DeletedItem
import com.fearmikey.projectreporter.ui.viewmodel.RecycleBinViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val deletedItems by viewModel.deletedItems.collectAsState()
    var showEmptyBinDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recycle Bin") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (deletedItems.isNotEmpty()) {
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
                        onRestore = { viewModel.restoreItem(item) },
                        onDeletePermanently = { viewModel.deletePermanently(item) }
                    )
                }
            }
        }

        if (showEmptyBinDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyBinDialog = false },
                title = { Text("Empty Recycle Bin?") },
                text = { Text("This will permanently delete all items currently in the bin.") },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.emptyBin()
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
    }
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
            Column(modifier = Modifier.weight(1f)) {
                val title = when (item) {
                    is DeletedItem.Photo -> "Photo"
                    is DeletedItem.Note -> "Note: ${item.note.content.take(20)}..."
                    is DeletedItem.Project -> "Project: ${item.project.projectName}"
                }
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                
                val daysPassed = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - item.deletedTimestamp)
                val daysLeft = 30 - daysPassed
                Text(
                    text = "Permanently deleting in $daysLeft days",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (daysLeft < 5) Color.Red else MaterialTheme.colorScheme.secondary
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
