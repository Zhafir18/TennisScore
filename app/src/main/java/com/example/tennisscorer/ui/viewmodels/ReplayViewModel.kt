package com.example.tennisscorer.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.TennisScoreState
import com.example.tennisscorer.applyPoint
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import com.example.tennisscorer.data.PointEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ReplayViewModel(
    private val repository: MatchRepository,
    private val matchId: Long
) : ViewModel() {

    private val _replayState = MutableStateFlow(TennisScoreState())
    val replayState: StateFlow<TennisScoreState> = _replayState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _speedMultiplier = MutableStateFlow(1.0f)
    val speedMultiplier: StateFlow<Float> = _speedMultiplier.asStateFlow()

    private val _isFinished = MutableStateFlow(false)
    val isFinished: StateFlow<Boolean> = _isFinished.asStateFlow()

    // Pair(currentPointIndex, totalPoints)
    private val _progress = MutableStateFlow(0 to 0)
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _matchRecord = MutableStateFlow<MatchRecord?>(null)
    val matchRecord: StateFlow<MatchRecord?> = _matchRecord.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private var events: List<PointEvent> = emptyList()
    private var currentIndex = 0
    private var playJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val record = repository.getMatchById(matchId)
                if (record == null) { _loadError.value = true; return@runCatching }
                _matchRecord.value = record
                events = repository.getEventsForMatch(matchId)
                if (events.isEmpty()) { _loadError.value = true; return@runCatching }
                _replayState.value = TennisScoreState(p1Name = record.p1Name, p2Name = record.p2Name)
                _progress.value = 0 to events.size
            }.onFailure { _loadError.value = true }
        }
    }

    fun togglePlayPause() {
        if (_isFinished.value) return
        if (_isPlaying.value) {
            playJob?.cancel()
            _isPlaying.value = false
        } else {
            _isPlaying.value = true
            playJob = viewModelScope.launch {
                while (currentIndex < events.size) {
                    val delayMs = (1000L / _speedMultiplier.value).toLong()
                    delay(delayMs)
                    val event = events[currentIndex]
                    _replayState.value = applyPoint(_replayState.value, event.playerNum)
                    currentIndex++
                    _progress.value = currentIndex to events.size
                }
                _isPlaying.value = false
                _isFinished.value = true
            }
        }
    }

    fun setSpeed(multiplier: Float) {
        _speedMultiplier.value = multiplier
    }

    companion object {
        fun Factory(repository: MatchRepository, matchId: Long): ViewModelProvider.Factory =
            viewModelFactory { initializer { ReplayViewModel(repository, matchId) } }
    }
}
