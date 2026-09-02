package com.example.tennisscorer.tracking

import androidx.camera.core.ImageProxy
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAnalyzerTest {

    @Test fun `frame closed when no analyzer set`() {
        val analyzer = ImageAnalyzer()
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        verify { mockProxy.close() }
    }

    @Test fun `analyzer called when set`() {
        val analyzer = ImageAnalyzer()
        var received: ImageProxy? = null
        analyzer.setFrameAnalyzer { image -> received = image; image.close() }
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        assertEquals(mockProxy, received)
        verify { mockProxy.close() }
    }

    @Test fun `frame closed after analyzer cleared`() {
        val analyzer = ImageAnalyzer()
        analyzer.setFrameAnalyzer { image -> image.close() }
        analyzer.setFrameAnalyzer(null)
        val mockProxy = mockk<ImageProxy>(relaxed = true)
        analyzer.analyze(mockProxy)
        verify { mockProxy.close() }
    }
}
