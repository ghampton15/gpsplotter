package com.gpsplotting.core

import kotlin.math.abs
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
    fun `autograde anchors start and end elevations`() {
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
        assertEquals(140.0, graded.last().elevation, 1e-6)
        assertTrue(summary.totalLengthFt > 199.0)
        assertTrue(summary.gradePct > 0.0)
    }

    @Test
    fun `autograde ties ends when grade limit is tighter than survey`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 200.0),
        )
        val (graded, summary) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 5.0),
        )
        assertEquals(100.0, graded[0].elevation, 1e-6)
        assertEquals(200.0, graded[1].elevation, 1e-6)
        assertTrue(summary.gradePct > 5.0)
    }

    @Test
    fun `autograde lowers highs and raises lows toward chord`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 50.0, 130.0),
            Point3(0.0, 100.0, 95.0),
            Point3(0.0, 150.0, 128.0),
            Point3(0.0, 200.0, 100.0),
        )
        val survey = center.map { it.elevation }
        val (graded, _) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 100.0),
        )
        val design = graded.map { it.elevation }
        assertEquals(100.0, design[0], 1e-6)
        assertEquals(100.0, design.last(), 1e-6)
        assertTrue(design[1] < survey[1], "high at 50ft should be lowered")
        assertTrue(design[2] > survey[2], "low at 100ft should be raised")
        assertTrue(design[3] < survey[3], "high at 150ft should be lowered")
    }

    @Test
    fun `autograde stays closer to existing than a giant arch`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 110.0),
            Point3(0.0, 200.0, 105.0),
        )
        val survey = center.map { it.elevation }.toDoubleArray()
        val (graded, _) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 100.0),
        )
        val design = graded.map { it.elevation }.toDoubleArray()
        val midErr = abs(design[1] - survey[1])
        assertTrue(midErr < 8.0, "mid design should stay near survey, not a huge arch")
    }

    @Test
    fun `autograde smooths jagged survey`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 50.0, 135.0),
            Point3(0.0, 100.0, 105.0),
            Point3(0.0, 150.0, 128.0),
            Point3(0.0, 200.0, 100.0),
        )
        val surveyZ = center.map { it.elevation }.toDoubleArray()
        val (graded, _) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 100.0),
        )
        val designZ = graded.map { it.elevation }.toDoubleArray()
        assertTrue(profileRoughnessPenalty(designZ) < profileRoughnessPenalty(surveyZ))
    }

    @Test
    fun `cut fill depths follow design vs existing`() {
        assertEquals(10.0, cutDepthFt(100.0, 90.0), 1e-9)
        assertEquals(0.0, fillDepthFt(100.0, 90.0), 1e-9)
        assertEquals(10.0, fillDepthFt(100.0, 110.0), 1e-9)
        assertEquals(0.0, cutDepthFt(100.0, 110.0), 1e-9)

        val stations = listOf(0.0, 100.0)
        val survey = listOf(100.0, 100.0)
        val design = doubleArrayOf(90.0, 110.0)
        val (cut, fill) = cutFillAreaAlongProfile(stations, survey, design)
        assertEquals(250.0, cut, 1.0)
        assertEquals(250.0, fill, 1.0)
    }

    @Test
    fun `segment with cut and fill does not net to zero`() {
        val stations = listOf(0.0, 100.0)
        val survey = listOf(100.0, 100.0)
        val design = doubleArrayOf(95.0, 105.0)
        val (cut, fill) = cutFillAreaAlongProfile(stations, survey, design)
        assertTrue(cut > 0.0)
        assertTrue(fill > 0.0)
    }

    @Test
    fun `autograde fill does not exceed cut from existing highs`() {
        val center = listOf(
            Point3(0.0, 0.0, 100.0),
            Point3(0.0, 100.0, 122.0),
            Point3(0.0, 200.0, 100.0),
        )
        val (graded, summary) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.0, maxGradePct = 100.0),
        )
        assertTrue(summary.approxFillAreaFt2 <= summary.approxCutAreaFt2 + 0.5)
        assertTrue(summary.approxCutAreaFt2 > 0.0)
    }

    @Test
    fun `least squares throws on zero length`() {
        assertThrows<IllegalArgumentException> {
            leastSquaresLine(listOf(0.0, 0.0), listOf(100.0, 101.0))
        }
    }
}
