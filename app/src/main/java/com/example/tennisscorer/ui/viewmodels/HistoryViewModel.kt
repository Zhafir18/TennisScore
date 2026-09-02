package com.example.tennisscorer.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.data.MatchRecord
import com.example.tennisscorer.data.MatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: MatchRepository) : ViewModel() {

    val matches: StateFlow<List<MatchRecord>> = repository.getAllMatches()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    fun deleteMatch(matchId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.deleteMatch(matchId) }
                .onFailure { android.util.Log.e("TennisScorer", "deleteMatch failed", it) }
        }
    }

    companion object {
        fun Factory(repository: MatchRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
