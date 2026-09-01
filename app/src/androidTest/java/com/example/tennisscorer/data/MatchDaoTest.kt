package com.example.tennisscorer.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MatchDaoTest {
    private lateinit var db: TennisScorerDatabase
    private lateinit var dao: MatchDao

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TennisScorerDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.matchDao()
    }

    @After fun teardown() { db.close() }

    @Test fun insertAndRetrieveMatch() = runBlocking {
        val record = MatchRecord(p1Name = "Alice", p2Name = "Bob",
            winnerName = "Alice", p1FinalSets = 2, p2FinalSets = 0, dateTimestamp = 1000L)
        val matchId = dao.insertMatch(record)
        dao.insertEvents(listOf(
            PointEvent(matchId = matchId, pointIndex = 0, playerNum = 1),
            PointEvent(matchId = matchId, pointIndex = 1, playerNum = 2)
        ))

        val allMatches = dao.getAllMatches().first()
        assertEquals(1, allMatches.size)
        assertEquals("Alice", allMatches[0].p1Name)

        val found = dao.getMatchById(matchId)
        assertNotNull(found)
        assertEquals(matchId, found!!.matchId)

        val events = dao.getEventsForMatch(matchId)
        assertEquals(2, events.size)
        assertEquals(1, events[0].playerNum)
    }

    @Test fun deleteMatchCascadesToEvents() = runBlocking {
        val matchId = dao.insertMatch(MatchRecord(p1Name = "A", p2Name = "B",
            winnerName = "A", p1FinalSets = 2, p2FinalSets = 1, dateTimestamp = 2000L))
        dao.insertEvents(listOf(PointEvent(matchId = matchId, pointIndex = 0, playerNum = 1)))

        dao.deleteMatch(matchId)

        assertEquals(0, dao.getAllMatches().first().size)
        assertEquals(0, dao.getEventsForMatch(matchId).size)
    }
}
