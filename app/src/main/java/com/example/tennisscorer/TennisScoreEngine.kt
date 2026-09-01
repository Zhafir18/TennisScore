package com.example.tennisscorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TennisScoreEngine : ViewModel() {

    private val _scoreState = MutableStateFlow(TennisScoreState())
    val scoreState: StateFlow<TennisScoreState> = _scoreState.asStateFlow()

    var p1NameInput by mutableStateOf("")
        private set
    var p2NameInput by mutableStateOf("")
        private set

    fun pointWonBy(playerNum: Int) {
        val current = _scoreState.value
        if (current.isMatchFinished) return

        var p1P = current.p1Points
        var p2P = current.p2Points
        var p1G = current.p1Games
        var p2G = current.p2Games
        var p1S = current.p1Sets
        var p2S = current.p2Sets
        var isTb = current.isTiebreak

        if (playerNum == 1) p1P++ else p2P++

        if (isTb) {
            if (p1P >= 7 && (p1P - p2P) >= 2) {
                p1S++
                p1G = 0; p2G = 0; p1P = 0; p2P = 0
                isTb = false
            } else if (p2P >= 7 && (p2P - p1P) >= 2) {
                p2S++
                p1G = 0; p2G = 0; p1P = 0; p2P = 0
                isTb = false
            }
        } else {
            if (p1P >= 4 && (p1P - p2P) >= 2) {
                p1G++
                p1P = 0; p2P = 0
            } else if (p2P >= 4 && (p2P - p1P) >= 2) {
                p2G++
                p1P = 0; p2P = 0
            } else if (p1P >= 3 && p2P >= 3 && p1P == p2P && p1P > 3) {
                p1P = 3; p2P = 3
            }
        }

        if (p1G == 6 && p2G == 6 && !isTb) {
            isTb = true
        } else if (p1G >= 6 && (p1G - p2G) >= 2) {
            p1S++
            p1G = 0; p2G = 0; p1P = 0; p2P = 0
            isTb = false
        } else if (p2G >= 6 && (p2G - p1G) >= 2) {
            p2S++
            p1G = 0; p2G = 0; p1P = 0; p2P = 0
            isTb = false
        }

        var finished = false
        var winner: String? = null
        if (p1S == 2) { finished = true; winner = current.p1Name }
        else if (p2S == 2) { finished = true; winner = current.p2Name }

        _scoreState.update {
            it.copy(
                p1Points = p1P, p2Points = p2P,
                p1Games = p1G, p2Games = p2G,
                p1Sets = p1S, p2Sets = p2S,
                isTiebreak = isTb,
                isMatchFinished = finished,
                winnerName = winner
            )
        }
    }

    fun resetMatch() {
        _scoreState.value = TennisScoreState()
    }

    fun resetScore() {
        val current = _scoreState.value
        _scoreState.value = TennisScoreState(p1Name = current.p1Name, p2Name = current.p2Name)
    }

    fun setPlayerNames(p1: String, p2: String) {
        _scoreState.update { it.copy(p1Name = p1, p2Name = p2) }
    }

    fun updateP1Name(name: String) { p1NameInput = name }
    fun updateP2Name(name: String) { p2NameInput = name }
}