package com.example.data

import kotlinx.coroutines.flow.Flow

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()

    suspend fun insert(recording: RecordingEntity) {
        recordingDao.insertRecording(recording)
    }

    suspend fun deleteById(id: Int) {
        recordingDao.deleteRecordingById(id)
    }

    suspend fun clearAll() {
        recordingDao.clearAllRecordings()
    }
}
