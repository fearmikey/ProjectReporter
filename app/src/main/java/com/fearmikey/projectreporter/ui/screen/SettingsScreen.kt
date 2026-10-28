package com.fearmikey.projectreporter.ui.screen

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.repository.AppTheme
import com.fearmikey.projectreporter.data.repository.ColorSchemeOption
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.ui.theme.previewColorScheme
import com.fearmikey.projectreporter.ui.viewmodel.BackupUiState
import com.fearmikey.projectreporter.ui.viewmodel.SettingsViewModel
import com.fearmikey.projectreporter.util.RoleLabels
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onReopenOnboarding: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeSettings by viewModel.themeSettings.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val backupUiState by viewModel.backupUiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var showThemeDialog by remember { mutableStateOf(false) }
    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showFlashModeDialog by remember { mutableStateOf(false) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var showCloudBackupInfoDialog by remember { mutableStateOf(false) }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()

                if (bitmap != null) {
                    val palette = Palette.from(bitmap).generate()
                    val primaryColor = palette.getVibrantColor(
                        palette.getDominantColor(palette.getMutedColor(0xFF1E88E5.toInt()))
                    )
                    val secondaryColor = palette.getLightVibrantColor(
                        palette.getDarkVibrantColor(palette.getLightMutedColor(primaryColor))
                    )

                    val logoFile = File(context.filesDir, "company_logo.png")
                    val outStream = FileOutputStream(logoFile)
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, outStream)
                    outStream.close()

                    val savedUri = Uri.fromFile(logoFile).toString()
                    viewModel.updateCompanyLogo(savedUri, primaryColor, secondaryColor)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.exportBackup(uri)
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importBackup(uri)
        }
    }

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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Backup & Restore Section
                Text(
                    text = "Backup & Restore",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Export Data Backup") },
                    supportingContent = { Text("Save full backup archive (.zip) with projects, photos, notes, & settings") },
                    leadingContent = { Icon(Icons.Default.Backup, contentDescription = null) },
                    modifier = Modifier.clickable {
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                        exportBackupLauncher.launch("ProjectReporter_Backup_$timestamp.zip")
                    }
                )
                ListItem(
                    headlineContent = { Text("Restore Data Backup") },
                    supportingContent = { Text("Import projects, notes, photos, and settings from a backup file") },
                    leadingContent = { Icon(Icons.Default.Restore, contentDescription = null) },
                    modifier = Modifier.clickable { showConfirmRestoreDialog = true }
                )
                ListItem(
                    headlineContent = { Text("External & Cloud Backup Options") },
                    supportingContent = { Text("Google Drive, Seedvault, Nextcloud, and SD Card external backup guide") },
                    leadingContent = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                    modifier = Modifier.clickable { showCloudBackupInfoDialog = true }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

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
                val schemeName = themeSettings.colorSchemeOption.name.lowercase().replaceFirstChar { it.uppercase() }
                val dynamicActive = themeSettings.useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ListItem(
                    headlineContent = { Text("Color Scheme") },
                    supportingContent = {
                        Text(
                            when {
                                themeSettings.logoPrimaryColor != null -> "$schemeName (accents from company logo)"
                                dynamicActive -> "$schemeName (overridden by dynamic color)"
                                else -> schemeName
                            }
                        )
                    },
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
                    headlineContent = { Text("High Contrast") },
                    supportingContent = { Text("Stronger text and accent contrast (WCAG AAA) for bright sunlight or low vision") },
                    leadingContent = { Icon(Icons.Default.Contrast, contentDescription = null) },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.highContrast,
                            onCheckedChange = { viewModel.setHighContrast(it) }
                        )
                    }
                )

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

                ListItem(
                    headlineContent = { Text("Company Logo & Colors") },
                    supportingContent = {
                        Text(if (themeSettings.companyLogoUri != null) "Logo set (Custom theme extracted)" else "Upload logo to auto-theme the app")
                    },
                    leadingContent = {
                        if (themeSettings.companyLogoUri != null) {
                            AsyncImage(
                                model = themeSettings.companyLogoUri,
                                contentDescription = "Company Logo",
                                modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Icon(Icons.Default.Business, contentDescription = null)
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { logoPickerLauncher.launch("image/*") }) {
                                Icon(Icons.Default.Upload, contentDescription = "Upload Logo")
                            }
                            if (themeSettings.companyLogoUri != null) {
                                IconButton(onClick = { viewModel.updateCompanyLogo(null, null, null) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove Logo", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Photo Watermarks Section
                Text(
                    text = "Photo Watermarks",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                ListItem(
                    headlineContent = { Text("Timestamp Watermark") },
                    supportingContent = { Text("Burn date and time onto captured photos") },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.watermarkTimestamp,
                            onCheckedChange = { viewModel.setWatermarkTimestamp(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("GPS Location Watermark") },
                    supportingContent = { Text("Burn latitude & longitude coordinates onto photos") },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.watermarkGps,
                            onCheckedChange = { viewModel.setWatermarkGps(it) }
                        )
                    }
                )
                ListItem(
                    headlineContent = { Text("Project Details Watermark") },
                    supportingContent = { Text("Burn project name onto captured photos") },
                    trailingContent = {
                        Switch(
                            checked = themeSettings.watermarkProjectDetails,
                            onCheckedChange = { viewModel.setWatermarkProjectDetails(it) }
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
                ListItem(
                    headlineContent = { Text("Re-open Onboarding") },
                    supportingContent = { Text("View introduction guide and update setup details") },
                    leadingContent = { Icon(Icons.Default.School, contentDescription = null) },
                    modifier = Modifier.clickable { onReopenOnboarding() }
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

        if (showConfirmRestoreDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmRestoreDialog = false },
                title = { Text("Restore Backup?") },
                text = {
                    Text("Restoring a backup will import all projects, photos, notes, your profile, and preferences from the selected backup file. Existing data will be preserved and merged.")
                },
                confirmButton = {
                    Button(onClick = {
                        showConfirmRestoreDialog = false
                        importBackupLauncher.launch(
                            arrayOf("application/zip", "application/x-zip-compressed", "application/json", "*/*")
                        )
                    }) {
                        Text("Select Backup File")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestoreDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showCloudBackupInfoDialog) {
            AlertDialog(
                onDismissRequest = { showCloudBackupInfoDialog = false },
                title = { Text("External Backup Options") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "1. Full Archive Export (.zip)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Use 'Export Data Backup' to save a complete archive containing all projects, full-resolution photos, notes, engineer profile, and settings. You can save this file directly to Nextcloud, Google Drive, Synology, Proton Drive, SD Card, or external USB.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "2. Android OS Cloud & Seedvault Backup",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "ProjectReporter automatically integrates with Android's built-in Auto Backup & Seedvault frameworks. Enable 'Backup' in your Android System Settings to automatically back up your app database and settings.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCloudBackupInfoDialog = false }) {
                        Text("Got it")
                    }
                }
            )
        }

        when (val state = backupUiState) {
            is BackupUiState.Processing -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Processing Backup") },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()
                            Text("Please wait while processing backup data...")
                        }
                    },
                    confirmButton = {}
                )
            }
            is BackupUiState.ExportSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetBackupUiState() },
                    title = { Text("Backup Exported") },
                    text = {
                        Text("Successfully exported backup archive containing ${state.count} project(s) along with all photos, notes, profile details, and app settings.")
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.resetBackupUiState() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupUiState.ImportSuccess -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetBackupUiState() },
                    title = { Text("Backup Restored") },
                    text = {
                        Text("Successfully restored ${state.result.projectsCount} project(s), ${state.result.photosCount} photo(s), and ${state.result.notesCount} note(s).")
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.resetBackupUiState() }) {
                            Text("OK")
                        }
                    }
                )
            }
            is BackupUiState.Error -> {
                AlertDialog(
                    onDismissRequest = { viewModel.resetBackupUiState() },
                    title = { Text("Backup Error") },
                    text = {
                        Text(state.message)
                    },
                    confirmButton = {
                        Button(onClick = { viewModel.resetBackupUiState() }) {
                            Text("OK")
                        }
                    }
                )
            }
            BackupUiState.Idle -> {}
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
                highContrast = themeSettings.highContrast,
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
                currentPhone = profile?.engineerPhone ?: "",
                currentCompany = profile?.company ?: "",
                currentRole = profile?.role ?: "",
                onDismiss = { showProfileDialog = false },
                onConfirm = { name, phone, company, role ->
                    viewModel.updateProfile(name, phone, company, role)
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
    highContrast: Boolean = false,
    onDismiss: () -> Unit,
    onOptionSelected: (ColorSchemeOption) -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
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
                        Text(
                            option.name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.weight(1f)
                        )
                        ColorSchemeSwatch(previewColorScheme(option, isDark, highContrast))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Small preview chip showing a scheme's surface with its primary, secondary and tertiary accents. */
@Composable
private fun ColorSchemeSwatch(scheme: ColorScheme) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(scheme.surfaceContainerHigh)
            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { color ->
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
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
    currentPhone: String = "",
    currentCompany: String,
    currentRole: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    var phone by remember { mutableStateOf(currentPhone) }
    var company by remember { mutableStateOf(currentCompany) }
    var role by remember { mutableStateOf(currentRole) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = role,
                    onValueChange = { role = it },
                    label = { Text("Role / Title") },
                    placeholder = { Text("e.g. Inspector, Technician") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(RoleLabels.yourNameLabel(role)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(RoleLabels.phoneLabel(role)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = company,
                    onValueChange = { company = it },
                    label = { Text("Company") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), phone.trim(), company.trim(), role.trim()) },
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
