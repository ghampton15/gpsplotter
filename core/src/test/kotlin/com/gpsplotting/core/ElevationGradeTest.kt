package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ElevationGradeTest {

    private fun nearlyEqual(a: Double, b: Double, eps: Double): Boolean = abs(a - b) <= eps

    @Test
    fun `solve run and grade gives rise`() {
        val s = ElevationGrade.solve(null, 200.0, 6.0)
        assertTrue(nearlyEqual(s.riseFt, 12.0, 1e-9))
        assertTrue(nearlyEqual(s.runFt, 200.0, 1e-9))
        assertTrue(nearlyEqual(s.gradePercent, 6.0, 1e-9))
    }

    @Test
    fun `solve rise and run gives grade`() {
        val s = ElevationGrade.solve(12.0, 200.0, null)
        assertTrue(nearlyEqual(s.gradePercent, 6.0, 1e-9))
    }

    @Test
    fun `solve rise and grade gives run`() {
        val s = ElevationGrade.solve(12.0, null, 6.0)
        assertTrue(nearlyEqual(s.runFt, 200.0, 1e-6))
    }
}
