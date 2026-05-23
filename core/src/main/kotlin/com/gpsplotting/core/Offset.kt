package com.gpsplotting.core

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
/**
 * Closed polygon corner offsets using bisector of adjacent edges (same as [OffsetCalc.py]).
 */
object OffsetCalculator {

    fun unitVector(dx: Double, dy: Double): Pair<Double, Double> {
        val length = hypot(dx, dy)
        return if (length != 0.0) dx / length to dy / length else 0.0 to 0.0
    }

    fun offsetDiagonal(
        corner: SurveyPoint,
        prev: SurveyPoint,
        next: SurveyPoint,
        distance: Double,
    ): SurveyPoint {
        val vec1 = prev.easting - corner.easting to prev.northing - corner.northing
        val vec2 = next.easting - corner.easting to next.northing - corner.northing
        val u1 = unitVector(vec1.first, vec1.second)
        val u2 = unitVector(vec2.first, vec2.second)
        val avgDx = u1.first + u2.first
        val avgDy = u1.second + u2.second
        val unitAvg = unitVector(avgDx, avgDy)
        val offsetX = corner.easting + unitAvg.first * distance
        val offsetY = corner.northing + unitAvg.second * distance
        return SurveyPoint(
            pointName = corner.pointName + "_OFFSET",
            easting = roundFeet3(offsetX),
            northing = roundFeet3(offsetY),
            elevation = roundFeet3(corner.elevation),
            code = "",
        )
    }

    fun generateOffsets(points: List<SurveyPoint>, distance: Double): List<SurveyPoint> {
        require(points.size >= 3) { "Need at least three points for a closed polygon." }
        val n = points.size
        val offsets = mutableListOf<SurveyPoint>()
        for (i in points.indices) {
            val prev = points[(i - 1 + n) % n]
            val next = points[(i + 1) % n]
            offsets.add(offsetDiagonal(points[i], prev, next, distance))
        }
        return offsets
    }
}

/**
 * Building pads: group by Code prefix BLDG_*; quadrilateral order with opposite at i+2 (same as [OffsetCalcMultiplePads.py]).
 */
object PadOffsetCalculator {

    fun groupByBuildingPrefix(rows: List<SurveyPoint>, prefixUpper: String = "BLDG_"): Map<String, List<SurveyPoint>> {
        val pfx = prefixUpper.uppercase()
        return rows
            .filter { it.code.trim().uppercase().startsWith(pfx) }
            .groupBy { it.code.trim().uppercase() }
            .mapValues { (_, pts) -> pts }
    }

    fun calculateDiagonalDirectionDegrees(corner1: SurveyPoint, corner2: SurveyPoint): Double {
        val dx = corner2.easting - corner1.easting
        val dy = corner2.northing - corner1.northing
        return Math.toDegrees(kotlin.math.atan2(dy, dx))
    }

    fun offsetOutward(
        corner: SurveyPoint,
        oppositeCorner: SurveyPoint,
        distance: Double,
        suffixIndex: Int = 0,
    ): SurveyPoint {
        val diagonalDeg = calculateDiagonalDirectionDegrees(corner, oppositeCorner)
        val angleRad = Math.toRadians(diagonalDeg + 180.0)
        val dx = cos(angleRad)
        val dy = sin(angleRad)
        return SurveyPoint(
            pointName = padOffsetPointName(corner.pointName, suffixIndex),
            easting = roundFeet3(corner.easting + dx * distance),
            northing = roundFeet3(corner.northing + dy * distance),
            elevation = roundFeet3(corner.elevation),
            code = "",
        )
    }

    fun generateOffsetRing(
        padPoints: List<SurveyPoint>,
        padCode: String,
        distance: Double,
        suffixIndex: Int = 0,
    ): List<SurveyPoint> {
        val n = padPoints.size
        require(n >= 4) { "Pad $padCode needs at least 4 corner points in order (got $n)." }
        return List(n) { i ->
            val opposite = padPoints[(i + 2) % n]
            offsetOutward(padPoints[i], opposite, distance, suffixIndex)
        }
    }

    /**
     * One polyline per pad per setback layer (OS_1 / OS_2). [setback2Feet] null = single ring only.
     */
    fun buildPadOffsetPolylines(
        pads: Map<String, List<SurveyPoint>>,
        setback1Feet: Double,
        setback2Feet: Double?,
    ): List<Pair<String, List<SurveyPoint>>> {
        val out = mutableListOf<Pair<String, List<SurveyPoint>>>()
        for ((code, padPoints) in pads) {
            out.add("OS_1" to generateOffsetRing(padPoints, code, setback1Feet, suffixIndex = 1))
            if (setback2Feet != null) {
                out.add("OS_2" to generateOffsetRing(padPoints, code, setback2Feet, suffixIndex = 2))
            }
        }
        return out
    }

    fun generateOffsets(pads: Map<String, List<SurveyPoint>>, distance: Double): List<SurveyPoint> {
        val all = mutableListOf<SurveyPoint>()
        for ((code, padPoints) in pads) {
            all.addAll(generateOffsetRing(padPoints, code, distance, suffixIndex = 0))
        }
        return all
    }

    private fun padOffsetPointName(name: String, suffixIndex: Int): String {
        val base = name
            .removeSuffix("_OFFSET")
            .removeSuffix("_offset1")
            .removeSuffix("_offset2")
        return when (suffixIndex) {
            0 -> "${base}_OFFSET"
            else -> "${base}_offset$suffixIndex"
        }
    }
}
