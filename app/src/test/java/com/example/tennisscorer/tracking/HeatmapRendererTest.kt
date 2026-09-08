package com.example.tennisscorer.tracking

import android.graphics.PointF
import org.junit.Assert.assertNull
import org.junit.Test

class HeatmapRendererTest {

    @Test fun `render empty list returns null`() {
        val result = HeatmapRenderer.render(emptyList())
        assertNull(result)
    }
}
