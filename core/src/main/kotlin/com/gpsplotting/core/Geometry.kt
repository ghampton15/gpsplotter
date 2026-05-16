package com.gpsplotting.core

import kotlin.math.abs
import kotlin.math.hypot

/** Plan coordinates + elevation (EPSG project ft US / NAVD88 ft US — passthrough). */
data class Point3(
    val easting: Double,
    val northing: Double,
    val elevation: Double,
)

data class SurveyPoint(
    val pointName: String,
    val easting: Double,
    val northing: Double,
    val elevation: Double,
    val code: String = "",
)

fun SurveyPoint.toPoint3(): Point3 = Point3(easting, northing, elevation)

fun Point3.withName(pointName: String, code: String = ""): SurveyPoint =
    SurveyPoint(pointName, easting, northing, elevation, code)

/** Azimuth clockwise from north (survey convention): dE = sin(θ)·h, dN = cos(θ)·h */
fun azimuthToDeltaEn(azimuthDegClockwiseFromNorth: Double, horizontalDistance: Double): Pair<Double, Double> {
    val r = Math.toRadians(azimuthDegClockwiseFromNorth)
    val dE = kotlin.math.sin(r) * horizontalDistance
    val dN = kotlin.math.cos(r) * horizontalDistance
    return dE to dN
}

/** Horizontal distance in feet between two plan positions. */
fun horizontalDistance(a: Point3, b: Point3): Double =
    hypot(b.easting - a.easting, b.northing - a.northing)

/** Inverse azimuth (degrees clockwise from north, 0–360). */
fun inverseAzimuthDegrees(from: Point3, to: Point3): Double {
    val dE = to.easting - from.easting
    val dN = to.northing - from.northing
    val rad = kotlin.math.atan2(dE, dN)
    var deg = Math.toDegrees(rad)
    if (deg < 0) deg += 360.0
    return deg
}

fun unitEn(vecE: Double, vecN: Double): Pair<Double, Double> {
    val h = hypot(vecE, vecN)
    require(h > 0.0) { "Zero-length segment: duplicate or coincident points." }
    return vecE / h to vecN / h
}

fun dedupeConsecutive(points: List<Point3>): List<Point3> {
    val out = mutableListOf<Point3>()
    for (p in points) {
        val last = out.lastOrNull()
        if (last != null &&
            last.easting == p.easting &&
            last.northing == p.northing &&
            last.elevation == p.elevation
        ) {
            continue
        }
        out.add(p)
    }
    return out
}

/**
 * Unit tangent in plan (E, N) at each vertex; interior uses bisector of adjacent chords
 * (matches [RoadBuilder/driveway_breaklines.py] tangents).
 */
fun tangents(points: List<Point3>): List<Pair<Double, Double>> {
    val n = points.size
    require(n >= 2) { "Need at least two distinct centerline points." }
    val tans = mutableListOf<Pair<Double, Double>>()
    for (i in 0 until n) {
        val te: Double
        val tn: Double
        when (i) {
            0 -> {
                val u = unitEn(
                    points[1].easting - points[0].easting,
                    points[1].northing - points[0].northing,
                )
                te = u.first
                tn = u.second
            }
            n - 1 -> {
                val u = unitEn(
                    points[n - 1].easting - points[n - 2].easting,
                    points[n - 1].northing - points[n - 2].northing,
                )
                te = u.first
                tn = u.second
            }
            else -> {
                val e0 = points[i].easting - points[i - 1].easting
                val n0 = points[i].northing - points[i - 1].northing
                val e1 = points[i + 1].easting - points[i].easting
                val n1 = points[i + 1].northing - points[i].northing
                val u = unitEn(e0 + e1, n0 + n1)
                te = u.first
                tn = u.second
            }
        }
        tans.add(te to tn)
    }
    return tans
}

fun roundFeet3(value: Double): Double =
    (kotlin.math.round(value * 1000.0) / 1000.0)

/**
 * Removes all whitespace so pasted values match Emlid-style grouping (e.g. `424 494.526`).
 */
fun normalizeSurveyNumber(input: String): String =
    input.filterNot { it.isWhitespace() }

fun parseDoubleLenient(input: String): Double? =
    normalizeSurveyNumber(input).toDoubleOrNull()

/** Normalize angle difference to (-180, 180]. */
internal fun angleDeltaDeg(a: Double, b: Double): Double {
    var d = (a - b) % 360.0
    if (d > 180) d -= 360
    if (d <= -180) d += 360
    return d
}

/** Are two doubles close (for tests). */
fun nearlyEqual(a: Double, b: Double, eps: Double = 1e-6): Boolean = abs(a - b) <= eps
