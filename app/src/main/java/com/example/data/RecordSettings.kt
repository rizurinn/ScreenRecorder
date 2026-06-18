package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.view.WindowManager
import android.os.Build
import kotlin.math.roundToInt

data class RecordSettings(
    val resolutionWidth: Int = 1080,
    val resolutionHeight: Int = 1920,
    val fps: Int = 30,
    val videoEncoding: String = "H.264 / AVC",
    val recordAudio: Boolean = false,
    val bitRate: Int = 8000000, // 8 Mbps default
    val audioSource: String = "None", // "None", "Mic", "Internal", "Both"
    val mergeAudioVideo: Boolean = true
) {
    companion object {
        private const val PREF_NAME = "screen_recorder_prefs"
        private const val KEY_WIDTH = "resolution_width"
        private const val KEY_HEIGHT = "resolution_height"
        private const val KEY_FPS = "fps"
        private const val KEY_ENCODING = "video_encoding"
        private const val KEY_RECORD_AUDIO = "record_audio"
        private const val KEY_BITRATE = "bitrate"
        private const val KEY_AUDIO_SOURCE = "audio_source"
        private const val KEY_MERGE_AUDIO_VIDEO = "merge_audio_video"

        fun getScreenAspect(context: Context): Double {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 16.0 / 9.0
            val rawWidth: Int
            val rawHeight: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val metrics = wm.currentWindowMetrics
                val bounds = metrics.bounds
                rawWidth = bounds.width()
                rawHeight = bounds.height()
            } else {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(metrics)
                rawWidth = metrics.widthPixels
                rawHeight = metrics.heightPixels
            }
            if (rawWidth <= 0 || rawHeight <= 0) return 16.0 / 9.0
            val pWidth = minOf(rawWidth, rawHeight)
            val pHeight = maxOf(rawWidth, rawHeight)
            return pHeight.toDouble() / pWidth.toDouble()
        }

        fun load(context: Context): RecordSettings {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val recordAudioLoaded = prefs.getBoolean(KEY_RECORD_AUDIO, false)
            val defaultAudioSource = if (recordAudioLoaded) "Mic" else "None"
            
            val ratio = getScreenAspect(context)
            val defaultHeight = ((1080 * ratio) / 16.0).roundToInt() * 16

            return RecordSettings(
                resolutionWidth = prefs.getInt(KEY_WIDTH, 1080),
                resolutionHeight = prefs.getInt(KEY_HEIGHT, defaultHeight),
                fps = prefs.getInt(KEY_FPS, 30),
                videoEncoding = prefs.getString(KEY_ENCODING, "H.264 / AVC") ?: "H.264 / AVC",
                recordAudio = recordAudioLoaded,
                bitRate = prefs.getInt(KEY_BITRATE, 8000000),
                audioSource = prefs.getString(KEY_AUDIO_SOURCE, defaultAudioSource) ?: defaultAudioSource,
                mergeAudioVideo = prefs.getBoolean(KEY_MERGE_AUDIO_VIDEO, true)
            )
        }

        fun save(context: Context, settings: RecordSettings) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_WIDTH, settings.resolutionWidth)
                .putInt(KEY_HEIGHT, settings.resolutionHeight)
                .putInt(KEY_FPS, settings.fps)
                .putString(KEY_ENCODING, settings.videoEncoding)
                .putBoolean(KEY_RECORD_AUDIO, settings.recordAudio)
                .putInt(KEY_BITRATE, settings.bitRate)
                .putString(KEY_AUDIO_SOURCE, settings.audioSource)
                .putBoolean(KEY_MERGE_AUDIO_VIDEO, settings.mergeAudioVideo)
                .apply()
        }
    }
}
