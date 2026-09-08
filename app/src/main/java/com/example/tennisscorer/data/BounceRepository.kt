package com.example.tennisscorer.data

class BounceRepository(private val dao: BounceDao) {
    suspend fun insert(record: BounceRecord) = dao.insert(record)
    suspend fun getByMatchId(matchId: Long): List<BounceRecord> = dao.getByMatchId(matchId)
}
