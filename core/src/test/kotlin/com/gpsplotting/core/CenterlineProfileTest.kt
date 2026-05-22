package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CenterlineProfileTest {

    @Test
    fun `build profile from centerline`() {
        val rows = listOf(
            SurveyPoint("A", 0.0, 0.0, 100.0, "CEN"),
            SurveyPoint("B", 0.0, 100.0, 110.0, "CEN"),
            SurveyPoint("C", 0.0, 200.0, 105.0, "CEN"),
        )
        val profile = buildCenterlineProfile(rows, "cen")
        assertEquals(3, profile.stations.size)
        assertEquals(10.0, profile.elevationDeltaFt, 1e-6)
        assertEquals(0.0, profile.stations[0].stationFt, 1e-9)
        assertEquals(200.0, profile.lengthFt, 1e-9)
    }

    @Test
    fun `preview autograde elevations`() {
        val rows = listOf(
            SurveyPoint("A", 0.0, 0.0, 100.0, "CEN"),
            SurveyPoint("B", 0.0, 100.0, 200.0, "CEN"),
        )
        val profile = buildCenterlineProfile(rows, "cen")
        val design = previewAutogradeElevations(profile, AutoGradeParams(0.0, 5.0))
        assertEquals(2, design.size)
        assertEquals(100.0, design[0], 1e-6)
        assertEquals(200.0, design[1], 0.01)
    }

    @Test
    fun `segment grades between stations`() {
        val rows = listOf(
            SurveyPoint("A", 0.0, 0.0, 100.0, "CEN"),
            SurveyPoint("B", 0.0, 100.0, 110.0, "CEN"),
        )
        val profile = buildCenterlineProfile(rows, "cen")
        val grades = segmentGradesPct(profile)
        assertEquals(1, grades.size)
        assertEquals(10.0, grades[0], 0.01)
    }

    @Test
    fun `requires at least two points`() {
        val rows = listOf(SurveyPoint("A", 0.0, 0.0, 100.0, "CEN"))
        assertThrows<IllegalArgumentException> {
            buildCenterlineProfile(rows, "cen")
        }
    }
}
