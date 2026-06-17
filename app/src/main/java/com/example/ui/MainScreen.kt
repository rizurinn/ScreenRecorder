package com.example.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.RecordingEntity
import com.example.data.RecordSettings
import com.example.service.ScreenRecordService
import com.example.viewmodel.ScreenRecordViewModel
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
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

    // Temporary variables for inputs
    var showCustomResolution by remember { mutableStateOf(false) }
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
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "App Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Screen Recorder",
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                if (!settingsEnabled) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth().animateContentSize(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                            ),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Lock,
                                                    contentDescription = "Locked",
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                                Text(
                                                    text = "Settings locked while recording is in progress.",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
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
                                        recordAudio = settings.recordAudio,
                                        enabled = settingsEnabled,
                                        onRecordAudioChange = {
                                            viewModel.updateSettings(settings.copy(recordAudio = it))
                                        }
                                    )
                                }
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
                            fontWeight = FontWeight.Bold,
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
                        .padding(16.dp),
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
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    if (!settingsEnabled) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().animateContentSize(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Locked",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                    Text(
                                        text = "Settings locked while recording is in progress.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
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
                            recordAudio = settings.recordAudio,
                            enabled = settingsEnabled,
                            onRecordAudioChange = {
                                viewModel.updateSettings(settings.copy(recordAudio = it))
                            }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Recording Logs",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            if (recordings.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllRecordings() },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all logs")
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
fun RecordingControllerCard(
    recordingState: ScreenRecordService.RecordingState,
    durationSeconds: Int,
    onRequestRecord: () -> Unit,
    onStopRecord: () -> Unit,
    recordSettings: RecordSettings,
    localContext: Context
) {
    val isRecording = recordingState != ScreenRecordService.RecordingState.IDLE
    val isPaused = recordingState == ScreenRecordService.RecordingState.PAUSED

    // Pulse animation for recording indicators
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
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
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
                // Active recording state visualizer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp)
                        )
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 1.sp
                    )
                }

                Text(
                    text = formatSecs(durationSeconds),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "${recordSettings.resolutionWidth}x${recordSettings.resolutionHeight} @ ${recordSettings.fps} FPS · ${recordSettings.videoEncoding}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    // Pause/Resume Button
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        Button(
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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .height(56.dp)
                                .weight(1f)
                                .testTag("pause_resume_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (isPaused) "Resume" else "Pause"
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
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .weight(1.2f)
                            .testTag("stop_record_button")
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop Recording")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Stop Record")
                    }
                }
            } else {
                // Ready to record state visualizer
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            shape = CircleShape
                        )
                        .clickable(onClick = onRequestRecord)
                        .padding(4.dp)
                        .testTag("start_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .background(MaterialTheme.colorScheme.surface, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RadioButtonChecked,
                            contentDescription = "Trigger record",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Text(
                    "Ready to Record",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    "Tap circle to launch screen projection authorization. Keep settings below lightweight and matching your system specs for high performance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
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
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AspectRatio,
                    contentDescription = "Resolution icon",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Resolution Preset",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ResolutionChip(
                    label = "1080p (FHD)",
                    width = 1080,
                    height = 1920,
                    isSelected = !showCustomResolution && widthInput == "1080" && heightInput == "1920",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                ResolutionChip(
                    label = "720p (HD)",
                    width = 720,
                    height = 1280,
                    isSelected = !showCustomResolution && widthInput == "720" && heightInput == "1280",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                ResolutionChip(
                    label = "480p (SD)",
                    width = 480,
                    height = 854,
                    isSelected = !showCustomResolution && widthInput == "480" && heightInput == "854",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                FilterChip(
                    selected = showCustomResolution,
                    enabled = enabled,
                    onClick = onCustomToggle,
                    label = { Text("Custom...") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_resolution_width"),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = heightInput,
                        enabled = enabled,
                        onValueChange = {
                            if (it.all { char -> char.isDigit() }) onHeightChange(it)
                        },
                        label = { Text("Height") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_resolution_height"),
                        shape = RoundedCornerShape(12.dp)
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
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
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
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "FPS icon",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Frame Rate (FPS)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FpsChip(
                    fps = 60,
                    isSelected = !showCustomFps && fpsInput == "60",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                FpsChip(
                    fps = 30,
                    isSelected = !showCustomFps && fpsInput == "30",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                FpsChip(
                    fps = 24,
                    isSelected = !showCustomFps && fpsInput == "24",
                    enabled = enabled,
                    onSelect = onPresetSelected
                )
                FilterChip(
                    selected = showCustomFps,
                    enabled = enabled,
                    onClick = onCustomToggle,
                    label = { Text("Custom...") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_fps"),
                    shape = RoundedCornerShape(12.dp)
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
        label = { Text("$fps FPS") },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
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

    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CompassCalibration,
                    contentDescription = "Encoding Icon",
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    "Video Encoding Specification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
            }

            // Custom selector dropdown trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onToggleDropdown(true) }
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = if (enabled) 0.5f else 0.2f),
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
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val match = codecsList.firstOrNull { it.name == selectedEncoding }
                        if (match != null) {
                            Text(
                                "Decode: ${match.decode} · Encode: ${match.encode}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Dropdown")
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
                                            fontWeight = FontWeight.Bold,
                                            color = if (spec.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Badge(
                                            containerColor = if (spec.decode == "HW") Color(0xFF4CAF50) else Color(0xFFFF9800),
                                            contentColor = Color.White
                                        ) {
                                            Text("D:${spec.decode}")
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Badge(
                                            containerColor = when (spec.encode) {
                                                "HW" -> Color(0xFF4CAF50)
                                                "—" -> Color(0xFFF44336)
                                                else -> Color(0xFFFF9800)
                                            },
                                            contentColor = Color.White
                                        ) {
                                            Text("E:${spec.encode}")
                                        }
                                    }
                                    Text(
                                        spec.description,
                                        fontSize = 11.sp,
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

data class CodecSpec(
    val name: String,
    val decode: String,
    val encode: String,
    val description: String,
    val enabled: Boolean
)

@Composable
fun AudioSection(
    recordAudio: Boolean,
    enabled: Boolean = true,
    onRecordAudioChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.65f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (recordAudio) Icons.Default.Mic else Icons.AutoMirrored.Filled.VolumeMute,
                contentDescription = "Audio option icon",
                tint = if (recordAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Include Microphone Audio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    "Simultaneously captures user microphone voice while recording.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Switch(
                checked = recordAudio,
                enabled = enabled,
                onCheckedChange = onRecordAudioChange,
                modifier = Modifier.testTag("audio_switch")
            )
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recording Logs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                if (recordings.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all archives")
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
            contentDescription = "Empty History",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
        )
        Text(
            "No Recordings Yet",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "Push record to save capture streams safely into Room Database logs.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "$formattedDate · $fileSizeFormatted · ${recording.resolution} · ${recording.fps} FPS · ${recording.encoder}",
                    fontSize = 11.sp,
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
                    Icon(Icons.Default.PlayCircle, contentDescription = "Stream playback")
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
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share capture file")
                }

                // Delete Button
                IconButton(
                    onClick = { onDelete(recording) },
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Wipe logs")
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
