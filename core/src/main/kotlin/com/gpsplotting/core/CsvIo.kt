package com.gpsplotting.core

import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Read/write survey CSV. Headers are matched case-insensitively and UTF-8 BOM is tolerated.
 *
 * Supported layouts:
 * - **Code, Easting, Northing, Elevation** — typical Flow export for coded corners (no Name column).
 * - **Name, Easting, Northing, Elevation** with optional Code — legacy / full point lists.
 *
 * At least one of **Name** or **Code** column headers must be present so rows can be labeled.
 * Rows without a Name use **Corner 1**, **Corner 2**, … in file order.
 */
object CsvIo {

    fun readSurveyRows(input: InputStream): List<SurveyPoint> {
        val text = input.bufferedReader(StandardCharsets.UTF_8).readText().trimStart('\uFEFF')
        val lines = text.lineSequence().map { it.trimEnd('\r') }.filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()

        val headers = parseCsvLine(lines.first()).map { it.trim() }
        val indexMap = headers.mapIndexed { i, h -> h.lowercase() to i }.toMap()

        fun idx(vararg keys: String): Int {
            for (k in keys) {
                indexMap[k]?.let { return it }
            }
            throw IllegalArgumentException(
                "CSV must include Easting, Northing, Elevation (found: $headers)",
            )
        }

        val iName = indexMap["name"]
        val iCode = indexMap["code"]
        require(iName != null || iCode != null) {
            "CSV must include at least one of columns Name or Code, plus Easting, Northing, Elevation (found: $headers)"
        }

        val iE = idx("easting")
        val iN = idx("northing")
        val iZ = idx("elevation", "height")

        val rows = mutableListOf<SurveyPoint>()
        var rowOrdinal = 0
        for (lineIdx in 1 until lines.size) {
            val cols = parseCsvLine(lines[lineIdx])
            fun col(i: Int): String = cols.getOrElse(i) { "" }.trim()
            fun colOpt(i: Int?): String = if (i == null) "" else col(i)

            val nameRaw = colOpt(iName)
            val codeRaw = colOpt(iCode)
            val eStr = col(iE)
            val nStr = col(iN)
            val zStr = col(iZ)
            if (eStr.isEmpty() || nStr.isEmpty() || zStr.isEmpty()) continue
            rowOrdinal++
            val pointName = when {
                nameRaw.isNotEmpty() -> nameRaw
                else -> "Corner $rowOrdinal"
            }
            rows.add(
                SurveyPoint(
                    pointName = pointName,
                    easting = eStr.toDouble(),
                    northing = nStr.toDouble(),
                    elevation = zStr.toDouble(),
                    code = codeRaw,
                ),
            )
        }
        return rows
    }

    /** RFC-ish CSV line split; handles quoted fields with commas. */
    fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i += 2
                        continue
                    } else {
                        inQuotes = false
                        i++
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
                c == '"' -> {
                    inQuotes = true
                    i++
                }
                c == ',' -> {
                    out.add(sb.toString())
                    sb.clear()
                    i++
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }
        out.add(sb.toString())
        return out
    }

    fun writeOffsetCsv(output: OutputStream, points: List<SurveyPoint>) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { w ->
            w.write("PointName,Easting,Northing,Elevation\r\n")
            for (p in points) {
                w.write(csvEscape(p.pointName))
                w.write(",")
                w.write(formatCoord(p.easting))
                w.write(",")
                w.write(formatCoord(p.northing))
                w.write(",")
                w.write(formatCoord(p.elevation))
                w.write("\r\n")
            }
        }
    }

    /** Full survey CSV with Name, Easting, Northing, Elevation, Code (matches common Emlid export). */
    fun writeSurveyCsv(output: OutputStream, points: List<SurveyPoint>) {
        OutputStreamWriter(output, StandardCharsets.UTF_8).use { w ->
            w.write("Name,Easting,Northing,Elevation,Code\r\n")
            for (p in points) {
                w.write(csvEscape(p.pointName))
                w.write(",")
                w.write(formatCoord(p.easting))
                w.write(",")
                w.write(formatCoord(p.northing))
                w.write(",")
                w.write(formatCoord(p.elevation))
                w.write(",")
                w.write(csvEscape(p.code))
                w.write("\r\n")
            }
        }
    }

    private fun formatCoord(v: Double): String =
        String.format(Locale.US, "%.3f", roundFeet3(v))

    private fun csvEscape(s: String): String {
        if (s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            return '"' + s.replace("\"", "\"\"") + '"'
        }
        return s
    }
}
