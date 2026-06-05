package com.shelfsnap.app.ui.camera

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlin.math.min

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

    // Navigate as soon as a saved item ID is available
    LaunchedEffect(uiState.savedItemId) {
        uiState.savedItemId?.let { onItemSaved(it) }
    }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // No API key configured — let the user choose between Settings and saving as-is.
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (appendMode) stringResource(R.string.add_another_photo)
                        else stringResource(R.string.take_photo)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::toggleFlash) {
                        Icon(
                            if (uiState.flashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = stringResource(R.string.toggle_flash)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (!cameraPermission.status.isGranted) {
            CameraPermissionRequest(
                modifier = Modifier.padding(padding),
                onRequest = { cameraPermission.launchPermissionRequest() }
            )
        } else {
            CameraContent(
                modifier = Modifier.padding(padding),
                capturedPaths = uiState.capturedPaths,
                isAnalysing = uiState.isAnalysing,
                flashOn = uiState.flashOn,
                appendMode = appendMode,
                autoAnalyze = uiState.autoAnalyze,
                onPhotoCaptured = viewModel::onPhotoCaptured,
                onRemovePhoto = viewModel::removePhoto,
                onAnalyseAndSave = viewModel::onAnalyseClicked,
                onSaveWithoutAnalysis = viewModel::saveWithoutAnalysis,
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
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
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
    autoAnalyze: Boolean,
    onPhotoCaptured: (String) -> Unit,
    onRemovePhoto: (String) -> Unit,
    onAnalyseAndSave: () -> Unit,
    onSaveWithoutAnalysis: () -> Unit,
    onCommitAppend: () -> Unit,
    onCaptureError: (String) -> Unit,
    context: Context
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val captureFailedMessage = stringResource(R.string.photo_capture_failed)
    val storageUnavailableMessage = stringResource(R.string.storage_unavailable)

    // Capture flash effect
    val scope = rememberCoroutineScope()
    val flashAlpha = remember { Animatable(0f) }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Camera preview
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

            // Rule-of-thirds grid + center reticle framing overlay
            ViewfinderOverlay()

            // Capture flash
            if (flashAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = flashAlpha.value))
                )
            }

            // Hint text
            Text(
                text = if (capturedPaths.isEmpty())
                    stringResource(R.string.hint_center_item)
                else
                    stringResource(R.string.hint_photos_captured, capturedPaths.size),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
            )

            // Shutter button (on top so it stays tappable)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
            ) {
                IconButton(
                    onClick = {
                        val file = ImageUtils.createImageFile(context)
                        if (file == null) {
                            onCaptureError(storageUnavailableMessage)
                            return@IconButton
                        }
                        imageCapture?.flashMode =
                            if (flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture?.takePicture(
                            outputOptions,
                            executor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    onPhotoCaptured(file.absolutePath)
                                    scope.launch {
                                        flashAlpha.snapTo(0.4f)
                                        flashAlpha.animateTo(0f, tween(250))
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    // File may be partially written; remove it, then tell the user.
                                    file.delete()
                                    onCaptureError(captureFailedMessage)
                                }
                            }
                        )
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = stringResource(R.string.take_photo),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Photo strip – numbered, removable, with an "add another" hint slot
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
        if (capturedPaths.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(capturedPaths) { path ->
                    val index = capturedPaths.indexOf(path)
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(14.dp))
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

        // Bottom actions
        if (capturedPaths.isNotEmpty()) {
            if (appendMode) {
                Button(
                    onClick = onCommitAppend,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_to_item, capturedPaths.size))
                }
            } else if (autoAnalyze) {
                // Auto-analyze on: a single one-tap Analyze action (no Save/Analyze choice).
                Button(
                    onClick = onAnalyseAndSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = !isAnalysing
                ) {
                    if (isAnalysing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.analyzing))
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.analyze))
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSaveWithoutAnalysis,
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalysing
                    ) {
                        Text(stringResource(R.string.save_without_analysis))
                    }
                    Button(
                        onClick = onAnalyseAndSave,
                        modifier = Modifier.weight(1f),
                        enabled = !isAnalysing
                    ) {
                        if (isAnalysing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.analyzing))
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.analyze))
                        }
                    }
                }
            }

            // Reassure the user the (potentially slow) analysis is still running.
            if (isAnalysing && !appendMode) {
                Text(
                    text = stringResource(R.string.analyzing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/** Decorative dashed tile at the end of the photo strip hinting that more can be added. */
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

/** Rule-of-thirds grid and a centered framing reticle drawn over the preview. */
@Composable
private fun ViewfinderOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val line = Color.White.copy(alpha = 0.10f)
        for (i in 1..2) {
            val x = w * i / 3f
            drawLine(line, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
            val y = h * i / 3f
            drawLine(line, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }
        val sideLen = min(w, h) * 0.45f
        val left = (w - sideLen) / 2f
        val top = (h - sideLen) / 2f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.18f),
            topLeft = Offset(left, top),
            size = Size(sideLen, sideLen),
            cornerRadius = CornerRadius(16.dp.toPx()),
            style = Stroke(width = 2.dp.toPx())
        )
    }
}
