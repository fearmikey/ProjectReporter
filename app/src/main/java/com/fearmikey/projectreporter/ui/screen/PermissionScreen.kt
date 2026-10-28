package com.fearmikey.projectreporter.ui.screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Runtime permissions requested during onboarding, in order. */
private enum class PermissionStep {
    CAMERA,
    LOCATION,
    NOTIFICATIONS;

    val permissions: Array<String>
        get() = when (this) {
            CAMERA -> arrayOf(Manifest.permission.CAMERA)
            LOCATION -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                emptyArray() // Granted implicitly before Android 13
            }
        }

    /** A step is done if every permission is granted (or not applicable on this OS version). */
    fun isSatisfied(context: Context): Boolean = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

/** Returns the first step at or after [from] that still needs user action, or null if all are done. */
private fun nextPendingStep(context: Context, from: Int): PermissionStep? =
    PermissionStep.entries.drop(from).firstOrNull { !it.isSatisfied(context) }

@Composable
fun PermissionScreen(
    onPermissionsGranted: () -> Unit
) {
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf(nextPendingStep(context, 0)) }

    fun advance() {
        val step = currentStep ?: return
        currentStep = nextPendingStep(context, step.ordinal + 1)
        if (currentStep == null) onPermissionsGranted()
    }

    // Everything already granted (e.g. returning user) – nothing to show.
    LaunchedEffect(Unit) {
        if (currentStep == null) onPermissionsGranted()
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Camera is required to use the app; stay on this step until it is granted.
        val cameraDenied = currentStep == PermissionStep.CAMERA && results.values.any { !it }
        if (!cameraDenied) advance()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (currentStep) {
            PermissionStep.CAMERA -> PermissionStepContent(
                icon = Icons.Default.CameraAlt,
                title = "Camera Access Required",
                description = "ProjectReporter needs camera access to capture site photos for your reports. Without this permission, you won't be able to document visual evidence.",
                buttonText = "Grant Camera Permission",
                onGrant = { launcher.launch(PermissionStep.CAMERA.permissions) },
                onSkip = null
            )
            PermissionStep.LOCATION -> PermissionStepContent(
                icon = Icons.Default.LocationOn,
                title = "Location Access (Optional)",
                description = "Location access is used to automatically stamp GPS coordinates onto your photos. It is highly recommended for comprehensive site reporting.",
                buttonText = "Grant Location Permission",
                onGrant = { launcher.launch(PermissionStep.LOCATION.permissions) },
                onSkip = ::advance
            )
            PermissionStep.NOTIFICATIONS -> PermissionStepContent(
                icon = Icons.Default.Notifications,
                title = "Notifications (Optional)",
                description = "Get notified when a PDF or Word report finishes exporting, with shortcuts to open the file or its folder.",
                buttonText = "Allow Notifications",
                onGrant = { launcher.launch(PermissionStep.NOTIFICATIONS.permissions) },
                onSkip = ::advance
            )
            null -> Unit
        }
    }
}

@Composable
private fun PermissionStepContent(
    icon: ImageVector,
    title: String,
    description: String,
    buttonText: String,
    onGrant: () -> Unit,
    onSkip: (() -> Unit)?
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(100.dp),
        tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(32.dp))
    Button(onClick = onGrant, modifier = Modifier.fillMaxWidth()) {
        Text(buttonText)
    }
    if (onSkip != null) {
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Skip for now")
        }
    }
}
