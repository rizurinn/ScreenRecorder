package com.example

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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.service.ScreenRecordService
import com.example.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.ScreenRecordViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScreenRecordViewModel by viewModels()

    // Activity result launcher for MediaProjection permission
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startScreenRecording(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Screen Recording Permission Denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Permission launcher for standard runtime permissions
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
            requestProjectionPermission()
        } else {
            Toast.makeText(this, "Notification permission is required to run the recording channel", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        viewModel = viewModel,
                        onRequestRecord = { checkPermissionsAndRecord() },
                        onStopRecord = { stopScreenRecording() },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        // Check if triggered from Quick Settings Custom Tile
        if (intent?.getBooleanExtra("LAUNCH_RECORD_FROM_TILE", false) == true) {
            checkPermissionsAndRecord()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("LAUNCH_RECORD_FROM_TILE", false)) {
            checkPermissionsAndRecord()
        }
    }

    private fun checkPermissionsAndRecord() {
        val permissionsToRequest = mutableListOf<String>()

        // RECORD_AUDIO is requested if audio enabled, or proactively to avoid mid-recording request failure
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }

        // Post Notification permission added in Android 13/Tiramisu is critical for foreground notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // On Android 9 and lower (API <= 28), we need WRITE_EXTERNAL_STORAGE to write to public Movies directory
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            requestProjectionPermission()
        }
    }

    private fun requestProjectionPermission() {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        projectionLauncher.launch(captureIntent)
    }

    private fun startScreenRecording(resultCode: Int, resultData: Intent) {
        val currentSettings = viewModel.settings.value
        
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenRecordService.EXTRA_RESULT_DATA, resultData)
            putExtra(ScreenRecordService.EXTRA_WIDTH, currentSettings.resolutionWidth)
            putExtra(ScreenRecordService.EXTRA_HEIGHT, currentSettings.resolutionHeight)
            putExtra(ScreenRecordService.EXTRA_FPS, currentSettings.fps)
            putExtra(ScreenRecordService.EXTRA_ENCODER, currentSettings.videoEncoding)
            putExtra(ScreenRecordService.EXTRA_BITRATE, currentSettings.bitRate)
            putExtra(ScreenRecordService.EXTRA_RECORD_AUDIO, currentSettings.recordAudio)
            putExtra(ScreenRecordService.EXTRA_AUDIO_SOURCE, currentSettings.audioSource)
            putExtra(ScreenRecordService.EXTRA_MERGE_AUDIO_VIDEO, currentSettings.mergeAudioVideo)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopScreenRecording() {
        val serviceIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        startService(serviceIntent)
    }
}
