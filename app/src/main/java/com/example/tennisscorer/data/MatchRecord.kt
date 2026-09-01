package com.example.tennisscorer.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "match_records")
data class MatchRecord(
    @PrimaryKey(autoGenerate = true) val matchId: Long = 0,
    val p1Name: String,
    val p2Name: String,
    val winnerName: String,
    val p1FinalSets: Int,
    val p2FinalSets: Int,
    val dateTimestamp: Long
)
