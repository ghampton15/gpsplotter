package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class GradeRatioTest {

    @Test
    fun `33 percent is 3 to 1`() {
        assertEquals("3 to 1", GradeRatio.format(33.0))
    }

    @Test
    fun `10 percent is 10 to 1`() {
        assertEquals("10 to 1", GradeRatio.format(10.0))
    }

    @Test
    fun `from rise and run`() {
        assertEquals("3 to 1", GradeRatio.formatFromRiseRun(1.0, 3.0))
    }

    @Test
    fun `zero grade has no ratio`() {
        assertNull(GradeRatio.format(0.0))
    }
}
