package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RoadProfileTest {

    @Test
    fun `cumulative stations plan only`() {
        val pts = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 100.0),
            Point3(100.0, 100.0, 100.0),
        )
        val s = cumulativeStations(pts)
        assertEquals(3, s.size)
        assertEquals(0.0, s[0], 1e-9)
        assertEquals(100.0, s[1], 1e-9)
        assertEquals(200.0, s[2], 1e-9)
    }

    @Test
    fun `autograde anchors start elevation`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 120.0),
            Point3(0.0, 200.0, 140.0),
        )
        val (graded, summary) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 100.0),
        )
        assertEquals(100.0, graded[0].elevation, 1e-6)
        assertTrue(summary.totalLengthFt > 199.0)
        assertTrue(summary.gradePct > 0.0)
    }

    @Test
    fun `autograde clamps steep slope`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 200.0),
        )
        val (graded, summary) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 5.0),
        )
        assertEquals(100.0, graded[0].elevation, 1e-6)
        assertEquals(5.0, summary.gradePct, 0.01)
        assertEquals(105.0, graded[1].elevation, 0.01)
    }

    @Test
    fun `least squares throws on zero length`() {
        assertThrows<IllegalArgumentException> {
            leastSquaresLine(listOf(0.0, 0.0), listOf(100.0, 101.0))
        }
    }
}
