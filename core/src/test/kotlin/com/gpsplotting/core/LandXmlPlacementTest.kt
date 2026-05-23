package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream

/** LandXML plan order matches Kubla-style exports (N E Z, no embedded CRS). */
class LandXmlPlacementTest {

    private val sample = Point3(377979.408, 924387.312, 321.225)

    private fun effectivePlanOrder(embedCrs: Boolean, northingFirst: Boolean): LandXmlWriter.PlanOrder =
        when {
            !embedCrs -> LandXmlWriter.PlanOrder.NorthingEastingElevation
            northingFirst -> LandXmlWriter.PlanOrder.NorthingEastingElevation
            else -> LandXmlCrs.planOrderForEpsg(6445)
        }

    private fun exportXml(embedCrs: Boolean, northingFirst: Boolean): String {
        val out = ByteArrayOutputStream()
        LandXmlWriter.writeTinSurface(
            out,
            "debug",
            listOf(sample, Point3(sample.easting + 10, sample.northing + 10, sample.elevation)),
            listOf(intArrayOf(1, 2, 1)),
            epsgCode = if (embedCrs) 6445 else null,
            planOrder = effectivePlanOrder(embedCrs, northingFirst),
        )
        return out.toString(Charsets.UTF_8)
    }

    @Test
    fun `no CRS export is northing easting with foot units`() {
        val xml = exportXml(embedCrs = false, northingFirst = false)
        assertTrue(xml.contains("<P id=\"1\">924387.312000 377979.408000"))
        assertTrue(xml.contains("linearUnit=\"foot\""))
    }

    @Test
    fun `embedded CRS export uses easting northing`() {
        val xml = exportXml(embedCrs = true, northingFirst = false)
        assertTrue(xml.contains("<P id=\"1\">377979.408000 924387.312000"))
        assertTrue(xml.contains("linearUnit=\"USSurveyFoot\""))
    }

    @Test
    fun `plan shift applies in survey easting northing before axis order`() {
        val out = ByteArrayOutputStream()
        LandXmlWriter.writeTinSurface(
            out,
            "T",
            listOf(sample),
            listOf(intArrayOf(1, 1, 1)),
            planOrder = LandXmlWriter.PlanOrder.NorthingEastingElevation,
            planShiftEastingFt = 0.85,
            planShiftNorthingFt = 1.9,
        )
        val xml = out.toString(Charsets.UTF_8)
        assertTrue(xml.contains("<P id=\"1\">924389.212000 377980.258000"))
    }
}
