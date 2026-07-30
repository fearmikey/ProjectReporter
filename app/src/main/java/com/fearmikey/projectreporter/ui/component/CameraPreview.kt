package com.fearmikey.projectreporter.ui.component

import android.net.Uri
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun CameraPreview(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit,
    defaultFlashMode: FlashModeOption
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    val previewView = remember { 
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val zoomState = camera?.cameraInfo?.zoomState?.observeAsState()
    val currentZoom = zoomState?.value?.zoomRatio ?: 1f
    val minZoom = zoomState?.value?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.value?.maxZoomRatio ?: 10f

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    var flashMode by remember { 
        mutableIntStateOf(
            when (defaultFlashMode) {
                FlashModeOption.OFF -> ImageCapture.FLASH_MODE_OFF
                FlashModeOption.ON -> ImageCapture.FLASH_MODE_ON
                FlashModeOption.AUTO -> ImageCapture.FLASH_MODE_AUTO
            }
        )
    }

    LaunchedEffect(flashMode) {
        imageCapture.flashMode = flashMode
    }

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
        // 1. Dedicated Top Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .zIndex(1f) // Force to front
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            
            Text(
                text = "Capture Report",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )

            IconButton(
                onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                val icon = when (flashMode) {
                    ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                    ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                    else -> Icons.Default.FlashOff
                }
                Icon(icon, contentDescription = "Flash Mode", tint = Color.White)
            }
        }

        // 2. Central Preview Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
                .transformable(state = transformableState)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        focusPoint = offset
                        showFocusRing = true
                        val factory = previewView.meteringPointFactory
                        val point = factory.createPoint(offset.x, offset.y)
                        val action = FocusMeteringAction.Builder(point).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                    }
                }
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            ) { view ->
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(view.surfaceProvider)
                    }

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageCapture
                        )
                    } catch (e: Exception) {
                        Log.e("CameraPreview", "Use case binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(context))
            }

            // Focus Ring (relative to this box)
            if (showFocusRing && focusPoint != null) {
                FocusRing(
                    modifier = Modifier.offset {
                        IntOffset(
                            (focusPoint!!.x - 40.dp.toPx()).roundToInt(),
                            (focusPoint!!.y - 40.dp.toPx()).roundToInt()
                        )
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    onTimeout = { showFocusRing = false }
                )
            }
        }

        // 3. Dedicated Bottom Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .padding(bottom = 48.dp, top = 24.dp)
                .zIndex(1f), // Force to front
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Controls (Simplified to 3-4 options)
            val availableRatios = remember(minZoom, maxZoom) {
                mutableListOf<Float>().apply {
                    // 1. Ultrawide if available
                    if (minZoom < 0.9f) add(minZoom)
                    // 2. Standard 1x
                    if (1f in minZoom..maxZoom) add(1f)
                    // 3. Telephoto / Zoom
                    if (maxZoom >= 5f) add(5f)
                    else if (maxZoom >= 2f) add(2f)
                    // 4. Max Zoom if it's high enough and we have space
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
                availableRatios.forEach { ratio ->
                    val label = when {
                        ratio < 1f -> ".${(ratio * 10).roundToInt()}x"
                        ratio == 1f -> "1x"
                        else -> "${ratio.roundToInt()}x"
                    }
                    
                    // Highlight the closest ratio
                    val isSelected = availableRatios.minByOrNull { abs(it - currentZoom) } == ratio

                    ZoomButton(text = label, isSelected = isSelected) {
                        camera?.cameraControl?.setZoomRatio(ratio)
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Shutter Button
            ShutterButton(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor, onPhotoCaptured)
                }
            )
        }
    }
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
fun ShutterButton(onClick: () -> Unit) {
    val primaryColor = MaterialTheme.colorScheme.primary
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
                .background(primaryColor, CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        )
    }
}

private fun takePhoto(
    context: android.content.Context,
    imageCapture: ImageCapture,
    executor: Executor,
    onPhotoCaptured: (Uri) -> Unit
) {
    val outputDirectory = context.getExternalFilesDir(null) ?: context.filesDir
    val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")

    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val savedUri = Uri.fromFile(photoFile)
                onPhotoCaptured(savedUri)
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}
