package com.gpsplotting.core

import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Minimal ASCII DXF (R2000-ish) with 3D POLYLINE entities matching [RoadBuilder] outputs.
 * `$INSUNITS` = 4 (US Survey Feet).
 */
object DxfWriter {

    data class BreaklineLayers(
        val left: String = "DRIVEWAY_LEFT",
        val center: String = "DRIVEWAY_CENTER",
        val right: String = "DRIVEWAY_RIGHT",
        val endCaps: String = "DRIVEWAY_END_CAPS",
    )

    fun writeBreaklinesDxf(
        output: OutputStream,
        left: List<Point3>,
        center: List<Point3>,
        right: List<Point3>,
        layers: BreaklineLayers = BreaklineLayers(),
        addEndCaps: Boolean = true,
    ) {
        val layerNames = mutableListOf(layers.left, layers.center, layers.right)
        if (addEndCaps) layerNames.add(layers.endCaps)
        val handles = HandleAllocator()

        val sb = StringBuilder()
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("HEADER")
        appendHeader(sb, handles)
        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("TABLES")
        appendLayerTable(sb, handles, layerNames.distinct())
        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("ENTITIES")

        fun poly(layer: String, pts: List<Point3>) {
            if (pts.size < 2) return
            appendPolyline3d(sb, handles, layer, pts)
        }

        poly(layers.left, left)
        poly(layers.center, center)
        poly(layers.right, right)

        if (addEndCaps && left.size >= 2 && right.size >= 2) {
            poly(layers.endCaps, listOf(left.first(), right.first()))
            poly(layers.endCaps, listOf(left.last(), right.last()))
        }

        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("EOF")

        OutputStreamWriter(output, StandardCharsets.US_ASCII).use { it.write(sb.toString()) }
    }

    /** Generic multi-layer export (e.g. slope line polylines). */
    fun writePolylinesDxf(
        output: OutputStream,
        layersAndPolylines: List<Pair<String, List<Point3>>>,
    ) {
        val layerNames = layersAndPolylines.map { it.first }.distinct()
        val handles = HandleAllocator()
        val sb = StringBuilder()
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("HEADER")
        appendHeader(sb, handles)
        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("TABLES")
        appendLayerTable(sb, handles, layerNames)
        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("SECTION")
        sb.appendLine("2")
        sb.appendLine("ENTITIES")
        for ((layer, pts) in layersAndPolylines) {
            if (pts.size >= 2) appendPolyline3d(sb, handles, layer, pts)
        }
        sb.appendLine("0")
        sb.appendLine("ENDSEC")
        sb.appendLine("0")
        sb.appendLine("EOF")
        OutputStreamWriter(output, StandardCharsets.US_ASCII).use { it.write(sb.toString()) }
    }

    private fun appendHeader(sb: StringBuilder, handles: HandleAllocator) {
        sb.appendLine("9")
        sb.appendLine("\$ACADVER")
        sb.appendLine("1")
        sb.appendLine("AC1015")
        sb.appendLine("9")
        sb.appendLine("\$INSUNITS")
        sb.appendLine("70")
        sb.appendLine("4")
    }

    private fun appendLayerTable(sb: StringBuilder, handles: HandleAllocator, layers: List<String>) {
        val tableHandle = handles.next()
        sb.appendLine("0")
        sb.appendLine("TABLE")
        sb.appendLine("2")
        sb.appendLine("LAYER")
        sb.appendLine("5")
        sb.appendLine(tableHandle)
        sb.appendLine("100")
        sb.appendLine("AcDbSymbolTable")
        sb.appendLine("70")
        sb.appendLine(layers.size.toString())
        for (layer in layers) {
            val hl = handles.next()
            sb.appendLine("0")
            sb.appendLine("LAYER")
            sb.appendLine("5")
            sb.appendLine(hl)
            sb.appendLine("100")
            sb.appendLine("AcDbSymbolTableRecord")
            sb.appendLine("100")
            sb.appendLine("AcDbLayerTableRecord")
            sb.appendLine("2")
            sb.appendLine(layer)
            sb.appendLine("70")
            sb.appendLine("0")
            sb.appendLine("62")
            sb.appendLine("7")
            sb.appendLine("6")
            sb.appendLine("CONTINUOUS")
        }
        sb.appendLine("0")
        sb.appendLine("ENDTAB")
    }

    private fun appendPolyline3d(sb: StringBuilder, handles: HandleAllocator, layer: String, pts: List<Point3>) {
        val polyHandle = handles.next()
        sb.appendLine("0")
        sb.appendLine("POLYLINE")
        sb.appendLine("5")
        sb.appendLine(polyHandle)
        sb.appendLine("100")
        sb.appendLine("AcDbEntity")
        sb.appendLine("8")
        sb.appendLine(layer)
        sb.appendLine("100")
        sb.appendLine("AcDbPolyline")
        sb.appendLine("66")
        sb.appendLine("1")
        sb.appendLine("70")
        sb.appendLine("8")
        sb.appendLine("10")
        sb.appendLine("0.0")
        sb.appendLine("20")
        sb.appendLine("0.0")
        sb.appendLine("30")
        sb.appendLine("0.0")
        for (p in pts) {
            val vh = handles.next()
            sb.appendLine("0")
            sb.appendLine("VERTEX")
            sb.appendLine("5")
            sb.appendLine(vh)
            sb.appendLine("100")
            sb.appendLine("AcDbEntity")
            sb.appendLine("8")
            sb.appendLine(layer)
            sb.appendLine("100")
            sb.appendLine("AcDbVertex")
            sb.appendLine("100")
            sb.appendLine("AcDbPolylineVertex")
            sb.appendLine("70")
            sb.appendLine("32")
            sb.appendLine("10")
            sb.appendLine(formatNum(p.easting))
            sb.appendLine("20")
            sb.appendLine(formatNum(p.northing))
            sb.appendLine("30")
            sb.appendLine(formatNum(p.elevation))
        }
        sb.appendLine("0")
        sb.appendLine("SEQEND")
        sb.appendLine("5")
        sb.appendLine(handles.next())
        sb.appendLine("100")
        sb.appendLine("AcDbEntity")
        sb.appendLine("8")
        sb.appendLine(layer)
    }

    private fun formatNum(v: Double): String = String.format(java.util.Locale.US, "%.6f", v)

    private class HandleAllocator(private var counter: Int = 0x10) {
        fun next(): String {
            val h = Integer.toHexString(counter).uppercase()
            counter++
            return h
        }
    }
}
