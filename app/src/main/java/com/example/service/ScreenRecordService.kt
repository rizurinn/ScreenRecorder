package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.data.RecordSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import java.io.File

class ScreenRecordService : Service() {

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var isRecordingInternal = false

    private var currentFile: File? = null
    private var currentWidth = 1080
    private var currentHeight = 1920
    private var currentFps = 30
    private var currentEncoder = "H.264 / AVC"
    private var currentBitrate = 8000000
    private var currentRecordAudio = false
    private var currentAudioSource = "None"
    private var currentMergeAudioVideo = true
    private var startTimeMillis = 0L

    private var isAudioRecording = false
    private var audioRecordMic: android.media.AudioRecord? = null
    private var audioRecordInternal: android.media.AudioRecord? = null
    private var audioFile: File? = null
    private var audioThread: Thread? = null

    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "ScreenRecordService"
        private const val NOTIFICATION_ID = 1337
        private const val CHANNEL_ID = "screen_record_service_channel"

        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val ACTION_PAUSE = "com.example.service.ACTION_PAUSE"
        const val ACTION_RESUME = "com.example.service.ACTION_RESUME"

        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_WIDTH = "EXTRA_WIDTH"
        const val EXTRA_HEIGHT = "EXTRA_HEIGHT"
        const val EXTRA_FPS = "EXTRA_FPS"
        const val EXTRA_ENCODER = "EXTRA_ENCODER"
        const val EXTRA_BITRATE = "EXTRA_BITRATE"
        const val EXTRA_RECORD_AUDIO = "EXTRA_RECORD_AUDIO"
        const val EXTRA_AUDIO_SOURCE = "EXTRA_AUDIO_SOURCE"
        const val EXTRA_MERGE_AUDIO_VIDEO = "EXTRA_MERGE_AUDIO_VIDEO"

        private val _recordingState = MutableStateFlow(RecordingState.IDLE)
        val recordingState: StateFlow<RecordingState> = _recordingState

        private val _durationSeconds = MutableStateFlow(0)
        val durationSeconds: StateFlow<Int> = _durationSeconds

        private val _errorFlow = MutableStateFlow<String?>(null)
        val errorFlow: StateFlow<String?> = _errorFlow
    }

    enum class RecordingState {
        IDLE,
        RECORDING,
        PAUSED
    }

    override fun onCreate() {
        super.onCreate()
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        when (action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                currentWidth = intent.getIntExtra(EXTRA_WIDTH, 1080)
                currentHeight = intent.getIntExtra(EXTRA_HEIGHT, 1920)
                currentFps = intent.getIntExtra(EXTRA_FPS, 30)
                currentEncoder = intent.getStringExtra(EXTRA_ENCODER) ?: "H.264 / AVC"
                currentBitrate = intent.getIntExtra(EXTRA_BITRATE, 8000000)
                currentRecordAudio = intent.getBooleanExtra(EXTRA_RECORD_AUDIO, false)
                currentAudioSource = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: "None"
                currentMergeAudioVideo = intent.getBooleanExtra(EXTRA_MERGE_AUDIO_VIDEO, true)

                if (resultCode != 0 && resultData != null) {
                    startRecording(resultCode, resultData)
                } else {
                    Log.e(TAG, "Invalid result data or code for screen projection")
                    _errorFlow.value = "Failed to obtain screen capture permission."
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
            ACTION_PAUSE -> {
                pauseRecording()
            }
            ACTION_RESUME -> {
                resumeRecording()
            }
        }

        return START_STICKY
    }

    private fun startRecording(resultCode: Int, resultData: Intent) {
        _errorFlow.value = null
        val notification = createNotification("Preparing screen recording...")
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service as foreground", e)
            _errorFlow.value = "Foreground service permission required."
            _recordingState.value = RecordingState.IDLE
            stopSelf()
            return
        }

        try {
            setupMediaRecorder()
            
            val pm = mediaProjectionManager ?: throw IllegalStateException("Projection Manager null")
            val projection = pm.getMediaProjection(resultCode, resultData)
            mediaProjection = projection

            if (projection == null) {
                throw IllegalStateException("MediaProjection authorization returned null.")
            }

            // Register callback BEFORE starting capture/virtualDisplay (Mandatory starting on Android 14)
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped via system")
                    stopRecording()
                    stopSelf()
                }
            }
            mediaProjectionCallback = callback
            projection.registerCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))

            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            val densityDpi = metrics.densityDpi

            virtualDisplay = projection.createVirtualDisplay(
                "ScreenRecordServiceDisplay",
                currentWidth,
                currentHeight,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder?.surface,
                null,
                null
            )

            mediaRecorder?.start()
            isRecordingInternal = true
            startAudioRecording()
            startTimeMillis = System.currentTimeMillis()
            _recordingState.value = RecordingState.RECORDING
            
            startTimer()
            updateNotification("Recording screen...")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            _errorFlow.value = "Failed to launch screen recorder: ${e.localizedMessage ?: "Unknown hardware conflict"}"
            cleanup()
            stopSelf()
        }
    }

    private fun getScreenMetrics(): Pair<Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)
            Pair(metrics.widthPixels, metrics.heightPixels)
        }
    }

    private fun setupMediaRecorder() {
        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder = recorder

        val outputFormat = getMediaRecorderOutputFormat(currentEncoder)
        val videoEncoder = getMediaRecorderEncoder(currentEncoder)
        val extension = getFileExtension(currentEncoder)

        var baseDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES),
            "ScreenRecorder"
        )
        try {
            if (!baseDir.exists()) {
                baseDir.mkdirs()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create public ScreenRecorder directory, fallback to app private storage", e)
            baseDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
        }
        if (!baseDir.exists()) {
            baseDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES) ?: filesDir
        }
        
        val file = File(baseDir, "Record_${System.currentTimeMillis()}.$extension")
        currentFile = file

        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
        // If a separate audio source configuration is selected, the separate AudioRecord handles it.
        // Otherwise, fallback to the old standard mic option if currentRecordAudio is enabled.
        val useMediaRecorderAudio = (currentAudioSource == "None" && currentRecordAudio)
        if (useMediaRecorderAudio) {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        }

        recorder.setOutputFormat(outputFormat)

        // Make size and aspect ratio safe for emulators & various screens
        val (screenW, screenH) = getScreenMetrics()
        var w = currentWidth
        var h = currentHeight

        if (w <= 0 || h <= 0) {
            w = screenW
            h = screenH
        }

        // Cap to physical screen bounds or standard H.264 profiles to prevent encoder crash
        if (w > screenW || h > screenH) {
            Log.d(TAG, "Mismatched or too high resolution settings, capping ${w}x${h} to physical screen bounds: ${screenW}x${screenH}")
            w = screenW
            h = screenH
        }

        // Enforce multiples of 16 (strict constraint of many mobile/emulator H.264 & H.265 HW video encoders)
        if (w % 16 != 0) w = (w / 16) * 16
        if ( h % 16 != 0) h = (h / 16) * 16

        // Fallback to minimal multiples of 16 if calculation zeroed out
        if (w <= 0) w = 320
        if (h <= 0) h = 480

        currentWidth = w
        currentHeight = h

        recorder.setVideoSize(currentWidth, currentHeight)
        recorder.setVideoEncoder(videoEncoder)
        if (useMediaRecorderAudio) {
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        }
        recorder.setVideoEncodingBitRate(currentBitrate)
        recorder.setVideoFrameRate(currentFps)
        recorder.setOutputFile(file.absolutePath)

        try {
            recorder.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Primary MediaRecorder prepare failed with standard resolution $w x $h. Applying robust low-end fallback.", e)
            recorder.reset()
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (useMediaRecorderAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setOutputFormat(outputFormat)

            // Safe, universal fallback resolution (e.g., standard highly-supported 480p landscape/portrait based on aspect ratio)
            val fallbackW = if (screenW < 720) (screenW / 16) * 16 else 720
            var fallbackH = ((fallbackW * screenH) / screenW / 16) * 16
            if (fallbackH <= 0) fallbackH = 480
            
            currentWidth = fallbackW
            currentHeight = fallbackH

            recorder.setVideoSize(currentWidth, currentHeight)
            recorder.setVideoEncoder(videoEncoder)
            if (useMediaRecorderAudio) {
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            recorder.setVideoEncodingBitRate(2500000) // Lower target bitrate for 480p-720p fallback
            recorder.setVideoFrameRate(24) // highly stable fallback frame rate
            recorder.setOutputFile(file.absolutePath)
            recorder.prepare()
        }
    }

    private fun stopRecording() {
        if (!isRecordingInternal) return
        isRecordingInternal = false
        stopTimer()

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media recorder", e)
        }

        // Keep local references for post-processing and database saving
        val videoFile = currentFile
        val audioF = audioFile
        val audioSourceTemp = currentAudioSource
        val mergeTemp = currentMergeAudioVideo
        val duration = System.currentTimeMillis() - startTimeMillis
        val widthTemp = currentWidth
        val heightTemp = currentHeight
        val fpsTemp = currentFps
        val encoderTemp = currentEncoder

        // Stop separate audio thread
        isAudioRecording = false
        try {
            audioThread?.join(1500)
        } catch (e: Exception) {
            Log.e(TAG, "Error joining audio thread", e)
        }

        // Post-processing: run merge in background thread
        Thread({
            var finalAudioF = audioF
            if (videoFile != null && videoFile.exists() && audioF != null && audioF.exists() && audioSourceTemp != "None") {
                if (mergeTemp) {
                    try {
                        val aacFile = File(audioF.parent, audioF.nameWithoutExtension + ".aac")
                        val mergedFile = File(videoFile.parent, videoFile.nameWithoutExtension + "_merged." + videoFile.extension)
                        
                        // 1. Encode WAV to AAC
                        encodeWavToAac(audioF, aacFile)
                        
                        // 2. Merge Video and AAC
                        mergeVideoAndAudio(videoFile, aacFile, mergedFile)
                        
                        // 3. Swap files
                        if (mergedFile.exists() && mergedFile.length() > 0) {
                            videoFile.delete()
                            mergedFile.renameTo(videoFile)
                            Log.d(TAG, "Successfully merged audio and video track.")
                        }
                        
                        // 4. Delete temp audio files
                        audioF.delete()
                        if (aacFile.exists()) aacFile.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error merging audio and video tracks, keeping unmerged files", e)
                    }
                } else {
                    // Split mode: move WAV to the same directory as MP4 video for ease of access
                    try {
                        val publicAudioFile = File(videoFile.parent, videoFile.nameWithoutExtension + "_audio.wav")
                        if (audioF.renameTo(publicAudioFile)) {
                            finalAudioF = publicAudioFile
                            Log.d(TAG, "Split mode: audio written directly to ${publicAudioFile.absolutePath}")
                        } else {
                            Log.e(TAG, "Split mode: failed to rename audio to ${publicAudioFile.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error renaming audio file in split mode", e)
                    }
                }
            }

            saveRecordingToDatabaseBackground(
                videoFile,
                duration,
                widthTemp,
                heightTemp,
                fpsTemp,
                encoderTemp,
                audioSourceTemp,
                mergeTemp,
                finalAudioF
            )
        }, "ScreenRecordMergeThread").start()

        cleanup()
    }

    private fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && _recordingState.value == RecordingState.RECORDING) {
            try {
                mediaRecorder?.pause()
                _recordingState.value = RecordingState.PAUSED
                updateNotification("Recording paused")
                pauseTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pause recorder", e)
            }
        }
    }

    private fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && _recordingState.value == RecordingState.PAUSED) {
            try {
                mediaRecorder?.resume()
                _recordingState.value = RecordingState.RECORDING
                updateNotification("Recording screen...")
                resumeTimer()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to resume recorder", e)
            }
        }
    }

    private fun saveRecordingToDatabaseBackground(
        file: File?,
        duration: Long,
        width: Int,
        height: Int,
        fps: Int,
        encoder: String,
        audioSource: String,
        mergeAudioVideo: Boolean,
        audioF: File?
    ) {
        if (file == null || !file.exists() || file.length() == 0L) {
            Log.w(TAG, "Recording file empty or missing: ${file?.absolutePath}")
            return
        }

        val entity = RecordingEntity(
            filePath = file.absolutePath,
            fileName = file.name,
            durationMs = duration,
            resolution = "${width}x${height}",
            fps = fps,
            encoder = encoder,
            fileSize = file.length()
        )

        try {
            val db = AppDatabase.getDatabase(applicationContext)
            val repository = RecordingRepository(db.recordingDao())
            kotlinx.coroutines.runBlocking {
                repository.insert(entity)
            }
            Log.d(TAG, "Successfully saved video/audio recording to Room.")

            // Scan the video file so it immediately shows up in the user's Gallery
            android.media.MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(file.absolutePath),
                arrayOf("video/mp4")
            ) { path, uri ->
                Log.d(TAG, "Scanned video $path to MediaStore -> uri: $uri")
            }

            // Register companion audio file in Split mode
            if (audioSource != "None" && !mergeAudioVideo) {
                if (audioF != null && audioF.exists()) {
                    val audioEntity = RecordingEntity(
                        filePath = audioF.absolutePath,
                        fileName = audioF.name,
                        durationMs = duration,
                        resolution = "Audio Only",
                        fps = 0,
                        encoder = "WAV / PCM",
                        fileSize = audioF.length()
                    )
                    kotlinx.coroutines.runBlocking {
                        repository.insert(audioEntity)
                    }
                    Log.d(TAG, "Successfully saved companion audio recording to Room.")

                    // Scan companion audio file
                    android.media.MediaScannerConnection.scanFile(
                        applicationContext,
                        arrayOf(audioF.absolutePath),
                        arrayOf("audio/wav")
                    ) { path, uri ->
                        Log.d(TAG, "Scanned companion audio $path to MediaStore -> uri: $uri")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save recording to db", e)
        }
    }

    private fun startAudioRecording() {
        if (currentAudioSource == "None") return
        isAudioRecording = true
        val baseDir = getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC) ?: filesDir
        if (!baseDir.exists()) baseDir.mkdirs()
        audioFile = File(baseDir, "TempAudio_${System.currentTimeMillis()}.wav")

        val sampleRate = 44100
        val encoding = android.media.AudioFormat.ENCODING_PCM_16BIT
        val channelMask = android.media.AudioFormat.CHANNEL_IN_MONO
        val channels = 1

        val bufferSize = android.media.AudioRecord.getMinBufferSize(sampleRate, channelMask, encoding)

        audioThread = Thread({
            var fos: java.io.FileOutputStream? = null
            try {
                fos = java.io.FileOutputStream(audioFile)
                // Write placeholder header (44 bytes) to be overwritten later
                fos.write(ByteArray(44))

                // Configure and start sources
                val useMic = currentAudioSource == "Mic" || currentAudioSource == "Both"
                val useInternal = currentAudioSource == "Internal" || currentAudioSource == "Both"

                if (useMic) {
                    try {
                        // Use CAMCORDER as preferred source for clarity and preventing echo/volume cancellation
                        audioRecordMic = android.media.AudioRecord(
                            android.media.MediaRecorder.AudioSource.CAMCORDER,
                            sampleRate,
                            channelMask,
                            encoding,
                            bufferSize * 2
                        )
                        if (audioRecordMic?.state != android.media.AudioRecord.STATE_INITIALIZED) {
                            audioRecordMic = android.media.AudioRecord(
                                android.media.MediaRecorder.AudioSource.MIC,
                                sampleRate,
                                channelMask,
                                encoding,
                                bufferSize * 2
                            )
                        }
                        audioRecordMic?.startRecording()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start microphone recording", e)
                    }
                }

                if (useInternal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val mediaProjection = this.mediaProjection
                    if (mediaProjection != null) {
                        try {
                            val config = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_GAME)
                                .addMatchingUsage(android.media.AudioAttributes.USAGE_UNKNOWN)
                                .build()
                            audioRecordInternal = android.media.AudioRecord.Builder()
                                .setAudioPlaybackCaptureConfig(config)
                                .setAudioFormat(android.media.AudioFormat.Builder()
                                    .setEncoding(encoding)
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(channelMask)
                                    .build())
                                .setBufferSizeInBytes(bufferSize * 2)
                                .build()
                            audioRecordInternal?.startRecording()
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to start internal loopback recording", e)
                        }
                    } else {
                        Log.e(TAG, "MediaProjection is null, cannot record internal audio")
                    }
                }

                val buffer1 = ShortArray(bufferSize)
                val buffer2 = ShortArray(bufferSize)
                val byteBuffer = ByteArray(bufferSize * 2)
                var bytesWritten = 0L

                while (isAudioRecording) {
                    var readMicCount = 0
                    var readInternalCount = 0

                    if (audioRecordMic != null && useMic) {
                        readMicCount = audioRecordMic!!.read(buffer1, 0, buffer1.size)
                    }
                    if (audioRecordInternal != null && useInternal && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        readInternalCount = audioRecordInternal!!.read(buffer2, 0, buffer2.size)
                    }

                    val maxCount = Math.max(readMicCount, readInternalCount)
                    if (maxCount <= 0) {
                        Thread.sleep(10)
                        continue
                    }

                    // Mix buffers if both are used, otherwise copy/pass
                    for (i in 0 until maxCount) {
                        val micVal = if (i < readMicCount) buffer1[i].toInt() else 0
                        val intVal = if (i < readInternalCount) buffer2[i].toInt() else 0
                        // Mix safely avoiding digital clipping
                        val mixed = micVal + intVal
                        val finalShort = when {
                            mixed > Short.MAX_VALUE -> Short.MAX_VALUE
                            mixed < Short.MIN_VALUE -> Short.MIN_VALUE
                            else -> mixed.toShort()
                        }
                        // Write to byteBuffer
                        byteBuffer[i * 2] = (finalShort.toInt() and 0xff).toByte()
                        byteBuffer[i * 2 + 1] = ((finalShort.toInt() shr 8) and 0xff).toByte()
                    }

                    fos.write(byteBuffer, 0, maxCount * 2)
                    bytesWritten += maxCount * 2
                }

                // Update WAV header with actual data lengths
                fos.channel.position(0)
                writeWavHeader(
                    fos,
                    bytesWritten,
                    bytesWritten + 36,
                    sampleRate.toLong(),
                    channels,
                    (sampleRate * channels * 2).toLong()
                )

                Log.d(TAG, "Audio recording finished. Written $bytesWritten bytes of PCM.")
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio recording thread", e)
            } finally {
                try {
                    audioRecordMic?.stop()
                    audioRecordMic?.release()
                } catch (e: Exception) {}
                audioRecordMic = null

                try {
                    audioRecordInternal?.stop()
                    audioRecordInternal?.release()
                } catch (e: Exception) {}
                audioRecordInternal = null

                try {
                    fos?.close()
                } catch (e: Exception) {}
            }
        }, "ScreenRecordAudioThread")
        audioThread?.start()
    }

    private fun writeWavHeader(outputStream: java.io.FileOutputStream, totalAudioLen: Long, totalDataLen: Long, longSampleRate: Long, channels: Int, byteRate: Long) {
        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xffL).toByte()
        header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
        header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
        header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xffL).toByte()
        header[25] = ((longSampleRate shr 8) and 0xffL).toByte()
        header[26] = ((longSampleRate shr 16) and 0xffL).toByte()
        header[27] = ((longSampleRate shr 24) and 0xffL).toByte()
        header[28] = (byteRate and 0xffL).toByte()
        header[29] = ((byteRate shr 8) and 0xffL).toByte()
        header[30] = ((byteRate shr 16) and 0xffL).toByte()
        header[31] = ((byteRate shr 24) and 0xffL).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xffL).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()
        outputStream.write(header, 0, 44)
    }

    private fun encodeWavToAac(wavFile: File, aacFile: File) {
        val sampleRate = 44100
        val channels = 1
        val bitRate = 128000
        
        val format = android.media.MediaFormat.createAudioFormat(android.media.MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels)
        format.setInteger(android.media.MediaFormat.KEY_AAC_PROFILE, android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        format.setInteger(android.media.MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(android.media.MediaFormat.KEY_MAX_INPUT_SIZE, 16384)

        val codec = android.media.MediaCodec.createEncoderByType(android.media.MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, android.media.MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val muxer = android.media.MediaMuxer(aacFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var audioTrackIndex = -1

        val fis = java.io.FileInputStream(wavFile)
        // Skip WAV header (44 bytes)
        fis.skip(44)

        val inputBuffers = codec.inputBuffers
        val outputBuffers = codec.outputBuffers
        val info = android.media.MediaCodec.BufferInfo()

        val buffer = ByteArray(8192)
        var isExtractorEOS = false
        var isCodecEOS = false
        var totalPresentationTimeUs = 0L

        while (!isCodecEOS) {
            if (!isExtractorEOS) {
                val inputBufferIndex = codec.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = inputBuffers[inputBufferIndex]
                    inputBuffer.clear()
                    val bytesRead = fis.read(buffer)
                    if (bytesRead == -1) {
                        isExtractorEOS = true
                        codec.queueInputBuffer(inputBufferIndex, 0, 0, totalPresentationTimeUs, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        inputBuffer.put(buffer, 0, bytesRead)
                        codec.queueInputBuffer(inputBufferIndex, 0, bytesRead, totalPresentationTimeUs, 0)
                        val samples = bytesRead / 2
                        val durationUs = (samples * 1000000L) / sampleRate
                        totalPresentationTimeUs += durationUs
                    }
                }
            }

            val outputBufferIndex = codec.dequeueOutputBuffer(info, 10000)
            if (outputBufferIndex >= 0) {
                if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    info.size = 0
                }

                if (info.size > 0 && audioTrackIndex >= 0) {
                    val outputBuffer = outputBuffers[outputBufferIndex]
                    outputBuffer.position(info.offset)
                    outputBuffer.limit(info.offset + info.size)
                    muxer.writeSampleData(audioTrackIndex, outputBuffer, info)
                }

                codec.releaseOutputBuffer(outputBufferIndex, false)

                if ((info.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isCodecEOS = true
                }
            } else if (outputBufferIndex == android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val outputFormat = codec.outputFormat
                audioTrackIndex = muxer.addTrack(outputFormat)
                muxer.start()
            }
        }

        try {
            codec.stop()
            codec.release()
        } catch (e: Exception) {}
        
        try {
            muxer.stop()
            muxer.release()
        } catch (e: Exception) {}

        try {
            fis.close()
        } catch (e: Exception) {}
    }

    private fun mergeVideoAndAudio(videoFile: File, audioFile: File, outputFile: File) {
        val videoExtractor = android.media.MediaExtractor()
        videoExtractor.setDataSource(videoFile.absolutePath)
        
        val audioExtractor = android.media.MediaExtractor()
        audioExtractor.setDataSource(audioFile.absolutePath)

        val muxer = android.media.MediaMuxer(outputFile.absolutePath, android.media.MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

        var videoTrackIndex = -1
        var extVideoTrack = -1
        for (i in 0 until videoExtractor.trackCount) {
            val format = videoExtractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoExtractor.selectTrack(i)
                extVideoTrack = i
                videoTrackIndex = muxer.addTrack(format)
                break
            }
        }

        var audioTrackIndex = -1
        var extAudioTrack = -1
        for (i in 0 until audioExtractor.trackCount) {
            val format = audioExtractor.getTrackFormat(i)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioExtractor.selectTrack(i)
                extAudioTrack = i
                audioTrackIndex = muxer.addTrack(format)
                break
            }
        }

        if (videoTrackIndex < 0) {
            videoExtractor.release()
            audioExtractor.release()
            muxer.release()
            return
        }

        muxer.start()

        val bufferSize = 1048576
        val videoBuffer = java.nio.ByteBuffer.allocate(bufferSize)
        val videoInfo = android.media.MediaCodec.BufferInfo()
        while (true) {
            videoBuffer.clear()
            val sampleSize = videoExtractor.readSampleData(videoBuffer, 0)
            if (sampleSize < 0) break
            videoInfo.offset = 0
            videoInfo.size = sampleSize
            videoInfo.presentationTimeUs = videoExtractor.sampleTime
            videoInfo.flags = videoExtractor.sampleFlags
            muxer.writeSampleData(videoTrackIndex, videoBuffer, videoInfo)
            videoExtractor.advance()
        }

        if (audioTrackIndex >= 0) {
            val audioBuffer = java.nio.ByteBuffer.allocate(bufferSize)
            val audioInfo = android.media.MediaCodec.BufferInfo()
            while (true) {
                audioBuffer.clear()
                val sampleSize = audioExtractor.readSampleData(audioBuffer, 0)
                if (sampleSize < 0) break
                audioInfo.offset = 0
                audioInfo.size = sampleSize
                audioInfo.presentationTimeUs = audioExtractor.sampleTime
                audioInfo.flags = audioExtractor.sampleFlags
                muxer.writeSampleData(audioTrackIndex, audioBuffer, audioInfo)
                audioExtractor.advance()
            }
        }

        try {
            videoExtractor.release()
        } catch (e: Exception) {}
        try {
            audioExtractor.release()
        } catch (e: Exception) {}
        try {
            muxer.stop()
            muxer.release()
        } catch (e: Exception) {}
    }

    private fun startTimer() {
        _durationSeconds.value = 0
        timerJob = serviceScope.launch {
            while (isRecordingInternal) {
                delay(1000)
                if (_recordingState.value == RecordingState.RECORDING) {
                    _durationSeconds.value += 1
                    updateNotification("Recording screen: ${formatDuration(_durationSeconds.value)}")
                }
            }
        }
    }

    private var savedTime = 0
    private fun pauseTimer() {
        savedTime = _durationSeconds.value
    }

    private fun resumeTimer() {
        // Simple continuation handled by state check in timer loop
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _durationSeconds.value = 0
    }

    private fun cleanup() {
        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing virtual display", e)
        }
        virtualDisplay = null

        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing media recorder", e)
        }
        mediaRecorder = null

        try {
            mediaProjectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering media projection callback", e)
        }
        mediaProjectionCallback = null

        try {
            mediaProjection?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping media projection", e)
        }
        mediaProjection = null

        _recordingState.value = RecordingState.IDLE
        stopForeground(true)
    }

    override fun onDestroy() {
        stopRecording()
        timerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (_recordingState.value == RecordingState.RECORDING) {
                val pauseIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_PAUSE }
                val pausePendingIntent = PendingIntent.getService(
                    this,
                    2,
                    pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            } else if (_recordingState.value == RecordingState.PAUSED) {
                val resumeIntent = Intent(this, ScreenRecordService::class.java).apply { action = ACTION_RESUME }
                val resumePendingIntent = PendingIntent.getService(
                    this,
                    3,
                    resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(android.R.drawable.ic_media_play, "Resume", resumePendingIntent)
            }
        }

        return builder.build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return String.format("%02d:%02d", m, s)
    }

    private fun getMediaRecorderEncoder(encodingName: String): Int {
        return when (encodingName) {
            "H.264 / AVC" -> MediaRecorder.VideoEncoder.H264
            "H.265 / HEVC" -> MediaRecorder.VideoEncoder.HEVC
            "VP8" -> MediaRecorder.VideoEncoder.VP8
            "VP9" -> 6 // MediaRecorder.VideoEncoder.VP9 (Added in API 24)
            "AV1" -> 8 // MediaRecorder.VideoEncoder.AV1 (Added in API 33)
            "MPEG-4 Visual" -> MediaRecorder.VideoEncoder.MPEG_4_SP
            "H.263" -> MediaRecorder.VideoEncoder.H263
            else -> MediaRecorder.VideoEncoder.H264
        }
    }

    private fun getMediaRecorderOutputFormat(encodingName: String): Int {
        return when (encodingName) {
            "VP8", "VP9", "AV1" -> 9 // MediaRecorder.OutputFormat.WEBM (Added in API 21)
            else -> MediaRecorder.OutputFormat.MPEG_4
        }
    }

    private fun getFileExtension(encodingName: String): String {
        return when (encodingName) {
            "VP8", "VP9", "AV1" -> "webm"
            else -> "mp4"
        }
    }
}
