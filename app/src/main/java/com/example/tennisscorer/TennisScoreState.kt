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