package com.gpsplotting.core

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * LandXML 1.2 TIN export aligned with Emlid-style layout:
 * optional [CoordinateSystem], simple `<F>i j k</F>` faces (no neighbor attribute).
 *
 * Each `<P>` uses **ENZ**: easting, northing, elevation — matching projected CRS axis order for
 * EPSG 6445 (NAD83(2011) / Georgia East ft US): easting before northing in the plan pair per CRS axis order.
 *
 * Optional vertical CRS (e.g. EPSG 6360 NAVD88 height ft US) is written using LandXML
 * `verticalCoordinateSystemName` / `verticalDatum` so elevations align with orthometric heights
 * (GEOID18 applied in the field maps to NAVD88 via EPSG 6360).
 * Use [PlanOrder.NorthingEastingElevation] only for CRS that expect northing first (e.g. some national grids).
 */
object LandXmlWriter {

    enum class PlanOrder {
        /** Typical US / Emlid CSV: easting, northing, elevation. */
        EastingNorthingElevation,

        /** Matches Emlid SWEREF99 TM sample: northing, easting, elevation. */
        NorthingEastingElevation,
    }

    fun writeTinSurface(
        output: OutputStream,
        surfaceName: String,
        points: List<Point3>,
        facesOneBased: List<IntArray>,
        projectName: String = "GPSPlotting",
        epsgCode: Int? = null,
        crsDescription: String? = null,
        verticalEpsgCode: Int? = null,
        verticalCrsDescription: String? = null,
        planOrder: PlanOrder = PlanOrder.EastingNorthingElevation,
    ) {
        require(points.isNotEmpty()) { "Surface needs at least one point." }

        val (dateStr, timeStr) = landXmlTimestamp()
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine(
            "<LandXML xmlns=\"http://www.landxml.org/schema/LandXML-1.2\" " +
                "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" " +
                "version=\"1.2\" " +
                "xsi:schemaLocation=\"http://www.landxml.org/schema/LandXML-1.2 " +
                "http://www.landxml.org/schema/LandXML-1.2/LandXML-1.2.xsd\" " +
                "date=\"$dateStr\" " +
                "time=\"$timeStr\" " +
                "language=\"English\" " +
                "readOnly=\"false\">",
        )
        sb.appendLine("  <Units>")
        sb.appendLine(
            "    <Imperial areaUnit=\"acre\" linearUnit=\"USSurveyFoot\" volumeUnit=\"cubicYard\" " +
                "temperatureUnit=\"fahrenheit\" pressureUnit=\"inchHG\"/>",
        )
        sb.appendLine("  </Units>")
        if (epsgCode != null) {
            sb.appendLine(coordinateSystemElement(epsgCode, crsDescription, verticalEpsgCode, verticalCrsDescription))
        }
        sb.appendLine("  <Application name=\"${xmlEscape(projectName)}\" manufacturer=\"local\" version=\"1.0\"/>")
        sb.appendLine("  <Surfaces>")
        sb.appendLine("    <Surface name=\"${xmlEscape(surfaceName)}\">")
        sb.appendLine("      <Definition surfType=\"TIN\">")
        sb.appendLine("        <Pnts>")
        for (i in points.indices) {
            val p = points[i]
            val id = i + 1
            val (c1, c2) = when (planOrder) {
                PlanOrder.EastingNorthingElevation -> p.easting to p.northing
                PlanOrder.NorthingEastingElevation -> p.northing to p.easting
            }
            sb.appendLine(
                "          <P id=\"$id\">${formatCoord(c1)} ${formatCoord(c2)} ${formatCoord(p.elevation)}</P>",
            )
        }
        sb.appendLine("        </Pnts>")
        sb.appendLine("        <Faces>")
        for (f in facesOneBased) {
            require(f.size == 3) { "Each face must have 3 indices." }
            sb.appendLine("          <F>${f[0]} ${f[1]} ${f[2]}</F>")
        }
        sb.appendLine("        </Faces>")
        sb.appendLine("      </Definition>")
        sb.appendLine("    </Surface>")
        sb.appendLine("  </Surfaces>")
        sb.appendLine("</LandXML>")

        OutputStreamWriter(output, StandardCharsets.UTF_8).use { it.write(sb.toString()) }
    }

    fun writeQuadTin(
        output: OutputStream,
        surfaceName: String,
        quadCorners: List<Point3>,
        projectName: String = "GPSPlotting",
        epsgCode: Int? = null,
        crsDescription: String? = null,
        verticalEpsgCode: Int? = null,
        verticalCrsDescription: String? = null,
        planOrder: PlanOrder = PlanOrder.EastingNorthingElevation,
    ) {
        require(quadCorners.size == 4) { "Need 4 corner points." }
        val (f1, f2) = SlopingPlane.triangulateQuad(quadCorners)
        writeTinSurface(
            output,
            surfaceName,
            quadCorners,
            listOf(f1, f2),
            projectName,
            epsgCode,
            crsDescription,
            verticalEpsgCode,
            verticalCrsDescription,
            planOrder,
        )
    }

    private fun coordinateSystemElement(
        horizontalEpsg: Int,
        crsDescription: String?,
        verticalEpsg: Int?,
        verticalCrsDescription: String?,
    ): String {
        val horizontalLabel = crsDescription?.trim()?.takeIf { it.isNotEmpty() } ?: "EPSG:$horizontalEpsg"
        val desc = if (verticalEpsg != null) {
            val vLabel = verticalCrsDescription?.trim()?.takeIf { it.isNotEmpty() }
                ?: "EPSG:$verticalEpsg NAVD88 height (ft US)"
            "$horizontalLabel | Elevations: $vLabel"
        } else {
            horizontalLabel
        }
        val sb = StringBuilder()
        sb.append("  <CoordinateSystem")
        val wkt = LandXmlCrs.ogcWktForEpsg(horizontalEpsg)
        if (wkt != null) {
            sb.append(" ogcWktCode=\"${xmlEscape(wkt)}\"")
        }
        sb.append(" epsgCode=\"$horizontalEpsg\"")
        sb.append(" desc=\"${xmlEscape(desc)}\"")
        if (verticalEpsg != null) {
            sb.append(" verticalDatum=\"${xmlEscape("NAVD88 (ft US)")}\"")
        }
        sb.append("/>")
        return sb.toString()
    }

    private fun landXmlTimestamp(): Pair<String, String> {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val timeStr = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        return dateStr to timeStr
    }

    private fun formatCoord(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
