package com.example.tennisscorer

data class TennisScoreState(
    val p1Name: String = "Pemain 1",
    val p2Name: String = "Pemain 2",
    val p1Points: Int = 0,
    val p2Points: Int = 0,
    val p1Games: Int = 0,
    val p2Games: Int = 0,
    val p1Sets: Int = 0,
    val p2Sets: Int = 0,
    val isTiebreak: Boolean = false,
    val isMatchFinished: Boolean = false,
    val winnerName: String? = null
) {
    val p1DisplayScore: String
        get() = formatScore(p1Points, p2Points, isTiebreak)

    val p2DisplayScore: String
        get() = formatScore(p2Points, p1Points, isTiebreak)

    private fun formatScore(myPoints: Int, opponentPoints: Int, tiebreak: Boolean): String {
        if (tiebreak) return myPoints.toString()

        return when {
            myPoints >= 3 && opponentPoints >= 3 -> {
                when {
                    myPoints == opponentPoints -> "40"
                    myPoints > opponentPoints -> "AD"
                    else -> "40"
                }
            }
            myPoints == 0 -> "0"
            myPoints == 1 -> "15"
            myPoints == 2 -> "30"
            myPoints == 3 -> "40"
            else -> "40"
        }
    }
}

fun applyPoint(state: TennisScoreState, playerNum: Int): TennisScoreState {
    if (state.isMatchFinished) return state

    var p1P = state.p1Points
    var p2P = state.p2Points
    var p1G = state.p1Games
    var p2G = state.p2Games
    var p1S = state.p1Sets
    var p2S = state.p2Sets
    var isTb = state.isTiebreak

    if (playerNum == 1) p1P++ else p2P++

    if (isTb) {
        if (p1P >= 7 && (p1P - p2P) >= 2) {
            p1S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
        } else if (p2P >= 7 && (p2P - p1P) >= 2) {
            p2S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
        }
    } else {
        if (p1P >= 4 && (p1P - p2P) >= 2) {
            p1G++; p1P = 0; p2P = 0
        } else if (p2P >= 4 && (p2P - p1P) >= 2) {
            p2G++; p1P = 0; p2P = 0
        } else if (p1P >= 3 && p2P >= 3 && p1P == p2P && p1P > 3) {
            p1P = 3; p2P = 3
        }
    }

    if (!isTb && p1G == 6 && p2G == 6) {
        isTb = true
    } else if (p1G >= 6 && (p1G - p2G) >= 2) {
        p1S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
    } else if (p2G >= 6 && (p2G - p1G) >= 2) {
        p2S++; p1G = 0; p2G = 0; p1P = 0; p2P = 0; isTb = false
    }

    var finished = false
    var winner: String? = null
    if (p1S == 2) { finished = true; winner = state.p1Name }
    else if (p2S == 2) { finished = true; winner = state.p2Name }

    return state.copy(
        p1Points = p1P, p2Points = p2P,
        p1Games = p1G, p2Games = p2G,
        p1Sets = p1S, p2Sets = p2S,
        isTiebreak = isTb,
        isMatchFinished = finished,
        winnerName = winner
    )
}