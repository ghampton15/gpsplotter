package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class LineOffsetTest {

    private fun pt(name: String, e: Double, n: Double, z: Double, code: String) =
        SurveyPoint(name, e, n, z, code)

    @Test
    fun `filterByCode is case insensitive and preserves order`() {
        val rows = listOf(
            pt("1", 0.0, 0.0, 10.0, "LINE"),
            pt("x", 1.0, 1.0, 0.0, "other"),
            pt("2", 10.0, 0.0, 11.0, "line"),
        )
        val filtered = LineOffset.filterByCode(rows, "line")
        assertEquals(2, filtered.size)
        assertEquals("1", filtered[0].pointName)
        assertEquals("2", filtered[1].pointName)
    }

    @Test
    fun `offsetElevation changes Z only`() {
        val line = listOf(
            pt("A", 100.0, 200.0, 50.0, "L"),
            pt("B", 110.0, 200.0, 52.0, "L"),
        )
        val out = LineOffset.offsetElevation(line, 2.0)
        assertEquals(100.0, out[0].easting, 1e-6)
        assertEquals(200.0, out[0].northing, 1e-6)
        assertEquals(52.0, out[0].elevation, 1e-3)
        assertEquals("A_offset1", out[0].pointName)
    }

    @Test
    fun `offsetPerpendicular right moves east on northward line`() {
        val line = listOf(
            pt("A", 0.0, 0.0, 10.0, "CURB"),
            pt("B", 0.0, 100.0, 10.0, "CURB"),
        )
        val out = LineOffset.offsetPerpendicular(line, 10.0, LineOffset.LineSide.Right)
        assertEquals(10.0, out[0].easting, 1e-3)
        assertEquals(0.0, out[0].northing, 1e-3)
        assertEquals(10.0, out[1].easting, 1e-3)
        assertEquals(100.0, out[1].northing, 1e-3)
    }

    @Test
    fun `left and right offset opposite easting on northward line`() {
        val line = listOf(
            pt("A", 0.0, 0.0, 10.0, "L"),
            pt("B", 0.0, 100.0, 10.0, "L"),
        )
        val right = LineOffset.offsetPerpendicular(line, 5.0, LineOffset.LineSide.Right)
        val left = LineOffset.offsetPerpendicular(line, 5.0, LineOffset.LineSide.Left)
        assertEquals(-5.0, left[0].easting, 1e-3)
        assertEquals(5.0, right[0].easting, 1e-3)
    }

    @Test
    fun `double stake produces two separate polylines`() {
        val line = listOf(
            pt("CP1", 0.0, 0.0, 100.0, "CURB"),
            pt("CP2", 0.0, 50.0, 100.0, "CURB"),
        )
        val lines = LineOffset.buildCurbStakeLines(
            line,
            setback1Feet = 2.0,
            setback2Feet = 4.0,
            side = LineOffset.LineSide.Right,
            deltaElevationFeet = 0.5,
        )
        assertEquals(2, lines.size)
        assertEquals("OS_1", lines[0].layerName)
        assertEquals("OS_2", lines[1].layerName)
        assertEquals(2, lines[0].points.size)
        assertEquals(2, lines[1].points.size)
        assertEquals("CP1_offset1", lines[0].points[0].pointName)
        assertEquals("CP1_offset2", lines[1].points[0].pointName)
        assertEquals(2.0, lines[0].points[0].easting, 1e-3)
        assertEquals(4.0, lines[1].points[0].easting, 1e-3)
    }

    @Test
    fun `single point throws`() {
        val one = listOf(pt("A", 0.0, 0.0, 0.0, "L"))
        assertThrows(IllegalArgumentException::class.java) {
            LineOffset.buildCurbStakes(one, 1.0, null, LineOffset.LineSide.Right, 0.0)
        }
    }
}
