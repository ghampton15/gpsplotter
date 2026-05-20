package com.gpsplotting.core

import kotlin.math.abs
import kotlin.math.hypot

/** Plan chainage along a centerline (feet); Z ignored for spacing. */
fun cumulativeStations(points: List<Point3>): List<Double> {
    if (points.isEmpty()) return emptyList()
    val stations = ArrayList<Double>(points.size)
    stations.add(0.0)
    var s = 0.0
    for (i in 1 until points.size) {
        s += hypot(
            points[i].easting - points[i - 1].easting,
            points[i].northing - points[i - 1].northing,
        )
        stations.add(s)
    }
    return stations
}

/** Least-squares slope m (ft/ft) and intercept b for Z ≈ m·s + b. */
fun leastSquaresLine(stations: List<Double>, z: List<Double>): Pair<Double, Double> {
    require(stations.size == z.size && stations.isNotEmpty()) {
        "Stations and elevations must be the same non-empty length."
    }
    val n = stations.size.toDouble()
    val sumS = stations.sum()
    val sumZ = z.sum()
    val sumS2 = stations.sumOf { it * it }
    val sumSZ = stations.zip(z) { s, zi -> s * zi }.sum()
    val denom = n * sumS2 - sumS * sumS
    if (abs(denom) < 1e-9) {
        throw IllegalArgumentException("Cannot fit grade: centerline has zero horizontal length.")
    }
    val m = (n * sumSZ - sumS * sumZ) / denom
    val b = (sumZ - m * sumS) / n
    return m to b
}

fun clampGradeSlope(m: Double, minGradePct: Double, maxGradePct: Double): Double {
    require(minGradePct <= maxGradePct) { "Min grade % must be <= max grade %." }
    val minM = minGradePct / 100.0
    val maxM = maxGradePct / 100.0
    return m.coerceIn(minM, maxM)
}

data class AutoGradeParams(
    val minGradePct: Double,
    val maxGradePct: Double,
) {
    init {
        require(minGradePct <= maxGradePct) { "Min grade % must be <= max grade %." }
    }
}

data class AutoGradeSummary(
    val gradePct: Double,
    val totalLengthFt: Double,
    val deltaElevFt: Double,
)

/**
 * Rebuilds centerline elevations from a constrained longitudinal grade.
 * Anchors Z at the first point to the surveyed elevation.
 */
fun applyAutoGradeProfile(
    center: List<Point3>,
    params: AutoGradeParams,
): Pair<List<Point3>, AutoGradeSummary> {
    require(center.size >= 2) { "Need at least two centerline points for auto-grade." }
    val stations = cumulativeStations(center)
    val zSurvey = center.map { it.elevation }
    val (mFit, _) = leastSquaresLine(stations, zSurvey)
    val m = clampGradeSlope(mFit, params.minGradePct, params.maxGradePct)
    val z0Survey = center[0].elevation
    val b = z0Survey - m * stations[0]
    val graded = center.mapIndexed { i, p ->
        Point3(p.easting, p.northing, roundFeet3(b + m * stations[i]))
    }
    val lastZ = graded.last().elevation
    val summary = AutoGradeSummary(
        gradePct = m * 100.0,
        totalLengthFt = stations.last(),
        deltaElevFt = roundFeet3(lastZ - z0Survey),
    )
    return graded to summary
}
