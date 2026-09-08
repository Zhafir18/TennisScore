package com.example.tennisscorer.ui.viewmodels

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tennisscorer.data.BounceRepository
import com.example.tennisscorer.tracking.HeatmapRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HeatmapViewModel(
    private val matchId: Long,
    private val bounceRepo: BounceRepository
) : ViewModel() {

    companion object {
        fun Factory(bounceRepo: BounceRepository, matchId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { HeatmapViewModel(matchId, bounceRepo) }
            }
    }

    private val _heatmapBitmap = MutableStateFlow<Bitmap?>(null)
    val heatmapBitmap: StateFlow<Bitmap?> = _heatmapBitmap.asStateFlow()

    private val _bounceCount = MutableStateFlow(0)
    val bounceCount: StateFlow<Int> = _bounceCount.asStateFlow()

    init {
        viewModelScope.launch {
            val records = bounceRepo.getByMatchId(matchId)
            val points = records.map { PointF(it.x, it.y) }
            _bounceCount.value = points.size
            _heatmapBitmap.value = HeatmapRenderer.render(points)
        }
    }
}
