package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class CoreMathTest {

    @Test
    fun `offset single square`() {
        val square = listOf(
            SurveyPoint("1", 0.0, 0.0, 100.0),
            SurveyPoint("2", 10.0, 0.0, 100.0),
            SurveyPoint("3", 10.0, 10.0, 100.0),
            SurveyPoint("4", 0.0, 10.0, 100.0),
        )
        val o = OffsetCalculator.generateOffsets(square, 1.0)
        assertEquals(4, o.size)
        // Corner at (0,0): bisector of edges toward prev/next offsets along (~0.707, 0.707) — matches OffsetCalc.py
        assertTrue(nearlyEqual(o[0].easting, 0.707, 0.02))
        assertTrue(nearlyEqual(o[0].northing, 0.707, 0.02))
    }

    @Test
    fun `road builder centerline`() {
        val rows = listOf(
            SurveyPoint("A", 0.0, 0.0, 100.0, "CEN"),
            SurveyPoint("B", 0.0, 100.0, 100.0, "CEN"),
        )
        val r = RoadBuilder.buildBreaklinesFromSurveyCsv(
            rows,
            RoadBuilder.RoadBreaklinesParams(12.0, 2.0, "cen"),
        )
        assertEquals(2, r.center.size)
        // half width 6, 2% -> dz = 0.12
        assertTrue(nearlyEqual(r.left[0].elevation, 100.0 - 0.12, 1e-6))
    }

    @Test
    fun `sloping plane through three`() {
        val p1 = Point3(0.0, 0.0, 100.0)
        val p2 = Point3(100.0, 0.0, 100.0)
        val p3 = Point3(0.0, 100.0, 102.0)
        val pl = SlopingPlane.fitPlaneThroughThreePoints(p1, p2, p3)
        val z4 = pl.zAt(100.0, 100.0)
        assertTrue(nearlyEqual(z4, 102.0, 1e-6))
    }

    @Test
    fun `dxf contains polyline`() {
        val out = ByteArrayOutputStream()
        DxfWriter.writeBreaklinesDxf(
            out,
            listOf(Point3(0.0, 0.0, 1.0), Point3(10.0, 0.0, 1.0)),
            listOf(Point3(0.0, 0.0, 1.0), Point3(10.0, 0.0, 1.0)),
            listOf(Point3(0.0, 0.0, 1.0), Point3(10.0, 0.0, 1.0)),
        )
        val s = out.toString(StandardCharsets.US_ASCII)
        assertTrue(s.contains("POLYLINE"))
        assertTrue(s.contains("\$INSUNITS"))
    }

    @Test
    fun `landxml roundtrip bytes`() {
        val out = ByteArrayOutputStream()
        val pts = listOf(
            Point3(1.0, 2.0, 3.0),
            Point3(4.0, 5.0, 6.0),
            Point3(7.0, 8.0, 9.0),
        )
        LandXmlWriter.writeTinSurface(out, "T", pts, listOf(intArrayOf(1, 2, 3)))
        val xml = out.toString(StandardCharsets.UTF_8)
        assertTrue(xml.contains("LandXML"))
        assertTrue(xml.contains("USSurveyFoot"))
    }

    @Test
    fun `csv parse`() {
        val csv = "Name,Easting,Northing,Elevation,Code\nP1,1,2,3,CEN\n"
        val rows = CsvIo.readSurveyRows(ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8)))
        assertEquals(1, rows.size)
        assertEquals("P1", rows[0].pointName)
        assertEquals(3.0, rows[0].elevation, 1e-9)
    }

    @Test
    fun `parseDoubleLenient strips spaces from Emlid-style numbers`() {
        assertEquals(424494.526, parseDoubleLenient("424 494.526")!!, 1e-9)
        assertEquals(-1000.5, parseDoubleLenient("-1 000.5")!!, 1e-9)
    }
}
