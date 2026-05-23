package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PadOffsetCalculatorTest {

    private fun corner(name: String, e: Double, n: Double) =
        SurveyPoint(name, e, n, 100.0, "BLDG_A")

    @Test
    fun `double stake produces separate OS layers per pad`() {
        val pad = listOf(
            corner("C1", 0.0, 0.0),
            corner("C2", 100.0, 0.0),
            corner("C3", 100.0, 100.0),
            corner("C4", 0.0, 100.0),
        )
        val pads = mapOf("BLDG_A" to pad)
        val lines = PadOffsetCalculator.buildPadOffsetPolylines(pads, setback1Feet = 5.0, setback2Feet = 10.0)
        assertEquals(2, lines.size)
        assertEquals("OS_1", lines[0].first)
        assertEquals("OS_2", lines[1].first)
        assertEquals(4, lines[0].second.size)
        assertEquals("C1_offset1", lines[0].second[0].pointName)
        assertEquals("C1_offset2", lines[1].second[0].pointName)
        val d1 = kotlin.math.hypot(lines[0].second[0].easting, lines[0].second[0].northing)
        val d2 = kotlin.math.hypot(lines[1].second[0].easting, lines[1].second[0].northing)
        assertTrue(d2 > d1)
    }
}
