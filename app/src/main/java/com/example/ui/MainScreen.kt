package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.RecordingEntity
import com.example.data.RecordSettings
import com.example.service.ScreenRecordService
import com.example.viewmodel.ScreenRecordViewModel
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: ScreenRecordViewModel,
    onRequestRecord: () -> Unit,
    onStopRecord: () -> Unit,
    modifier: Modifier = Modifier
) {
    val localContext = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val recordings by viewModel.recordings.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val durationSeconds by viewModel.durationSeconds.collectAsState()
    val error by viewModel.errorFlow.collectAsState()

    // Screen Layout Responsiveness based on width
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isExpanded = configuration.screenWidthDp >= 600

    // Temporary variables for inputs. Detect whether current settings match an
    // aspect-ratio-aware preset; otherwise default to the custom resolution input.
    val currentIsPreset = remember(settings, localContext) {
        val ratio = RecordSettings.getScreenAspect(localContext)
        val fhdHeight = ((1080 * ratio) / 16.0).roundToInt() * 16
        val hdHeight = ((720 * ratio) / 16.0).roundToInt() * 16
        val sdHeight = ((480 * ratio) / 16.0).roundToInt() * 16
        (settings.resolutionWidth == 1080 && settings.resolutionHeight == fhdHeight) ||
        (settings.resolutionWidth == 720 && settings.resolutionHeight == hdHeight) ||
        (settings.resolutionWidth == 480 && settings.resolutionHeight == sdHeight)
    }
    var showCustomResolution by remember { mutableStateOf(!currentIsPreset) }
    var widthInput by remember { mutableStateOf(settings.resolutionWidth.toString()) }
    var heightInput by remember { mutableStateOf(settings.resolutionHeight.toString()) }

    var showCustomFps by remember { mutableStateOf(false) }
    var fpsInput by remember { mutableStateOf(settings.fps.toString()) }

    var showEncoderDropdown by remember { mutableStateOf(false) }

    // Sync input states when loaded settings change
    LaunchedEffect(settings) {
        if (!showCustomResolution) {
            widthInput = settings.resolutionWidth.toString()
            heightInput = settings.resolutionHeight.toString()
        }
        if (!showCustomFps) {
            fpsInput = settings.fps.toString()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(localContext, it, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Screen Recorder")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            if (isExpanded) {
                // Expanded two-pane layout for tablets/foldables
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Settings
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val settingsEnabled = recordingState == ScreenRecordService.RecordingState.IDLE
                        Text(
                            "Configurations",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (!settingsEnabled) {
                                item { LockedBanner() }
                            }
                            item {
                                ResolutionSection(
                                    widthInput = widthInput,
                                    heightInput = heightInput,
                                    enabled = settingsEnabled,
                                    onWidthChange = {
                                        widthInput = it
                                        val w = it.toIntOrNull() ?: 1080
                                        viewModel.updateSettings(settings.copy(resolutionWidth = w))
                                    },
                                    onHeightChange = {
                                        heightInput = it
                                        val h = it.toIntOrNull() ?: 1920
                                        viewModel.updateSettings(settings.copy(resolutionHeight = h))
                                    },
                                    showCustomResolution = showCustomResolution,
                                    onPresetSelected = { w, h ->
                                        showCustomResolution = false
                                        widthInput = w.toString()
                                        heightInput = h.toString()
                                        viewModel.updateSettings(settings.copy(resolutionWidth = w, resolutionHeight = h))
                                    },
                                    onCustomToggle = { showCustomResolution = true }
                                )
                            }
                            item {
                                FpsSection(
                                    fpsInput = fpsInput,
                                    enabled = settingsEnabled,
                                    onFpsChange = {
                                        fpsInput = it
                                        val f = it.toIntOrNull() ?: 30
                                        viewModel.updateSettings(settings.copy(fps = f))
                                    },
                                    showCustomFps = showCustomFps,
                                    onPresetSelected = { f ->
                                        showCustomFps = false
                                        fpsInput = f.toString()
                                        viewModel.updateSettings(settings.copy(fps = f))
                                    },
                                    onCustomToggle = { showCustomFps = true }
                                )
                            }
                            item {
                                EncodingSection(
                                    selectedEncoding = settings.videoEncoding,
                                    enabled = settingsEnabled,
                                    onEncodingSelected = {
                                        viewModel.updateSettings(settings.copy(videoEncoding = it))
                                    },
                                    showDropdown = showEncoderDropdown,
                                    onToggleDropdown = { showEncoderDropdown = it }
                                )
                            }
                            item {
                                AudioSection(
                                    audioSource = settings.audioSource,
                                    onAudioSourceChange = { source ->
                                        viewModel.updateSettings(
                                            settings.copy(
                                                audioSource = source,
                                                recordAudio = source != "None"
                                            )
                                        )
                                    },
                                    mergeAudioVideo = settings.mergeAudioVideo,
                                    onMergeAudioVideoChange = { merge ->
                                        viewModel.updateSettings(settings.copy(mergeAudioVideo = merge))
                                    },
                                    enabled = settingsEnabled
                                )
                            }
                        }
                    }

                    // Right Pane: Recording Status & Logs
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "Operations & History",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        RecordingControllerCard(
                            recordingState = recordingState,
                            durationSeconds = durationSeconds,
                            onRequestRecord = onRequestRecord,
                            onStopRecord = onStopRecord,
                            recordSettings = settings,
                            localContext = localContext
                        )
                        HistorySection(
                            recordings = recordings,
                            onDelete = { viewModel.deleteRecording(it) },
                            onClearAll = { viewModel.clearAllRecordings() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Compact standard one-pane layout for phones
                val settingsEnabled = recordingState == ScreenRecordService.RecordingState.IDLE
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item {
                        RecordingControllerCard(
                            recordingState = recordingState,
                            durationSeconds = durationSeconds,
                            onRequestRecord = onRequestRecord,
                            onStopRecord = onStopRecord,
                            recordSettings = settings,
                            localContext = localContext
                        )
                    }

                    item {
                        Text(
                            "Video & Codec Settings",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (!settingsEnabled) {
                        item { LockedBanner() }
                    }

                    item {
                        ResolutionSection(
                            widthInput = widthInput,
                            heightInput = heightInput,
                            enabled = settingsEnabled,
                            onWidthChange = {
                                widthInput = it
                                val w = it.toIntOrNull() ?: 1080
                                viewModel.updateSettings(settings.copy(resolutionWidth = w))
                            },
                            onHeightChange = {
                                heightInput = it
                                val h = it.toIntOrNull() ?: 1920
                                viewModel.updateSettings(settings.copy(resolutionHeight = h))
                            },
                            showCustomResolution = showCustomResolution,
                            onPresetSelected = { w, h ->
                                showCustomResolution = false
                                widthInput = w.toString()
                                heightInput = h.toString()
                                viewModel.updateSettings(settings.copy(resolutionWidth = w, resolutionHeight = h))
                            },
                            onCustomToggle = { showCustomResolution = true }
                        )
                    }

                    item {
                        FpsSection(
                            fpsInput = fpsInput,
                            enabled = settingsEnabled,
                            onFpsChange = {
                                fpsInput = it
                                val f = it.toIntOrNull() ?: 30
                                viewModel.updateSettings(settings.copy(fps = f))
                            },
                            showCustomFps = showCustomFps,
                            onPresetSelected = { f ->
                                showCustomFps = false
                                fpsInput = f.toString()
                                viewModel.updateSettings(settings.copy(fps = f))
                            },
                            onCustomToggle = { showCustomFps = true }
                        )
                    }

                    item {
                        EncodingSection(
                            selectedEncoding = settings.videoEncoding,
                            enabled = settingsEnabled,
                            onEncodingSelected = {
                                viewModel.updateSettings(settings.copy(videoEncoding = it))
                            },
                            showDropdown = showEncoderDropdown,
                            onToggleDropdown = { showEncoderDropdown = it }
                        )
                    }

                    item {
                        AudioSection(
                            audioSource = settings.audioSource,
                            onAudioSourceChange = { source ->
                                viewModel.updateSettings(
                                    settings.copy(
                                        audioSource = source,
                                        recordAudio = source != "None"
                                    )
                                )
                            },
                            mergeAudioVideo = settings.mergeAudioVideo,
                            onMergeAudioVideoChange = { merge ->
                                viewModel.updateSettings(settings.copy(mergeAudioVideo = merge))
                            },
                            enabled = settingsEnabled
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recording Logs",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (recordings.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllRecordings() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear All")
                                }
                            }
                        }
                    }

                    if (recordings.isEmpty()) {
                        item {
                            EmptyStateCard()
                        }
                    } else {
                        items(recordings, key = { it.id }) { recording ->
                            RecordingHistoryItem(
                                recording = recording,
                                onDelete = { viewModel.deleteRecording(it) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "Settings locked while recording is in progress.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun RecordingControllerCard(
    recordingState: ScreenRecordService.RecordingState,
    durationSeconds: Int,
    onRequestRecord: () -> Unit,
    onStopRecord: () -> Unit,
    recordSettings: RecordSettings,
    localContext: Context
) {
    val isRecording = recordingState == ScreenRecordService.RecordingState.RECORDING ||
        recordingState == ScreenRecordService.RecordingState.PAUSED
    val isPaused = recordingState == ScreenRecordService.RecordingState.PAUSED
    val isProcessing = recordingState == ScreenRecordService.RecordingState.PROCESSING

    // Pulse animation for the recording indicator dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulseAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("controller_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (isRecording) {
                // Active recording state
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.error.copy(
                                    alpha = if (isPaused) 1f else alphaAnim
                                )
                            )
                    )
                    Text(
                        text = if (isPaused) "PAUSED" else "REC",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Text(
                    text = formatSecs(durationSeconds),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "${recordSettings.resolutionWidth}x${recordSettings.resolutionHeight} @ ${recordSettings.fps} FPS · ${recordSettings.videoEncoding}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    // Pause/Resume Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        FilledTonalButton(
                            onClick = {
                                val action = if (isPaused) {
                                    ScreenRecordService.ACTION_RESUME
                                } else {
                                    ScreenRecordService.ACTION_PAUSE
                                }
                                localContext.startService(Intent(localContext, ScreenRecordService::class.java).apply {
                                    this.action = action
                                })
                            },
                            modifier = Modifier
                                .height(52.dp)
                                .weight(1f)
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                    }

                    // Stop Button
                    Button(
                        onClick = onStopRecord,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        ),
                        modifier = Modifier
                            .height(52.dp)
                            .weight(1.2f)
                            .testTag("stop_record_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop")
                    }
                }
            } else if (isProcessing) {
                // Processing / finalizing state
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.tertiary,
                    strokeWidth = 4.dp,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("processing_progress_bar")
                )
                Text(
                    "Saving & Finalizing…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    "Merging audio tracks and registering the file in your media library. Don't close the app or start another recording.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                // Ready to record state
                Icon(
                    imageVector = Icons.Default.RadioButtonChecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
                )

                Text(
                    "Ready to Record",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    "Tap below to grant screen projection and start capturing. Adjust the settings to match your device for best performance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(
                    onClick = onRequestRecord,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_record_button")
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Start Recording")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = 8.dp)
        )
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResolutionSection(
    widthInput: String,
    heightInput: String,
    enabled: Boolean = true,
    onWidthChange: (String) -> Unit,
    onHeightChange: (String) -> Unit,
    showCustomResolution: Boolean,
    onPresetSelected: (Int, Int) -> Unit,
    onCustomToggle: () -> Unit
) {
    // Compute preset heights from the device aspect ratio (rounded to a multiple of 16)
    val localContext = LocalContext.current
    val ratio = remember(localContext) { RecordSettings.getScreenAspect(localContext) }
    val fhdHeight = remember(ratio) { ((1080 * ratio) / 16.0).roundToInt() * 16 }
    val hdHeight = remember(ratio) { ((720 * ratio) / 16.0).roundToInt() * 16 }
    val sdHeight = remember(ratio) { ((480 * ratio) / 16.0).roundToInt() * 16 }

    Box(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        SettingsCard {
            SectionHeader(Icons.Default.AspectRatio, "Resolution")

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionChip(
                    label = "1080p",
                    width = 1080,
                    height = fhdHeight,
                    isSelected = !showCustomResolution && widthInput == "1080" && heightInput == fhdHeight.toString(),
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                ResolutionChip(
                    label = "720p",
                    width = 720,
                    height = hdHeight,
                    isSelected = !showCustomResolution && widthInput == "720" && heightInput == hdHeight.toString(),
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                ResolutionChip(
                    label = "480p",
                    width = 480,
                    height = sdHeight,
                    isSelected = !showCustomResolution && widthInput == "480" && heightInput == sdHeight.toString(),
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                FilterChip(
                    selected = showCustomResolution,
                    enabled = enabled,
                    onClick = onCustomToggle,
                    label = { Text("Custom") }
                )
            }

            AnimatedVisibility(visible = showCustomResolution) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = widthInput,
                        enabled = enabled,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) onWidthChange(it)
                        },
                        label = { Text("Width") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_resolution_width")
                    )
                    OutlinedTextField(
                        value = heightInput,
                        enabled = enabled,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) onHeightChange(it)
                        },
                        label = { Text("Height") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_resolution_height")
                    )
                }
            }
        }
    }
}

@Composable
fun ResolutionChip(
    label: String,
    width: Int,
    height: Int,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: (Int, Int) -> Unit
) {
    FilterChip(
        selected = isSelected,
        enabled = enabled,
        onClick = { onSelect(width, height) },
        label = { Text(label) }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FpsSection(
    fpsInput: String,
    enabled: Boolean = true,
    onFpsChange: (String) -> Unit,
    showCustomFps: Boolean,
    onPresetSelected: (Int) -> Unit,
    onCustomToggle: () -> Unit
) {
    Box(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        SettingsCard {
            SectionHeader(Icons.Default.Speed, "Frame Rate")

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FpsChip(fps = 60, isSelected = !showCustomFps && fpsInput == "60", enabled = enabled, onSelect = onPresetSelected)
                FpsChip(fps = 30, isSelected = !showCustomFps && fpsInput == "30", enabled = enabled, onSelect = onPresetSelected)
                FpsChip(fps = 24, isSelected = !showCustomFps && fpsInput == "24", enabled = enabled, onSelect = onPresetSelected)
                FilterChip(
                    selected = showCustomFps,
                    enabled = enabled,
                    onClick = onCustomToggle,
                    label = { Text("Custom") }
                )
            }

            AnimatedVisibility(visible = showCustomFps) {
                OutlinedTextField(
                    value = fpsInput,
                    enabled = enabled,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() }) onFpsChange(it)
                    },
                    label = { Text("Frame Rate") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_fps")
                )
            }
        }
    }
}

@Composable
fun FpsChip(
    fps: Int,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: (Int) -> Unit
) {
    FilterChip(
        selected = isSelected,
        enabled = enabled,
        onClick = { onSelect(fps) },
        label = { Text("$fps FPS") }
    )
}

@Composable
fun EncodingSection(
    selectedEncoding: String,
    enabled: Boolean = true,
    onEncodingSelected: (String) -> Unit,
    showDropdown: Boolean,
    onToggleDropdown: (Boolean) -> Unit
) {
    val localContext = LocalContext.current
    val codecsList = listOf(
        CodecSpec("H.264 / AVC", "HW", "HW", "Highly compatible, hardware accelerated.", true),
        CodecSpec("H.265 / HEVC", "HW", "HW", "Modern efficient format, smaller file.", true),
        CodecSpec("VP9", "HW", "SW", "High compression, software encoded.", true),
        CodecSpec("VP8", "SW", "SW", "WebM format target, lightweight.", true),
        CodecSpec("AV1", "SW", "SW", "Next-gen compression, software based.", true),
        CodecSpec("MPEG-4 Visual", "SW", "SW", "Legacy video standard, software.", true),
        CodecSpec("H.263", "SW", "SW", "Vintage 3GP standard, software.", true),
        CodecSpec("WMV", "SW", "—", "Windows Media Video decoder only.", false)
    )

    Box(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        SettingsCard {
            SectionHeader(Icons.Default.CompassCalibration, "Video Encoding")

            // Selector dropdown trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onToggleDropdown(true) }
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
                    .testTag("codec_dropdown"),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selectedEncoding,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val match = codecsList.firstOrNull { it.name == selectedEncoding }
                        if (match != null) {
                            Text(
                                "Decode: ${match.decode} · Encode: ${match.encode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { onToggleDropdown(false) },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    codecsList.forEach { spec ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            spec.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = if (spec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CodecBadge("D:${spec.decode}", spec.decode)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        CodecBadge("E:${spec.encode}", spec.encode)
                                    }
                                    Text(
                                        spec.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            onClick = {
                                if (spec.enabled) {
                                    onEncodingSelected(spec.name)
                                } else {
                                    Toast.makeText(localContext, "WMV encoder is unsupported (Encode —)", Toast.LENGTH_SHORT).show()
                                }
                                onToggleDropdown(false)
                            },
                            enabled = spec.enabled
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodecBadge(text: String, level: String) {
    val container = when (level) {
        "HW" -> MaterialTheme.colorScheme.primaryContainer
        "—" -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (level) {
        "HW" -> MaterialTheme.colorScheme.onPrimaryContainer
        "—" -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Badge(containerColor = container, contentColor = content) {
        Text(text)
    }
}

data class CodecSpec(
    val name: String,
    val decode: String,
    val encode: String,
    val description: String,
    val enabled: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioSection(
    audioSource: String,
    onAudioSourceChange: (String) -> Unit,
    mergeAudioVideo: Boolean,
    onMergeAudioVideoChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Box(modifier = Modifier.alpha(if (enabled) 1f else 0.5f)) {
        SettingsCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (audioSource != "None") Icons.Default.Mic else Icons.AutoMirrored.Filled.VolumeMute,
                    contentDescription = null,
                    tint = if (audioSource != "None") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Audio Configuration",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                "Select where audio should be captured from during screen recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Audio source chips
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("None", "Mic", "Internal", "Both").forEach { source ->
                    val label = when (source) {
                        "None" -> "Mute"
                        "Mic" -> "Microphone"
                        "Internal" -> "Device Audio"
                        "Both" -> "Mic & Device"
                        else -> source
                    }
                    FilterChip(
                        selected = audioSource == source,
                        enabled = enabled,
                        onClick = { onAudioSourceChange(source) },
                        label = { Text(label) },
                        modifier = Modifier.testTag("audio_source_chip_$source")
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Merge switch row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Merge Audio and Video",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Integrate audio tracks automatically, or keep separate files.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = mergeAudioVideo,
                    enabled = enabled,
                    onCheckedChange = onMergeAudioVideoChange,
                    modifier = Modifier.testTag("audio_merge_switch")
                )
            }
        }
    }
}

@Composable
fun HistorySection(
    recordings: List<RecordingEntity>,
    onDelete: (RecordingEntity) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recording Logs",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (recordings.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (recordings.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyStateCard()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recordings, key = { it.id }) { recording ->
                        RecordingHistoryItem(recording = recording, onDelete = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(56.dp)
        )
        Text(
            "No Recordings Yet",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Tap record to capture your screen. Saved clips appear here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
fun RecordingHistoryItem(
    recording: RecordingEntity,
    onDelete: (RecordingEntity) -> Unit
) {
    val localContext = LocalContext.current
    val formattedDate = remember(recording.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(recording.timestamp))
    }
    val fileSizeFormatted = remember(recording.fileSize) {
        if (recording.fileSize < 1024 * 1024) {
            "${DecimalFormat("#.##").format(recording.fileSize / 1024.0)} KB"
        } else {
            "${DecimalFormat("#.##").format(recording.fileSize / (1024.0 * 1024.0))} MB"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("recording_${recording.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    recording.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$formattedDate · $fileSizeFormatted · ${recording.resolution} · ${recording.fps} FPS · ${recording.encoder}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Play internal video player
                IconButton(
                    onClick = {
                        val file = File(recording.filePath)
                        if (file.exists()) {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(
                                    localContext,
                                    "${localContext.packageName}.fileprovider",
                                    file
                                )
                                val playIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "video/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                localContext.startActivity(playIntent)
                            } catch (e: Exception) {
                                Toast.makeText(localContext, "No video player located to stream this file.", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(localContext, "Recorded source file expired or was deleted.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = "Play")
                }

                // Share internal file
                IconButton(
                    onClick = {
                        val file = File(recording.filePath)
                        if (file.exists()) {
                            try {
                                val uri: Uri = FileProvider.getUriForFile(
                                    localContext,
                                    "${localContext.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                localContext.startActivity(Intent.createChooser(shareIntent, "Share Screen Capture"))
                            } catch (e: Exception) {
                                Toast.makeText(localContext, "Share channel failure: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(localContext, "File does not exist.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }

                // Delete Button
                IconButton(
                    onClick = { onDelete(recording) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

private fun formatSecs(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}
