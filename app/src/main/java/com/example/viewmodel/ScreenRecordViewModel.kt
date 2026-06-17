package com.example.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.RecordingEntity
import com.example.data.RecordingRepository
import com.example.data.RecordSettings
import com.example.service.ScreenRecordService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class ScreenRecordViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: RecordingRepository
    val recordings: StateFlow<List<RecordingEntity>>

    private val _settings = MutableStateFlow(RecordSettings())
    val settings: StateFlow<RecordSettings> = _settings

    val recordingState = ScreenRecordService.recordingState
    val durationSeconds = ScreenRecordService.durationSeconds
    val errorFlow = ScreenRecordService.errorFlow

    init {
        val database = AppDatabase.getDatabase(application)
        repository = RecordingRepository(database.recordingDao())
        
        recordings = repository.allRecordings.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        loadSettings()
    }

    private fun loadSettings() {
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = RecordSettings.load(getApplication())
            _settings.value = loaded
        }
    }

    fun updateSettings(newSettings: RecordSettings) {
        _settings.value = newSettings
        viewModelScope.launch(Dispatchers.IO) {
            RecordSettings.save(getApplication(), newSettings)
        }
    }

    fun deleteRecording(recording: RecordingEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual file
            try {
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Delete database row
            repository.deleteById(recording.id)
        }
    }

    fun clearAllRecordings() {
        viewModelScope.launch(Dispatchers.IO) {
            // Delete actual files first
            try {
                recordings.value.forEach { recording ->
                    val file = File(recording.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // Clear db
            repository.clearAll()
        }
    }
}
