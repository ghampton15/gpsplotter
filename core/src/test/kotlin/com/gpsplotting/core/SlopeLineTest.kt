package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SlopeLineTest {

    @Test
    fun `stakeBetweenPoints applies negative grade from start`() {
        val start = Point3(0.0, 0.0, 100.0)
        val end = Point3(100.0, 0.0, 100.0)
        val r = SlopeLine.stakeBetweenPoints(start, end, -1.0)
        val last = r.stations.last()
        assertEquals(100.0, start.elevation, 1e-6)
        assertEquals(99.0, last.elevation, 1e-3)
        assertEquals(100.0, last.easting, 1e-3)
    }

    @Test
    fun `stakeBetweenPoints applies positive grade`() {
        val start = Point3(0.0, 0.0, 100.0)
        val end = Point3(50.0, 0.0, 100.0)
        val r = SlopeLine.stakeBetweenPoints(start, end, 2.0)
        assertEquals(101.0, r.stations.last().elevation, 1e-3)
    }

    @Test
    fun `coincident plan points throw`() {
        val p = Point3(1.0, 2.0, 3.0)
        assertThrows(IllegalArgumentException::class.java) {
            SlopeLine.stakeBetweenPoints(p, p, 1.0)
        }
    }
}
