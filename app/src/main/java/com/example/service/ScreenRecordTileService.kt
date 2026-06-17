package com.example.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity

class ScreenRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = ScreenRecordService.recordingState.value != ScreenRecordService.RecordingState.IDLE

        if (isRecording) {
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            val launchIntent = Intent(this, com.example.ui.QuickSettingsDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    99,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(launchIntent)
            }
        }
        updateTileState()
    }

    private fun updateTileState() {
        val tile = qsTile ?: return
        val isRecording = ScreenRecordService.recordingState.value != ScreenRecordService.RecordingState.IDLE
        
        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = "Stop Rec"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Recording..."
            }
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Screen Rec"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = "Tap to record"
            }
        }
        tile.updateTile()
    }
}
