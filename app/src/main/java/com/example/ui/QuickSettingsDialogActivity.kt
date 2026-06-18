package com.example.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.RecordSettings
import com.example.service.ScreenRecordService
import com.example.ui.theme.MyApplicationTheme

class QuickSettingsDialogActivity : ComponentActivity() {

    private lateinit var settings: RecordSettings

    // MediaProjection capture launcher
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenRecordingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen record permission denied", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Standard runtime permissions launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (notificationGranted) {
            requestScreenCapture()
        } else {
            Toast.makeText(this, "Notification permission is required to start recording", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Load settings
        settings = RecordSettings.load(this)

        setContent {
            MyApplicationTheme {
                val recordingState by ScreenRecordService.recordingState.collectAsState()
                val isRecording = recordingState != ScreenRecordService.RecordingState.IDLE

                var selectedResolution by remember {
                    mutableStateOf(
                        when (settings.resolutionWidth) {
                            1080 -> "1080p"
                            720 -> "720p"
                            else -> "480p"
                        }
                    )
                }
                var selectedFps by remember { mutableStateOf(settings.fps) }
                var selectedAudioSource by remember { mutableStateOf(settings.audioSource) }
                var mergeAudioVideo by remember { mutableStateOf(settings.mergeAudioVideo) }

                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Quick Record Controls") },
                    text = {
                        Column {
                            if (isRecording) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
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
                                            text = "Settings locked. Recording in progress…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            SegmentGroup(
                                label = "Resolution",
                                options = listOf("1080p", "720p", "480p"),
                                selected = selectedResolution,
                                enabled = !isRecording,
                                onSelect = { selectedResolution = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            SegmentGroup(
                                label = "Frame Rate",
                                options = listOf("30 FPS", "60 FPS"),
                                selected = "$selectedFps FPS",
                                enabled = !isRecording,
                                onSelect = { selectedFps = if (it == "30 FPS") 30 else 60 }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            SegmentGroup(
                                label = "Audio Source",
                                options = listOf("None", "Mic", "Internal", "Both"),
                                selected = selectedAudioSource,
                                enabled = !isRecording,
                                onSelect = { selectedAudioSource = it }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Merge Audio and Video",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Merge tracks into a single MP4, or keep split",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = mergeAudioVideo,
                                    enabled = !isRecording,
                                    onCheckedChange = { mergeAudioVideo = it },
                                    modifier = Modifier.testTag("qs_merge_switch")
                                )
                            }
                        }
                    },
                    confirmButton = {
                        if (isRecording) {
                            Button(
                                onClick = {
                                    val stopIntent = Intent(this@QuickSettingsDialogActivity, ScreenRecordService::class.java).apply {
                                        action = ScreenRecordService.ACTION_STOP
                                    }
                                    startService(stopIntent)
                                    finish()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            ) {
                                Text("Stop Recording")
                            }
                        } else {
                            Button(
                                onClick = {
                                    val resolutionW = when (selectedResolution) {
                                        "1080p" -> 1080
                                        "720p" -> 720
                                        else -> 480
                                    }
                                    val resolutionH = when (selectedResolution) {
                                        "1080p" -> 1920
                                        "720p" -> 1280
                                        else -> 854
                                    }
                                    val bitRate = when (selectedResolution) {
                                        "1080p" -> 8000000
                                        "720p" -> 4000000
                                        else -> 2000000
                                    }

                                    settings = RecordSettings(
                                        resolutionWidth = resolutionW,
                                        resolutionHeight = resolutionH,
                                        fps = selectedFps,
                                        audioSource = selectedAudioSource,
                                        recordAudio = selectedAudioSource != "None",
                                        mergeAudioVideo = mergeAudioVideo,
                                        bitRate = bitRate,
                                        videoEncoding = settings.videoEncoding
                                    )
                                    RecordSettings.save(this@QuickSettingsDialogActivity, settings)

                                    checkPermissionsAndProceed()
                                }
                            ) {
                                Text("Start Recording")
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { finish() }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SegmentGroup(
        label: String,
        options: List<String>,
        selected: String,
        enabled: Boolean = true,
        onSelect: (String) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == selected,
                        enabled = enabled,
                        onClick = { onSelect(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(option)
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        val permissions = mutableListOf<String>()

        // RECORD_AUDIO is only requested if we are recording audio
        if (settings.audioSource != "None" && ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = manager.createScreenCaptureIntent()
        projectionLauncher.launch(intent)
    }

    private fun startScreenRecordingService(resultCode: Int, resultData: Intent) {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenRecordService.EXTRA_WIDTH, settings.resolutionWidth)
            putExtra(ScreenRecordService.EXTRA_HEIGHT, settings.resolutionHeight)
            putExtra(ScreenRecordService.EXTRA_FPS, settings.fps)
            putExtra(ScreenRecordService.EXTRA_ENCODER, settings.videoEncoding)
            putExtra(ScreenRecordService.EXTRA_BITRATE, settings.bitRate)
            putExtra(ScreenRecordService.EXTRA_RECORD_AUDIO, settings.recordAudio)
            putExtra(ScreenRecordService.EXTRA_AUDIO_SOURCE, settings.audioSource)
            putExtra(ScreenRecordService.EXTRA_MERGE_AUDIO_VIDEO, settings.mergeAudioVideo)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        finish()
    }
}
