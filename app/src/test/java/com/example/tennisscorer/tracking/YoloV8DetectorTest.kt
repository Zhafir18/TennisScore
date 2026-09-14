package com.example.tennisscorer.tracking

import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test

class YoloV8DetectorTest {

    private fun makeOutput(anchors: List<FloatArray>): Array<FloatArray> {
        // anchors: list of [cx, cy, w, h, conf] in 640-pixel space
        // conf placed at feature index 4 (class 0 = 'Tennis-ball')
        val out = Array(21) { FloatArray(8400) }
        anchors.forEachIndexed { i, v ->
            out[0][i] = v[0]; out[1][i] = v[1]
            out[2][i] = v[2]; out[3][i] = v[3]
            out[4][i] = v[4]
        }
        return out
    }

    /** Creates RectF using field assignment (parameterized constructor is stubbed in JVM tests). */
    private fun rectF(left: Float, top: Float, right: Float, bottom: Float): RectF {
        val r = RectF()
        r.left = left; r.top = top; r.right = right; r.bottom = bottom
        return r
    }

    @Test fun `parseOutput empty when all confidence below threshold`() {
        val output = makeOutput(listOf(floatArrayOf(320f, 240f, 50f, 50f, 0.1f)))
        assertTrue(YoloV8Detector.parseOutput(output).isEmpty())
    }

    @Test fun `parseOutput returns detection above threshold`() {
        val output = makeOutput(listOf(floatArrayOf(320f, 240f, 64f, 64f, 0.9f)))
        val result = YoloV8Detector.parseOutput(output)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence, 0.001f)
    }

    @Test fun `parseOutput normalizes coords to 0-1`() {
        val output = makeOutput(listOf(floatArrayOf(320f, 240f, 64f, 64f, 0.8f)))
        val result = YoloV8Detector.parseOutput(output)
        val box = result[0].boundingBox
        // cx=320, cy=240, w=64, h=64 → left=(320-32)/640, top=(240-32)/640, ...
        assertEquals((320f - 32f) / 640f, box.left,   0.001f)
        assertEquals((240f - 32f) / 640f, box.top,    0.001f)
        assertEquals((320f + 32f) / 640f, box.right,  0.001f)
        assertEquals((240f + 32f) / 640f, box.bottom, 0.001f)
    }

    @Test fun `parseOutput clamps coords to 0-1`() {
        val output = makeOutput(listOf(floatArrayOf(0f, 240f, 100f, 64f, 0.8f)))
        val result = YoloV8Detector.parseOutput(output)
        assertEquals(1, result.size)
        assertTrue(result[0].boundingBox.left >= 0f)
    }

    @Test fun `nms suppresses overlapping boxes`() {
        val box1 = rectF(0f, 0f, 100f, 100f)
        val box2 = rectF(10f, 10f, 110f, 110f)
        val candidates = listOf(Pair(box1, 0.9f), Pair(box2, 0.8f))
        val result = YoloV8Detector.nms(candidates)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].second, 0.001f)
    }

    @Test fun `nms keeps non-overlapping boxes`() {
        val box1 = rectF(0f, 0f, 50f, 50f)
        val box2 = rectF(200f, 200f, 250f, 250f)
        val candidates = listOf(Pair(box1, 0.9f), Pair(box2, 0.8f))
        assertEquals(2, YoloV8Detector.nms(candidates).size)
    }

    @Test fun `iou zero for non-overlapping boxes`() {
        val a = rectF(0f, 0f, 10f, 10f)
        val b = rectF(20f, 20f, 30f, 30f)
        assertEquals(0f, YoloV8Detector.iou(a, b), 0.001f)
    }

    @Test fun `iou one for identical boxes`() {
        val a = rectF(0f, 0f, 10f, 10f)
        assertEquals(1f, YoloV8Detector.iou(a, a), 0.001f)
    }

    @Test fun `iou partial overlap correct`() {
        val a = rectF(0f, 0f, 10f, 10f)
        val b = rectF(5f, 0f, 15f, 10f)
        // intersection=50, union=150
        assertEquals(50f / 150f, YoloV8Detector.iou(a, b), 0.001f)
    }
}
