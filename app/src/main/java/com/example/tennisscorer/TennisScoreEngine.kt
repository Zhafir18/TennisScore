package com.example.tennisscorer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.data.PointEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TennisScoreEngine(private val repository: MatchRepository) : ViewModel() {

    private val _scoreState = MutableStateFlow(TennisScoreState())
    val scoreState: StateFlow<TennisScoreState> = _scoreState.asStateFlow()

    var p1NameInput by mutableStateOf("")
        private set
    var p2NameInput by mutableStateOf("")
        private set

    private val pendingEvents = mutableListOf<PointEvent>()

    fun pointWonBy(playerNum: Int) {
        val current = _scoreState.value
        if (current.isMatchFinished) return
        pendingEvents.add(PointEvent(matchId = 0, pointIndex = pendingEvents.size, playerNum = playerNum))
        val next = applyPoint(current, playerNum)
        _scoreState.value = next
        if (next.isMatchFinished) persistMatch(next)
    }

    private fun persistMatch(finalState: TennisScoreState) {
        val snapshot = pendingEvents.toList()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.saveMatch(
                    MatchRecord(
                        p1Name = finalState.p1Name,
                        p2Name = finalState.p2Name,
                        winnerName = finalState.winnerName ?: "",
                        p1FinalSets = finalState.p1Sets,
                        p2FinalSets = finalState.p2Sets,
                        dateTimestamp = System.currentTimeMillis()
                    ),
                    snapshot
                )
            }
        }
    }

    fun resetMatch() {
        _scoreState.value = TennisScoreState()
        pendingEvents.clear()
    }

    fun resetScore() {
        val current = _scoreState.value
        _scoreState.value = TennisScoreState(p1Name = current.p1Name, p2Name = current.p2Name)
        pendingEvents.clear()
    }

    fun setPlayerNames(p1: String, p2: String) {
        _scoreState.value = _scoreState.value.copy(p1Name = p1, p2Name = p2)
    }

    fun updateP1Name(name: String) { p1NameInput = name }
    fun updateP2Name(name: String) { p2NameInput = name }

    companion object {
        fun Factory(repository: MatchRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { TennisScoreEngine(repository) }
        }
    }
}
