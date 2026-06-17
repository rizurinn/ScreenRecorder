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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
                        .clickable { 
                            // Tapping outside closes the dialog activity
                            finish() 
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clickable(enabled = false) {}, // Prevent closing when tapping inside card
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Quick Record Controls",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )

                            if (isRecording) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "Settings locked. Recording in progress...",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }

                            // Choices layout
                            var selectedResolution by remember { mutableStateOf(if (settings.resolutionWidth == 1080) "1080p" else if (settings.resolutionWidth == 720) "720p" else "480p") }
                            var selectedFps by remember { mutableStateOf(settings.fps) }
                            var selectedAudioSource by remember { mutableStateOf(settings.audioSource) }
                            var mergeAudioVideo by remember { mutableStateOf(settings.mergeAudioVideo) }

                            // 1. Quality Row Selection
                            LabeledSegmentRow(
                                label = "Resolution",
                                options = listOf("1080p", "720p", "480p"),
                                selected = selectedResolution,
                                enabled = !isRecording,
                                onSelect = { selectedResolution = it }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 2. FPS Row Selection
                            LabeledSegmentRow(
                                label = "Frame Rate",
                                options = listOf("30 FPS", "60 FPS"),
                                selected = "${selectedFps} FPS",
                                enabled = !isRecording,
                                onSelect = { 
                                    selectedFps = if (it == "30 FPS") 30 else 60
                                }
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 3. Audio Source Options
                            LabeledSegmentRow(
                                label = "Audio Source",
                                options = listOf("None", "Mic", "Internal", "Both"),
                                selected = selectedAudioSource,
                                enabled = !isRecording,
                                onSelect = { selectedAudioSource = it }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // 4. Split / Merge Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .alpha(if (!isRecording) 1f else 0.65f)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Merge Audio and Video",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Merge tracks into a single MP4, or keep split",
                                        fontSize = 11.sp,
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

                            Spacer(modifier = Modifier.height(24.dp))

                            // Actions
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = { finish() },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Cancel")
                                }
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
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        Text("Stop Recording")
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            // Save configurations
                                            val resolutionW = if (selectedResolution == "1080p") 1080 else if (selectedResolution == "720p") 720 else 480
                                            val resolutionH = if (selectedResolution == "1080p") 1920 else if (selectedResolution == "720p") 1280 else 854
                                            val bitRate = if (selectedResolution == "1080p") 8000000 else if (selectedResolution == "720p") 4000000 else 2000000
                                            
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

                                            // Initiate permissions and capture
                                            checkPermissionsAndProceed()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text("Start Recording")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun LabeledSegmentRow(
        label: String,
        options: List<String>,
        selected: String,
        enabled: Boolean = true,
        onSelect: (String) -> Unit
    ) {
        Column(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.65f)) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                options.forEach { option ->
                    val isSelected = option == selected
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .clickable(enabled = enabled) { onSelect(option) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = option,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    private fun checkPermissionsAndProceed() {
        val permissions = mutableListOf<String>()

        // RECORD_AUDIO is only requested if we are recording audio or proactively
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
