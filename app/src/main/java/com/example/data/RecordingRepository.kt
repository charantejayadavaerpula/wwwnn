package com.example.data

import kotlinx.coroutines.flow.Flow
import java.io.File

class RecordingRepository(private val recordingDao: RecordingDao) {
    val allRecordings: Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()

    suspend fun insert(recording: RecordingEntity): Long {
        return recordingDao.insertRecording(recording)
    }

    suspend fun delete(recording: RecordingEntity) {
        // First delete file from local storage
        try {
            val file = File(recording.filePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Then delete from DB
        recordingDao.deleteRecording(recording)
    }

    suspend fun update(recording: RecordingEntity) {
        recordingDao.updateRecording(recording)
    }

    suspend fun getById(id: Int): RecordingEntity? {
        return recordingDao.getRecordingById(id)
    }
}
