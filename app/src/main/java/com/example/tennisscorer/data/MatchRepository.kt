package com.example.tennisscorer.data

import kotlinx.coroutines.flow.Flow

class MatchRepository(private val dao: MatchDao) {

    suspend fun saveMatch(record: MatchRecord, events: List<PointEvent>) {
        val matchId = dao.insertMatch(record)
        dao.insertEvents(events.map { it.copy(matchId = matchId) })
    }

    fun getAllMatches(): Flow<List<MatchRecord>> = dao.getAllMatches()

    suspend fun getMatchById(matchId: Long): MatchRecord? = dao.getMatchById(matchId)

    suspend fun getEventsForMatch(matchId: Long): List<PointEvent> =
        dao.getEventsForMatch(matchId)

    suspend fun deleteMatch(matchId: Long) = dao.deleteMatch(matchId)
}
