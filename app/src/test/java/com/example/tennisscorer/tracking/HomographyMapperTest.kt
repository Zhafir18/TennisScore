package com.example.tennisscorer.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class HomographyMapperTest {

    @Test fun `identity matrix maps point to itself`() {
        val identity = floatArrayOf(1f, 0f, 0f,  0f, 1f, 0f,  0f, 0f, 1f)
        val mapper = HomographyMapper(identity)
        val (x, y) = mapper.mapNormalized(0.3f, 0.7f)
        assertEquals(0.3f, x, 0.001f)
        assertEquals(0.7f, y, 0.001f)
    }

    @Test fun `scaling matrix maps unit corner to court dimensions`() {
        val matrix = floatArrayOf(
            HomographyMapper.COURT_WIDTH_M,  0f, 0f,
            0f, HomographyMapper.COURT_LENGTH_M, 0f,
            0f, 0f, 1f
        )
        val mapper = HomographyMapper(matrix)
        val (x, y) = mapper.mapNormalized(1f, 1f)
        assertEquals(HomographyMapper.COURT_WIDTH_M, x, 0.001f)
        assertEquals(HomographyMapper.COURT_LENGTH_M, y, 0.001f)
    }

    @Test fun `constants match standard doubles court dimensions`() {
        assertEquals(10.97f, HomographyMapper.COURT_WIDTH_M, 0.001f)
        assertEquals(23.77f, HomographyMapper.COURT_LENGTH_M, 0.001f)
    }
}
