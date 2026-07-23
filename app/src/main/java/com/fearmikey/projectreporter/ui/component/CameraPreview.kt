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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Composable
fun CameraPreview(
    onPhotoCaptured: (Uri) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val zoomState = camera?.cameraInfo?.zoomState?.observeAsState()
    val currentZoom = zoomState?.value?.zoomRatio ?: 1f
    val minZoom = zoomState?.value?.minZoomRatio ?: 1f
    val maxZoom = zoomState?.value?.maxZoomRatio ?: 10f

    var focusPoint by remember { mutableStateOf<Offset?>(null) }
    var showFocusRing by remember { mutableStateOf(false) }

    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        camera?.cameraControl?.setZoomRatio(currentZoom * zoomChange)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .transformable(state = transformableState)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
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

        // Focus Ring
        if (showFocusRing && focusPoint != null) {
            FocusRing(
                modifier = Modifier.offset {
                    IntOffset(
                        (focusPoint!!.x - 40.dp.toPx()).roundToInt(),
                        (focusPoint!!.y - 40.dp.toPx()).roundToInt()
                    )
                },
                onTimeout = { showFocusRing = false }
            )
        }

        // Top Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Zoom Buttons (Google Camera Style)
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp) // Adjusted to prevent shielding
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZoomButton(text = "1x", isSelected = currentZoom < 1.5f) {
                camera?.cameraControl?.setZoomRatio(1f)
            }
            ZoomButton(text = "2x", isSelected = currentZoom >= 1.5f && currentZoom < 4f) {
                camera?.cameraControl?.setZoomRatio(2f)
            }
            if (maxZoom >= 5f) {
                ZoomButton(text = "5x", isSelected = currentZoom >= 4f) {
                    camera?.cameraControl?.setZoomRatio(5f)
                }
            }
        }

        // Shutter Button Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.2f))
                .navigationBarsPadding()
                .padding(bottom = 64.dp, top = 16.dp), // Adjusted to lower the button
            contentAlignment = Alignment.Center
        ) {
            ShutterButton(
                onClick = {
                    takePhoto(context, imageCapture, cameraExecutor, onPhotoCaptured)
                }
            )
        }
    }
}

@Composable
fun FocusRing(modifier: Modifier, onTimeout: () -> Unit) {
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
            .border(2.dp, Color.Yellow, CircleShape)
    )
}

@Composable
fun ZoomButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (isSelected) Color.White else Color.Transparent,
        modifier = Modifier.size(36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) Color.Black else Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ShutterButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        modifier = Modifier.size(72.dp),
        border = BorderStroke(4.dp, Color.White),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
                .background(Color.White, CircleShape)
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
