package com.example.tennisscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bounce_records")
data class BounceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchId: Long,
    val x: Float,
    val y: Float,
    val player: Int
)
