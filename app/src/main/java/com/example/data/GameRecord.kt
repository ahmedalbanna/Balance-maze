package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_records")
data class GameRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val levelIndex: Int,
    val timeSpentSec: Float,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val isWin: Boolean
)
