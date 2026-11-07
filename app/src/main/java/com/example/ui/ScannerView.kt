package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Size
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.Forklift
import com.example.ui.theme.ScanGreen
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@OptIn(ExperimentalGetImage::class)
@Composable
fun ScannerView(
    bannerMessage: String?,
    onBarcodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("scanner_permission_screen"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "To scan QR codes and barcodes continuously, please grant camera access.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    modifier = Modifier.testTag("request_camera_button")
                ) {
                    Text("Grant Permission")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.outlinedButtonColors()
                ) {
                    Text("Cancel")
                }
            }
        }
        return
    }

    var camera by remember { mutableStateOf<Camera?>(null) }
    var isFlashOn by remember { mutableStateOf(false) }
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }

    // A code counts only after several frames in a row agree on it, which a moving or unsteady
    // camera cannot produce. rememberUpdatedState keeps the analyzer pointing at the current
    // callback instead of the one captured when the reader was first remembered.
    val currentOnBarcodeScanned by rememberUpdatedState(onBarcodeScanned)
    val reader = remember {
        StableBarcodeReader(onConfirmed = { code ->
            mainExecutor.execute { currentOnBarcodeScanned(code) }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            scanner.close()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("scanner_view_container")
    ) {
        // Camera Preview
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    tag = lensFacing
                }

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    bindScannerCamera(
                        provider = cameraProvider,
                        lifecycleOwner = lifecycleOwner,
                        previewView = previewView,
                        lensFacing = lensFacing,
                        scanner = scanner,
                        executor = cameraExecutor,
                        reader = reader
                    )?.let { camera = it }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            update = { previewView ->
                // update runs on every recomposition. Unbinding and rebinding each time restarted
                // the preview mid-scan, so only a lens flip actually rebuilds the pipeline.
                if (previewView.tag != lensFacing) {
                    previewView.tag = lensFacing
                    val cameraProviderFuture =
                        ProcessCameraProvider.getInstance(previewView.context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        bindScannerCamera(
                            provider = cameraProvider,
                            lifecycleOwner = lifecycleOwner,
                            previewView = previewView,
                            lensFacing = lensFacing,
                            scanner = scanner,
                            executor = cameraExecutor,
                            reader = reader
                        )?.let { camera = it }
                    }, ContextCompat.getMainExecutor(previewView.context))
                }
            }
        )

        // Target Reticle & Animated Scan Beam
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val reticleSize = (maxWidth * 0.75f).coerceAtMost(280.dp)
            val transition = rememberInfiniteTransition(label = "scan_beam")
            val beamFraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "beam_offset"
            )

            // Four corner brackets and one sweeping line, in the same yellow as the Scan button.
            Canvas(modifier = Modifier.size(reticleSize)) {
                val arm = size.minDimension * 0.14f
                val stroke = 5.dp.toPx()
                fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(Forklift, Offset(x - dx * stroke / 2, y), Offset(x + dx * arm, y), strokeWidth = stroke)
                    drawLine(Forklift, Offset(x, y - dy * stroke / 2), Offset(x, y + dy * arm), strokeWidth = stroke)
                }
                corner(0f, 0f, 1f, 1f)
                corner(size.width, 0f, -1f, 1f)
                corner(0f, size.height, 1f, -1f)
                corner(size.width, size.height, -1f, -1f)

                val yPos = size.height * beamFraction
                drawLine(
                    color = Forklift.copy(alpha = 0.9f),
                    start = Offset(stroke, yPos),
                    end = Offset(size.width - stroke, yPos),
                    strokeWidth = 3.dp.toPx()
                )
            }
        }

        // Top Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .testTag("scanner_close_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Scanner",
                    tint = Color.White
                )
            }

            Text(
                text = "Scanning",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            )

            Row {
                IconButton(
                    onClick = {
                        val newFlash = !isFlashOn
                        camera?.cameraControl?.enableTorch(newFlash)
                        isFlashOn = newFlash
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("scanner_flash_button")
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Toggle Flash",
                        tint = if (isFlashOn) Forklift else Color.White
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("scanner_flip_camera_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Flip Camera",
                        tint = Color.White
                    )
                }
            }
        }

        // Bottom Animated Feedback Banner
        AnimatedVisibility(
            visible = bannerMessage != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp, start = 20.dp, end = 20.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = ScanGreen,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bannerMessage.orEmpty(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/**
 * Accepts a code only after [requiredFrames] consecutive frames agree on it, and only while those
 * frames keep arriving close together. A camera that is still moving rarely repeats the same wrong
 * reading, so agreement across frames is what keeps a misread from becoming a wrong count.
 */
internal class StableBarcodeReader(
    private val onConfirmed: (String) -> Unit,
    private val requiredFrames: Int = 3,
    private val maxFrameGapMs: Long = 900L,
    private val now: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var candidate: String? = null
    private var agreeingFrames = 0
    private var lastSeenAt = 0L

    fun onFrame(codes: List<String>) {
        val frameAt = now()
        val code = codes.firstOrNull()
        if (code == null) {
            candidate = null
            agreeingFrames = 0
            return
        }
        if (code == candidate && frameAt - lastSeenAt <= maxFrameGapMs) {
            agreeingFrames++
        } else {
            candidate = code
            agreeingFrames = 1
        }
        lastSeenAt = frameAt

        if (agreeingFrames >= requiredFrames) {
            // Start over, so the next accepted read has to earn its own agreement.
            candidate = null
            agreeingFrames = 0
            onConfirmed(code)
        }
    }
}

/** Builds the preview and analyser pipeline, binds it to [lifecycleOwner], returns the camera. */
@OptIn(ExperimentalGetImage::class)
private fun bindScannerCamera(
    provider: ProcessCameraProvider,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    lensFacing: Int,
    scanner: BarcodeScanner,
    executor: Executor,
    reader: StableBarcodeReader
): Camera? {
    val preview = Preview.Builder().build().also {
        it.surfaceProvider = previewView.surfaceProvider
    }

    val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        // Small, dense codes need more pixels than the default 640x480 frame to resolve.
        .setResolutionSelector(
            ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
        )
        .build()

    imageAnalysis.setAnalyzer(executor) { imageProxy ->
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return@setAnalyzer
        }
        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                reader.onFrame(barcodes.mapNotNull { it.rawValue?.takeIf { value -> value.isNotBlank() } })
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
    return try {
        provider.unbindAll()
        provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalysis)
    } catch (_: Exception) {
        null
    }
}
