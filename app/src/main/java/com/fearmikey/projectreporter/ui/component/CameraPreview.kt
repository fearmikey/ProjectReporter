package com.fearmikey.projectreporter.ui.component

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.location.LocationManager
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.fearmikey.projectreporter.data.repository.FlashModeOption
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var annotation by remember { mutableStateOf("") }
    var isBurstMode by remember { mutableStateOf(false) }
    
    val focusManager = LocalFocusManager.current
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
                text = if (isBurstMode) "Fast Capture Mode" else "Capture Report",
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )

            Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                // Burst Mode Toggle
                IconButton(
                    onClick = { isBurstMode = !isBurstMode },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .background(
                            if (isBurstMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.15f), 
                            CircleShape
                        )
                ) {
                    Icon(if (isBurstMode) Icons.Default.Speed else Icons.Default.Timer, contentDescription = "Burst Mode", tint = Color.White)
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
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Default.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Default.FlashAuto
                        else -> Icons.Default.FlashOff
                    }
                    Icon(icon, contentDescription = "Flash Mode", tint = Color.White)
                }
            }
        }

        // 2. Central Preview Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.DarkGray)
        ) {
            if (capturedPhotoUri == null) {
                Box(modifier = Modifier.fillMaxSize()
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
                    }) {
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
            } else {
                AsyncImage(
                    model = capturedPhotoUri,
                    contentDescription = "Captured Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }

        // 3. Dedicated Bottom Control Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 48.dp, top = 24.dp)
                .zIndex(1f), // Force to front
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (capturedPhotoUri == null) {
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
                        takePhoto(
                            context,
                            imageCapture,
                            cameraExecutor,
                            watermarkTimestamp,
                            watermarkGps,
                            watermarkProjectDetails,
                            projectName
                        ) { uri ->
                            if (isBurstMode) {
                                onPhotoCaptured(uri, "")
                            } else {
                                capturedPhotoUri = uri
                            }
                        }
                    }
                )
            } else {
                OutlinedTextField(
                    value = annotation,
                    onValueChange = { annotation = it },
                    placeholder = { Text("Add a note...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.DarkGray,
                        unfocusedContainerColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { 
                            capturedPhotoUri = null
                            annotation = ""
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .background(Color.DarkGray, CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Retake", tint = Color.White)
                    }

                    // Confirm Button
                    ConfirmButton(
                        onClick = {
                            capturedPhotoUri?.let { uri ->
                                onPhotoCaptured(uri, annotation)
                            }
                        }
                    )
                }
            }
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

@Composable
fun ConfirmButton(onClick: () -> Unit) {
    val successColor = Color(0xFF4CAF50) // Green color
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
                .background(successColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = "Confirm", tint = Color.White, modifier = Modifier.size(48.dp))
        }
    }
}

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    executor: Executor,
    watermarkTimestamp: Boolean,
    watermarkGps: Boolean,
    watermarkProjectDetails: Boolean,
    projectName: String,
    onPhotoCaptured: (Uri) -> Unit
) {
    imageCapture.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                try {
                    val buffer = imageProxy.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)
                    val originalBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, null)

                    val matrix = Matrix()
                    matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                    val rotatedBitmap = Bitmap.createBitmap(
                        originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                    )

                    // Mutable bitmap for drawing
                    val mutableBitmap = rotatedBitmap.copy(Bitmap.Config.ARGB_8888, true)
                    val canvas = Canvas(mutableBitmap)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.YELLOW
                        textSize = (mutableBitmap.height * 0.03f).coerceAtLeast(30f)
                        setShadowLayer(5f, 2f, 2f, android.graphics.Color.BLACK)
                    }

                    val watermarkLines = mutableListOf<String>()

                    if (watermarkTimestamp) {
                        val timestampStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                        watermarkLines.add(timestampStr)
                    }

                    if (watermarkProjectDetails && projectName.isNotBlank()) {
                        watermarkLines.add("Project: $projectName")
                    }

                    if (watermarkGps) {
                        var locationString = "Location Unknown"
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                            val loc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                            if (loc != null) {
                                locationString = "GPS: ${"%.5f".format(loc.latitude)}, ${"%.5f".format(loc.longitude)}"
                            }
                        }
                        watermarkLines.add(locationString)
                    }

                    if (watermarkLines.isNotEmpty()) {
                        var yOffset = mutableBitmap.height * (0.95f - (watermarkLines.size * 0.035f))
                        watermarkLines.forEach { line ->
                            canvas.drawText(line, mutableBitmap.width * 0.05f, yOffset, paint)
                            yOffset += paint.textSize * 1.2f
                        }
                    }

                    val outputDirectory = context.getExternalFilesDir(null) ?: context.filesDir
                    val photoFile = File(outputDirectory, "${System.currentTimeMillis()}.jpg")
                    
                    val outputStream = FileOutputStream(photoFile)
                    mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    outputStream.close()

                    val savedUri = Uri.fromFile(photoFile)
                    
                    // Cleanup
                    originalBitmap.recycle()
                    if (originalBitmap != rotatedBitmap) rotatedBitmap.recycle()
                    mutableBitmap.recycle()

                    // Return to UI thread
                    ContextCompat.getMainExecutor(context).execute {
                        onPhotoCaptured(savedUri)
                    }
                } catch (e: Exception) {
                    Log.e("CameraPreview", "Watermark application failed", e)
                } finally {
                    imageProxy.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraPreview", "Photo capture failed: ${exception.message}", exception)
            }
        }
    )
}
