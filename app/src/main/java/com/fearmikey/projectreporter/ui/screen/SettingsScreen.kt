package com.fearmikey.projectreporter.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val currentTheme by viewModel.appTheme.collectAsState()
    val profile by viewModel.profile.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ListItem(
                    headlineContent = { Text("App Theme") },
                    supportingContent = { Text(currentTheme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
                HorizontalDivider()
                ListItem(
                    headlineContent = { Text("Engineer Profile") },
                    supportingContent = { Text(profile?.engineerName ?: "Not set") },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.clickable { showProfileDialog = true }
                )
            }

            // App Metadata Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ProjectReporter",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Version ${viewModel.appVersion}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "GitHub Repository",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/fearmikey")
                    }
                )
            }
        }

        if (showThemeDialog) {
            ThemeSelectionDialog(
                currentTheme = currentTheme,
                onDismiss = { showThemeDialog = false },
                onThemeSelected = {
                    viewModel.setTheme(it)
                    showThemeDialog = false
                }
            )
        }

        if (showProfileDialog) {
            EditProfileDialog(
                currentName = profile?.engineerName ?: "",
                onDismiss = { showProfileDialog = false },
                onConfirm = {
                    viewModel.updateProfile(it)
                    showProfileDialog = false
                }
            )
        }
    }
}

@Composable
fun ThemeSelectionDialog(
    currentTheme: AppTheme,
    onDismiss: () -> Unit,
    onThemeSelected: (AppTheme) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Theme") },
        text = {
            Column {
                AppTheme.entries.forEach { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(theme) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentTheme == theme,
                            onClick = { onThemeSelected(theme) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun EditProfileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Engineer Name") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
