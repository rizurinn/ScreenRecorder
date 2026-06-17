package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String,
    val fileName: String,
    val durationMs: Long,
    val resolution: String,
    val fps: Int,
    val encoder: String,
    val timestamp: Long = System.currentTimeMillis(),
    val fileSize: Long
)
