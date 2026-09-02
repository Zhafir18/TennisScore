package com.example.tennisscorer.tracking

import androidx.camera.core.ImageProxy

fun interface FrameAnalyzer {
    fun analyze(image: ImageProxy)
}
