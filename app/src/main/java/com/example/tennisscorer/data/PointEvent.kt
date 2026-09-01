package com.example.tennisscorer.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "point_events",
    foreignKeys = [ForeignKey(
        entity = MatchRecord::class,
        parentColumns = ["matchId"],
        childColumns = ["matchId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class PointEvent(
    @PrimaryKey(autoGenerate = true) val eventId: Long = 0,
    val matchId: Long,
    val pointIndex: Int,
    val playerNum: Int
)
