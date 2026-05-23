package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlopingPlaneSurfaceTest {

    private fun corner(e: Double, n: Double, z: Double, code: String, name: String = code) =
        SurveyPoint(name, e, n, z, code)

    private fun start() = corner(0.0, 0.0, 100.0, "slope A")
    private fun end() = corner(100.0, 0.0, 100.0, "slope B")
    private fun c2() = corner(100.0, 50.0, 100.0, "slope")
    private fun c3() = corner(0.0, 50.0, 100.0, "slope")

    @Test
    fun `four match preserves surveyed Z`() {
        val corners = listOf(
            corner(0.0, 0.0, 100.0, "Match"),
            corner(100.0, 0.0, 101.0, "Match"),
            corner(100.0, 50.0, 102.0, "Match"),
            corner(0.0, 50.0, 101.5, "Match"),
        )
        val r = SlopingPlane.buildPadSurfaceCorners(
            corners,
            start = null,
            end = null,
            gradePercent = 0.0,
            mode = SlopingPlane.PadSurfaceMode.MatchCorners,
        )
        assertEquals(100.0, r.corners[0].elevation, 1e-6)
        assertEquals(101.0, r.corners[1].elevation, 1e-6)
        assertEquals(102.0, r.corners[2].elevation, 1e-6)
        assertEquals(101.5, r.corners[3].elevation, 1e-6)
        assertTrue(r.detailLines.any { it.contains("surveyed Z") })
    }

    @Test
    fun `zero match sloping plane matches planeFromAnchor`() {
        val corners = listOf(start(), c2(), end(), c3())
        val grade = 1.0
        val r = SlopingPlane.buildPadSurfaceCorners(
            corners,
            start = start(),
            end = end(),
            gradePercent = grade,
            mode = SlopingPlane.PadSurfaceMode.SlopingPlane,
        )
        val anchor = Point3(0.0, 0.0, 100.0)
        val azimuth = inverseAzimuthDegrees(anchor, Point3(100.0, 0.0, 100.0))
        val plane = SlopingPlane.planeFromAnchorSingleSlope(anchor, azimuth, grade)
        val expected = SlopingPlane.cornersWithPlaneZ(corners.map { it.easting to it.northing }, plane)
        assertEquals(expected.size, r.corners.size)
        for (i in expected.indices) {
            assertEquals(expected[i].elevation, r.corners[i].elevation, 1e-3)
        }
    }

    @Test
    fun `one match plane passes through match Z`() {
        val match = corner(0.0, 0.0, 105.0, "Match")
        val corners = listOf(match, c2(), end(), c3())
        val grade = 2.0
        val r = SlopingPlane.buildPadSurfaceCorners(
            corners,
            start = start(),
            end = end(),
            gradePercent = grade,
            mode = SlopingPlane.PadSurfaceMode.SlopingPlane,
        )
        val matchCorner = r.corners[0]
        assertEquals(105.0, matchCorner.elevation, 1e-3)
        assertEquals(2.0, r.plane!!.maxGradePercent(), 1e-3)
    }

    @Test
    fun `two match corners throws`() {
        val corners = listOf(
            corner(0.0, 0.0, 100.0, "Match"),
            corner(100.0, 0.0, 100.0, "Match"),
            end(),
            c3(),
        )
        assertThrows(IllegalStateException::class.java) {
            SlopingPlane.buildPadSurfaceCorners(
                corners,
                start = start(),
                end = end(),
                gradePercent = 1.0,
                mode = SlopingPlane.PadSurfaceMode.SlopingPlane,
            )
        }
    }
}
