package com.example.tennisscorer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MatchDao {
    @Insert
    suspend fun insertMatch(match: MatchRecord): Long

    @Insert
    suspend fun insertEvents(events: List<PointEvent>)

    @Query("SELECT * FROM match_records ORDER BY dateTimestamp DESC")
    fun getAllMatches(): Flow<List<MatchRecord>>

    @Query("SELECT * FROM match_records WHERE matchId = :matchId")
    suspend fun getMatchById(matchId: Long): MatchRecord?

    @Query("SELECT * FROM point_events WHERE matchId = :matchId ORDER BY pointIndex ASC")
    suspend fun getEventsForMatch(matchId: Long): List<PointEvent>

    @Query("DELETE FROM match_records WHERE matchId = :matchId")
    suspend fun deleteMatch(matchId: Long)
}
