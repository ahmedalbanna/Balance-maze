package com.example.data

import kotlinx.coroutines.flow.Flow

class GameRepository(private val dao: GameRecordDao) {
    val allRecords: Flow<List<GameRecord>> = dao.getAllRecords()

    suspend fun saveRecord(record: GameRecord) {
        dao.insertRecord(record)
    }

    suspend fun clearRecords() {
        dao.clearAllRecords()
    }
}
