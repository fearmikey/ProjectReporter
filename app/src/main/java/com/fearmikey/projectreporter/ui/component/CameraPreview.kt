package com.fearmikey.projectreporter.ui.component

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.MediaActionSound
import android.net.Uri
import android.os.CancellationSignal
import android.os.SystemClock
import android.util.Log
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.OrientationEventListener
import androidx.activity.compose.BackHandler
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.BurstMode
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import com.fearmikey.projectreporter.util.PhotoWatermark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "CameraPreview"

/** Photos are capped near 12 MP: plenty for reports, and safe to watermark in memory. */
private val TARGET_CAPTURE_SIZE = Size(4032, 3024)

/** Cached location fixes older than this are ignored. */
private val MAX_LOCATION_AGE_NANOS = TimeUnit.MINUTES.toNanos(5)

private enum class DiscardAction { RETAKE, CLOSE }

/**
 * Full-screen camera with tap-to-focus, zoom presets, flash, a fast-capture (burst) mode and,
 * in normal mode, a review step for adding a note before the photo is saved.
 *
 * [onPhotoCaptured] only saves a photo; the camera stays open in fast-capture mode and calls
 * [onClose] itself after a reviewed photo is confirmed.
 */
@Composable
fun CameraPreview(
    onPhotoCaptured: (Uri, String) -> Unit,
    onClose: () -> Unit,
    defaultFlashMode: FlashModeOption,
    watermarkTimestamp: Boolean = true,
    watermarkGps: Boolean = false,
    watermarkProjectDetails: Boolean = true,
    projectName: String = ""
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiScope = rememberCoroutineScope()
    // Deliberately not cancelled on dispose, so photos already being processed still get saved
    // if the camera is closed mid-capture. Work is short-lived and the scope holds no resources.
    val processingScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnPhotoCaptured by rememberUpdatedState(onPhotoCaptured)

    var capturedPhotoUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isBurstMode by rememberSaveable { mutableStateOf(false) }
    var burstCount by rememberSaveable { mutableIntStateOf(0) }
    var lastBurstUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var pendingDiscard by rememberSaveable { mutableStateOf<DiscardAction?>(null) }
    var isShutterBusy by remember { mutableStateOf(false) }
    var processingCount by remember { mutableIntStateOf(0) }
    val shutterFlash = remember { Animatable(0f) }
    val isReviewing = capturedPhotoUri != null

    // Fresh note draft for every captured photo; discarded on retake.
    val noteEditor: NoteEditorState? = capturedPhotoUri?.let { uri ->
        key(uri) { rememberNoteEditorState("") }
    }

    val currentLocation = rememberCurrentLocation(enabled = watermarkGps)

    // --- Camera setup -------------------------------------------------------------------------

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            // Show the whole 4:3 frame so the preview matches what ends up in the photo.
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            TARGET_CAPTURE_SIZE,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                        )
                    )
                    .build()
            )
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var disposed = false
        var provider: ProcessCameraProvider? = null
        var preview: Preview? = null

        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                val newPreview = Preview.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .build()
                    )
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    newPreview,
                    imageCapture
                )
                provider = cameraProvider
                preview = newPreview
            } catch (e: Exception) {
                Log.e(TAG, "Use case binding failed", e)
                uiScope.launch { snackbarHostState.showSnackbar("Camera unavailable. Close and try again.") }
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            preview?.let { provider?.unbind(it, imageCapture) }
            camera = null
        }
    }

    // Keep photo orientation correct even when the screen itself doesn't rotate
    // (auto-rotate off or a portrait-locked activity).
    DisposableEffect(imageCapture) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                imageCapture.targetRotation = when (orientation) {
                    in 45 until 135 -> android.view.Surface.ROTATION_270
                    in 135 until 225 -> android.view.Surface.ROTATION_180
                    in 225 until 315 -> android.view.Surface.ROTATION_90
                    else -> android.view.Surface.ROTATION_0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    var flashMode by remember {
        mutableIntStateOf(
            when (defaultFlashMode) {
                FlashModeOption.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
                FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
            }
        )
    }
    LaunchedEffect(flashMode) { imageCapture.flashMode = flashMode }

    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }

    // Delete an un-confirmed photo if the camera goes away for good (not on rotation).
    val isDisposed = remember { AtomicBoolean(false) }
    val latestCapturedUri by rememberUpdatedState(capturedPhotoUri)
    DisposableEffect(Unit) {
        onDispose {
            isDisposed.set(true)
            shutterSound.release()
            if (context.findActivity()?.isChangingConfigurations != true) {
                latestCapturedUri?.let { deletePhotoFile(it, processingScope) }
            }
        }
    }

    // --- Actions --------------------------------------------------------------------------------

    fun showMessage(message: String) {
        uiScope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun capture() {
        if (isShutterBusy || camera == null) return
        if (!isBurstMode && processingCount > 0) return
        isShutterBusy = true

        shutterSound.play(MediaActionSound.SHUTTER_CLICK)
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        uiScope.launch {
            shutterFlash.snapTo(0.7f)
            shutterFlash.animateTo(0f, tween(durationMillis = 250))
        }

        val burst = isBurstMode
        val location = currentLocation
        val watermarkLines = PhotoWatermark.buildLines(
            includeTimestamp = watermarkTimestamp,
            projectName = projectName.takeIf { watermarkProjectDetails },
            includeGps = watermarkGps,
            location = location
        )
        val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
        val file = File(outputDir, "${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file)
            .setMetadata(ImageCapture.Metadata().apply { if (watermarkGps) this.location = location })
            .build()

        imageCapture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isShutterBusy = false
                    processingCount++
                    processingScope.launch {
                        val watermarked = withContext(Dispatchers.IO) {
                            runCatching { PhotoWatermark.apply(file, watermarkLines) }
                                .onFailure { Log.e(TAG, "Watermark failed; keeping original photo", it) }
                                .isSuccess
                        }
                        processingCount--
                        if (!watermarked) showMessage("Watermark couldn't be added to this photo")

                        val uri = Uri.fromFile(file)
                        when {
                            burst -> {
                                currentOnPhotoCaptured(uri, "")
                                burstCount++
                                lastBurstUri = uri
                            }
                            isDisposed.get() -> deletePhotoFile(uri, processingScope)
                            else -> capturedPhotoUri = uri
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    isShutterBusy = false
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    file.delete()
                    showMessage("Couldn't take the photo. Please try again.")
                }
            }
        )
    }

    fun discardCapturedPhoto() {
        capturedPhotoUri?.let { deletePhotoFile(it, processingScope) }
        capturedPhotoUri = null
    }

    fun requestDiscard(action: DiscardAction) {
        if (noteEditor?.text?.isNotBlank() == true) {
            pendingDiscard = action
        } else {
            discardCapturedPhoto()
            if (action == DiscardAction.CLOSE) onClose()
        }
    }

    fun confirmCapturedPhoto() {
        val uri = capturedPhotoUri ?: return
        val note = noteEditor?.text?.trimEnd().orEmpty()
        capturedPhotoUri = null // Handed over: must not be deleted on dispose.
        currentOnPhotoCaptured(uri, note)
        onClose()
    }

    // While reviewing, back returns to the viewfinder instead of closing the camera.
    BackHandler(enabled = isReviewing) { requestDiscard(DiscardAction.RETAKE) }

    // --- UI -------------------------------------------------------------------------------------

    val zoomState = camera?.cameraInfo?.zoomState?.observeAsState()
    val currentZoom = zoomState?.value?.zoomRatio ?: 1f
    val minZoom = zoomState?.value?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.value?.maxZoomRatio ?: 10f

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        camera?.cameraControl?.setZoomRatio(currentZoom * zoomChange)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                // Consume all touch events to prevent pass-through to underlying screens
                detectTapGestures { }
            }
    ) {
        // 1. Top bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .zIndex(1f)
        ) {
            IconButton(
                onClick = { if (isReviewing) requestDiscard(DiscardAction.CLOSE) else onClose() },
                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close camera", tint = Color.White)
            }

            Text(
                text = when {
                    isReviewing -> "Photo note"
                    isBurstMode -> "Fast Capture Mode"
                    else -> "Capture Report"
                },
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )

            if (noteEditor != null) {
                val iconColors = IconButtonDefaults.iconButtonColors(
                    contentColor = Color.White,
                    disabledContentColor = Color.White.copy(alpha = 0.38f)
                )
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = noteEditor::undo, enabled = noteEditor.history.canUndo, colors = iconColors) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = noteEditor::redo, enabled = noteEditor.history.canRedo, colors = iconColors) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                }
            } else {
                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(
                        onClick = {
                            isBurstMode = !isBurstMode
                            if (isBurstMode) {
                                burstCount = 0
                                lastBurstUri = null
                            }
                        },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                if (isBurstMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            Icons.Default.BurstMode,
                            contentDescription = if (isBurstMode) "Turn off fast capture" else "Turn on fast capture",
                            tint = if (isBurstMode) MaterialTheme.colorScheme.onPrimary else Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            flashMode = when (flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                        },
                        modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        val (icon, label) = when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn to "Flash on"
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto to "Flash auto"
                            else -> Icons.Default.FlashOff to "Flash off"
                        }
                        Icon(icon, contentDescription = label, tint = Color.White)
                    }
                }
            }
        }

        // 2. Preview / review area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(state = transformableState, enabled = !isReviewing)
                    .pointerInput(isReviewing) {
                        if (isReviewing) return@pointerInput
                        detectTapGestures { offset ->
                            focusPoint = offset
                            showFocusRing = true
                            val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
                        }
                    }
            ) {
                // Kept composed during review so the camera doesn't need to restart on retake.
                AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

                val point = focusPoint
                if (showFocusRing && point != null && !isReviewing) {
                    FocusRing(
                        modifier = Modifier.offset {
                            IntOffset(
                                (point.x - 40.dp.toPx()).roundToInt(),
                                (point.y - 40.dp.toPx()).roundToInt()
                            )
                        },
                        color = MaterialTheme.colorScheme.secondary,
                        onTimeout = { showFocusRing = false }
                    )
                }
            }

            if (capturedPhotoUri != null) {
                AsyncImage(
                    model = capturedPhotoUri,
                    contentDescription = "Captured photo",
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentScale = ContentScale.Fit
                )
            }

            if (shutterFlash.value > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.White.copy(alpha = shutterFlash.value))
                )
            }

            SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
        }

        // 3. Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .imePadding()
                .padding(
                    bottom = if (isReviewing) 16.dp else 48.dp,
                    top = if (isReviewing) 0.dp else 24.dp
                )
                .zIndex(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (noteEditor == null) {
                val availableRatios = remember(minZoom, maxZoom) {
                    mutableListOf<Float>().apply {
                        if (minZoom < 0.9f) add(minZoom) // Ultrawide
                        if (1f in minZoom..maxZoom) add(1f)
                        if (maxZoom >= 5f) add(5f) else if (maxZoom >= 2f) add(2f)
                        if (maxZoom >= 10f && size < 4) add(10f)
                    }.distinct().sorted()
                }

                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.1f), CircleShape)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val closestRatio = availableRatios.minByOrNull { abs(it - currentZoom) }
                    availableRatios.forEach { ratio ->
                        val label = when {
                            ratio < 1f -> ".${(ratio * 10).roundToInt()}x"
                            ratio == 1f -> "1x"
                            else -> "${ratio.roundToInt()}x"
                        }
                        ZoomButton(text = label, isSelected = closestRatio == ratio) {
                            camera?.cameraControl?.setZoomRatio(ratio)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        if (isBurstMode) {
                            BurstThumbnail(
                                lastPhoto = lastBurstUri,
                                count = burstCount,
                                isProcessing = processingCount > 0
                            )
                        }
                    }

                    ShutterButton(
                        onClick = ::capture,
                        enabled = camera != null,
                        isBusy = isShutterBusy || (!isBurstMode && processingCount > 0)
                    )

                    Spacer(modifier = Modifier.size(64.dp))
                }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    PhotoNoteEditor(noteEditor, autoFocus = false)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { requestDiscard(DiscardAction.RETAKE) },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "Retake", tint = Color.White)
                    }

                    ConfirmButton(onClick = ::confirmCapturedPhoto)
                }
            }
        }
    }

    pendingDiscard?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("Discard this photo?") },
            text = { Text("The photo and the note you've written will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscard = null
                    discardCapturedPhoto()
                    if (action == DiscardAction.CLOSE) onClose()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("Keep editing") }
            }
        )
    }
}

/**
 * Best-effort location for GPS watermarks: starts with a recent cached fix (if any) and then
 * requests a fresh one. Returns null when disabled, without permission, or with no fix yet.
 */
@SuppressLint("MissingPermission") // Checked below.
@Composable
private fun rememberCurrentLocation(enabled: Boolean): Location? {
    val context = LocalContext.current
    var location by remember { mutableStateOf<Location?>(null) }

    DisposableEffect(enabled) {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val manager = context.getSystemService(LocationManager::class.java)
        if (!enabled || manager == null || !(fine || coarse)) {
            location = null
            return@DisposableEffect onDispose { }
        }

        val cancellation = CancellationSignal()
        try {
            val now = SystemClock.elapsedRealtimeNanos()
            location = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
                .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
                .filter { now - it.elapsedRealtimeNanos < MAX_LOCATION_AGE_NANOS }
                .maxByOrNull { it.elapsedRealtimeNanos }

            val provider = when {
                fine && manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider != null) {
                manager.getCurrentLocation(provider, cancellation, ContextCompat.getMainExecutor(context)) { fix ->
                    if (fix != null) location = fix
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location unavailable", e)
        }
        onDispose { cancellation.cancel() }
    }
    return location
}

private fun deletePhotoFile(uri: Uri, scope: CoroutineScope) {
    val path = uri.path ?: return
    scope.launch(Dispatchers.IO) {
        if (!File(path).delete()) Log.w(TAG, "Could not delete discarded photo $path")
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun FocusRing(modifier: Modifier, color: Color, onTimeout: () -> Unit) {
    val scale = remember { Animatable(1.5f) }
    val alpha = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        delay(1000)
        alpha.animateTo(0f, animationSpec = tween(500))
        onTimeout()
    }

    Box(
        modifier = modifier
            .size(80.dp)
            .scale(scale.value)
            .alpha(alpha.value)
            .border(2.dp, color, CircleShape)
    )
}

@Composable
fun ZoomButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent,
        modifier = Modifier.size(40.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondary else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit, enabled: Boolean = true, isBusy: Boolean = false) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onClick,
        enabled = enabled && !isBusy,
        shape = CircleShape,
        modifier = Modifier
            .size(88.dp)
            .semantics { contentDescription = if (isBusy) "Saving photo" else "Take photo" },
        border = BorderStroke(4.dp, Color.White.copy(alpha = if (enabled) 1f else 0.38f)),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
                .background(primaryColor.copy(alpha = if (isBusy) 0.6f else 1f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isBusy) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/** Last photo taken in fast-capture mode with a running count, plus a spinner while saving. */
@Composable
private fun BurstThumbnail(lastPhoto: Uri?, count: Int, isProcessing: Boolean) {
    BadgedBox(
        badge = {
            if (count > 0) Badge { Text("$count") }
        },
        modifier = Modifier.semantics {
            contentDescription = "$count ${if (count == 1) "photo" else "photos"} saved"
        }
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.1f))
                .border(2.dp, Color.White, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (lastPhoto != null) {
                AsyncImage(
                    model = lastPhoto,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isProcessing) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun ConfirmButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(88.dp),
        border = BorderStroke(4.dp, Color.White),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Save photo",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}
