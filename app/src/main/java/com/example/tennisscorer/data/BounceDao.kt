package com.example.tennisscorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BounceDao {
    @Insert
    suspend fun insert(record: BounceRecord)

    @Query("SELECT * FROM bounce_records WHERE matchId = :matchId")
    suspend fun getByMatchId(matchId: Long): List<BounceRecord>
}
