package com.fearmikey.projectreporter.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import android.os.Build

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeSettings by viewModel.themeSettings.collectAsState()
    val profile by viewModel.profile.collectAsState()
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showFlashModeDialog by remember { mutableStateOf(false) }
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
                // Appearance Section
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("App Theme") },
                    supportingContent = { Text(themeSettings.appTheme.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.Palette, contentDescription = null) },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
                ListItem(
                    headlineContent = { Text("Color Scheme") },
                    supportingContent = { Text(themeSettings.colorSchemeOption.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.ColorLens, contentDescription = null) },
                    modifier = Modifier.clickable { showColorSchemeDialog = true }
                )
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    ListItem(
                        headlineContent = { Text("Use Dynamic Color") },
                        supportingContent = { Text("Use colors from your system wallpaper") },
                        trailingContent = {
                            Switch(
                                checked = themeSettings.useDynamicColor,
                                onCheckedChange = { viewModel.setDynamicColor(it) }
                            )
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text("AMOLED Mode") },
                    supportingContent = { Text("Pure black background in dark mode") },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.amoledMode,
                            onCheckedChange = { viewModel.setAmoledMode(it) }
                        )
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // General Section
                Text(
                    text = "General",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Default Flash Mode") },
                    supportingContent = { Text(themeSettings.defaultFlashMode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(Icons.Default.FlashOn, contentDescription = null) },
                    modifier = Modifier.clickable { showFlashModeDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                currentTheme = themeSettings.appTheme,
                onDismiss = { showThemeDialog = false },
                onThemeSelected = {
                    viewModel.setTheme(it)
                    showThemeDialog = false
                }
            )
        }

        if (showColorSchemeDialog) {
            ColorSchemeSelectionDialog(
                currentOption = themeSettings.colorSchemeOption,
                onDismiss = { showColorSchemeDialog = false },
                onOptionSelected = {
                    viewModel.setColorScheme(it)
                    showColorSchemeDialog = false
                }
            )
        }

        if (showFlashModeDialog) {
            FlashModeSelectionDialog(
                currentOption = themeSettings.defaultFlashMode,
                onDismiss = { showFlashModeDialog = false },
                onOptionSelected = {
                    viewModel.setDefaultFlashMode(it)
                    showFlashModeDialog = false
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
fun ColorSchemeSelectionDialog(
    currentOption: ColorSchemeOption,
    onDismiss: () -> Unit,
    onOptionSelected: (ColorSchemeOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Color Scheme") },
        text = {
            Column {
                ColorSchemeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentOption == option,
                            onClick = { onOptionSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
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
fun FlashModeSelectionDialog(
    currentOption: FlashModeOption,
    onDismiss: () -> Unit,
    onOptionSelected: (FlashModeOption) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Default Flash Mode") },
        text = {
            Column {
                FlashModeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOptionSelected(option) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentOption == option,
                            onClick = { onOptionSelected(option) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(option.name.lowercase().replaceFirstChar { it.uppercase() })
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
