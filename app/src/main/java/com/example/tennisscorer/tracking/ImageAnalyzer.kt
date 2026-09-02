package com.example.tennisscorer.tracking

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class ImageAnalyzer : ImageAnalysis.Analyzer {
    private var frameAnalyzer: FrameAnalyzer? = null

    fun setFrameAnalyzer(analyzer: FrameAnalyzer?) {
        frameAnalyzer = analyzer
    }

    override fun analyze(image: ImageProxy) {
        frameAnalyzer?.analyze(image) ?: image.close()
    }
}
