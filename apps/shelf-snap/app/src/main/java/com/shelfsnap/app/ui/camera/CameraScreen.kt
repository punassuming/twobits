package com.shelfsnap.app.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.shelfsnap.app.R
import com.shelfsnap.app.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onItemSaved: (Long) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    itemId: Long = -1L,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(itemId) { viewModel.initFor(itemId) }

    LaunchedEffect(uiState.savedItemId) {
        uiState.savedItemId?.let { onItemSaved(it) }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (uiState.showApiKeyPrompt) {
        AlertDialog(
            onDismissRequest = viewModel::dismissApiKeyPrompt,
            title = { Text(stringResource(R.string.no_api_key_dialog_title)) },
            text = { Text(stringResource(R.string.no_api_key_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissApiKeyPrompt()
                    onOpenSettings()
                }) {
                    Text(stringResource(R.string.open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::saveWithoutAnalysis) {
                    Text(stringResource(R.string.save_without_analysis))
                }
            }
        )
    }

    val appendMode = uiState.appendToItemId != null

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { _ ->
        if (!cameraPermission.status.isGranted) {
            CameraPermissionRequest(
                modifier = Modifier.fillMaxSize(),
                onRequest = { cameraPermission.launchPermissionRequest() }
            )
        } else {
            CameraContent(
                modifier = Modifier.fillMaxSize(),
                capturedPaths = uiState.capturedPaths,
                isAnalysing = uiState.isAnalysing,
                flashOn = uiState.flashOn,
                appendMode = appendMode,
                onBack = onBack,
                onToggleFlash = viewModel::toggleFlash,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onRemovePhoto = viewModel::removePhoto,
                onAnalyseAndSave = viewModel::onAnalyseClicked,
                onCommitAppend = viewModel::commitAppend,
                onCaptureError = viewModel::showError,
                context = context
            )
        }
    }
}

@Composable
private fun CameraPermissionRequest(
    modifier: Modifier = Modifier,
    onRequest: () -> Unit
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.camera_permission_required),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.grant_permission))
        }
    }
}

@Composable
private fun CameraContent(
    modifier: Modifier = Modifier,
    capturedPaths: List<String>,
    isAnalysing: Boolean,
    flashOn: Boolean,
    appendMode: Boolean,
    onBack: () -> Unit,
    onToggleFlash: () -> Unit,
    onPhotoCaptured: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onAnalyseAndSave: () -> Unit,
    onCommitAppend: () -> Unit,
    onCaptureError: (String) -> Unit,
    context: Context
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val captureFailedMessage = stringResource(R.string.photo_capture_failed)
    val storageUnavailableMessage = stringResource(R.string.storage_unavailable)
    val scope = rememberCoroutineScope()
    val flashAlpha = remember { Animatable(0f) }
    var previewPath by remember { mutableStateOf<String?>(null) }

    previewPath?.let { path ->
        Dialog(onDismissRequest = { previewPath = null }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                AsyncImage(
                    model = File(path),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { previewPath = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Camera viewfinder ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            imageCapture = capture
                            runCatching {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                ViewfinderOverlay()

                if (flashAlpha.value > 0f) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.White.copy(alpha = flashAlpha.value))
                    )
                }

                // Top controls overlay
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.60f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CamIconBtn(icon = Icons.Default.Close, onClick = onBack)
                    Spacer(modifier = Modifier.weight(1f))
                    CamIconBtn(
                        icon = if (flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        onClick = onToggleFlash,
                        active = flashOn
                    )
                }

                // AI tip pill
                val tipText = if (capturedPaths.isEmpty())
                    stringResource(R.string.hint_center_item)
                else
                    stringResource(R.string.hint_photos_captured, capturedPaths.size)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .background(Color.Black.copy(alpha = 0.60f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = tipText,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.88f)
                    )
                }
            }

            // ── Dark bottom panel ──────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Photo strip
                if (capturedPaths.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(capturedPaths) { path ->
                            val index = capturedPaths.indexOf(path)
                            val isLatest = index == capturedPaths.size - 1
                            Box(modifier = Modifier.size(96.dp)) {
                                AsyncImage(
                                    model = File(path),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(14.dp))
                                        .then(
                                            if (isLatest) Modifier.border(
                                                2.dp,
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(14.dp)
                                            ) else Modifier
                                        )
                                        .clickable { previewPath = path },
                                    contentScale = ContentScale.Crop
                                )
                                IconButton(
                                    onClick = { onRemovePhoto(path) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(22.dp)
                                        .background(
                                            MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                            CircleShape
                                        )
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(R.string.delete),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.5f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        item { AddAnotherHint() }
                    }
                }

                // Shutter row: gallery thumb | shutter | analyse pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = if (capturedPaths.isEmpty()) 8.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Gallery thumbnail — shows first captured photo
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1A1A1A))
                            .then(
                                if (capturedPaths.isNotEmpty())
                                    Modifier.clickable { previewPath = capturedPaths.first() }
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (capturedPaths.isNotEmpty()) {
                            AsyncImage(
                                model = File(capturedPaths.first()),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Shutter — classic white ring
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .clickable {
                                val file = ImageUtils.createImageFile(context)
                                if (file == null) {
                                    onCaptureError(storageUnavailableMessage)
                                    return@clickable
                                }
                                imageCapture?.flashMode =
                                    if (flashOn) ImageCapture.FLASH_MODE_ON
                                    else ImageCapture.FLASH_MODE_OFF
                                val outputOptions =
                                    ImageCapture.OutputFileOptions.Builder(file).build()
                                imageCapture?.takePicture(
                                    outputOptions, executor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(
                                            output: ImageCapture.OutputFileResults
                                        ) {
                                            onPhotoCaptured(file.absolutePath)
                                            scope.launch {
                                                flashAlpha.snapTo(0.4f)
                                                flashAlpha.animateTo(0f, tween(250))
                                            }
                                        }
                                        override fun onError(e: ImageCaptureException) {
                                            file.delete()
                                            onCaptureError(captureFailedMessage)
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = size.minDimension / 2f
                            val bw = 3.dp.toPx()
                            drawCircle(Color.White, radius = r, style = Stroke(width = bw))
                            drawCircle(Color.White, radius = r - bw - 4.dp.toPx())
                        }
                    }

                    // Right: analyse pill / append button / empty placeholder
                    Box(contentAlignment = Alignment.Center) {
                        when {
                            capturedPaths.isEmpty() -> Spacer(Modifier.size(44.dp))
                            appendMode -> Surface(
                                onClick = onCommitAppend,
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp, vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Add (${capturedPaths.size})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                            else -> Surface(
                                onClick = onAnalyseAndSave,
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        horizontal = 14.dp, vertical = 8.dp
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.analyse_count, capturedPaths.size
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Full-screen analysing overlay
        if (isAnalysing) {
            AnalysingView(
                photoCount = capturedPaths.size,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun AnalysingView(photoCount: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 0.95f,
            animationSpec = tween(durationMillis = 12000, easing = FastOutSlowInEasing)
        )
    }
    val steps = listOf(
        0.20f to "Reading photos",
        0.45f to "Identifying item",
        0.70f to "Fetching market data",
        0.88f to "Estimating value",
        1.00f to "Generating description"
    )
    val currentStep = steps.firstOrNull { progress.value <= it.first }?.second ?: "Finalising…"

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.analyzing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Processing $photoCount photo${if (photoCount != 1) "s" else ""}…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(28.dp))
        @Suppress("DEPRECATION")
        LinearProgressIndicator(
            progress = progress.value,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = currentStep,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CamIconBtn(
    icon: ImageVector,
    onClick: () -> Unit,
    active: Boolean = false
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Decorative dashed tile at the end of the photo strip. */
@Composable
private fun AddAnotherHint() {
    val outline = Color.White.copy(alpha = 0.4f)
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    )
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AddAPhoto,
            contentDescription = null,
            tint = outline,
            modifier = Modifier.size(22.dp)
        )
    }
}

/** Rule-of-thirds grid and L-bracket corner guides over the preview. */
@Composable
private fun ViewfinderOverlay() {
    val primary = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // Subtle rule-of-thirds grid
        val gridLine = Color.White.copy(alpha = 0.06f)
        for (i in 1..2) {
            val x = w * i / 3f
            drawLine(gridLine, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            val y = h * i / 3f
            drawLine(gridLine, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        // Corner bracket guides
        val arm = 28.dp.toPx()
        val m = 20.dp.toPx()
        val sw = 2.dp.toPx()
        // Top-left
        drawLine(primary, Offset(m, m), Offset(m + arm, m), strokeWidth = sw)
        drawLine(primary, Offset(m, m), Offset(m, m + arm), strokeWidth = sw)
        // Top-right
        drawLine(primary, Offset(w - m, m), Offset(w - m - arm, m), strokeWidth = sw)
        drawLine(primary, Offset(w - m, m), Offset(w - m, m + arm), strokeWidth = sw)
        // Bottom-left
        drawLine(primary, Offset(m, h - m), Offset(m + arm, h - m), strokeWidth = sw)
        drawLine(primary, Offset(m, h - m), Offset(m, h - m - arm), strokeWidth = sw)
        // Bottom-right
        drawLine(primary, Offset(w - m, h - m), Offset(w - m - arm, h - m), strokeWidth = sw)
        drawLine(primary, Offset(w - m, h - m), Offset(w - m, h - m - arm), strokeWidth = sw)
    }
}
