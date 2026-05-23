package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiseUnitsTest {

    @Test
    fun `one foot rise and 100 foot run is 1 percent grade`() {
        val s = ElevationGrade.solve(1.0, 100.0, null)
        assertEquals(1.0, s.gradePercent, 1e-6)
    }
}
