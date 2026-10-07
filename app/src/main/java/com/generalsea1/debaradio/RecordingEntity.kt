package com.generalsea1.tmfm

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stationId: String,
    val stationName: String,
    val title: String? = null,
    val frequencyMhz: Double? = null,
    val filePath: String,
    val createdAtMillis: Long,
    val durationMillis: Long,
    val fileSizeBytes: Long,
    val source: String
)
