package com.example.tennisscorer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyPointTest {

    private fun state(p1P: Int = 0, p2P: Int = 0, p1G: Int = 0, p2G: Int = 0,
                      p1S: Int = 0, p2S: Int = 0, tb: Boolean = false) =
        TennisScoreState("P1", "P2", p1P, p2P, p1G, p2G, p1S, p2S, tb)

    @Test fun firstPointIncrementsP1() {
        assertEquals(1, applyPoint(state(), 1).p1Points)
        assertEquals(0, applyPoint(state(), 1).p2Points)
    }

    @Test fun gameWonAt40_0() {
        val next = applyPoint(state(p1P = 3, p2P = 0), 1)
        assertEquals(1, next.p1Games)
        assertEquals(0, next.p1Points)
        assertEquals(0, next.p2Points)
    }

    @Test fun deuceResetsTo40_40() {
        // At 40-40 (p1P=3, p2P=3), advantage then opponent wins → back to 40-40
        val adState = applyPoint(state(p1P = 3, p2P = 3), 1) // P1 has AD
        assertEquals(4, adState.p1Points)
        val backToDeuce = applyPoint(adState, 2) // P2 wins → deuce
        assertEquals(3, backToDeuce.p1Points)
        assertEquals(3, backToDeuce.p2Points)
    }

    @Test fun setWonAfterSixGamesWithTwoGameLead() {
        // 5-0, win one game → 6-0 → p1 wins set
        var s = state(p1G = 5, p2G = 0)
        repeat(4) { s = applyPoint(s, 1) }
        assertEquals(1, s.p1Sets)
        assertEquals(0, s.p1Games)
    }

    @Test fun tiebreakStartsAt6_6() {
        var s = state(p1G = 5, p2G = 5)
        repeat(4) { s = applyPoint(s, 1) } // P1 wins game → 6-5
        repeat(4) { s = applyPoint(s, 2) } // P2 wins game → 6-6
        assertTrue(s.isTiebreak)
    }

    @Test fun matchFinishesAfterTwoSets() {
        var s = state(p1S = 1, p2S = 0, p1G = 5, p2G = 0)
        repeat(4) { s = applyPoint(s, 1) }
        assertTrue(s.isMatchFinished)
        assertEquals("P1", s.winnerName)
    }

    @Test fun noPointAfterMatchFinished() {
        val finished = state(p1S = 2).copy(isMatchFinished = true, winnerName = "P1")
        val same = applyPoint(finished, 1)
        assertEquals(finished, same)
    }
}
