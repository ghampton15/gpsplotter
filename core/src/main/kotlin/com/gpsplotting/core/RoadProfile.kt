package com.gpsplotting.core

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign

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
    /** Minimum drainage grade (%) on segments; default 0.3 in UI. */
    val minGradePct: Double,
    /** Soft upper limit on slope (%); use a large value (e.g. 100) to prioritize cut/fill fit. */
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
    /** Cut: design below existing (ft·ft); material removed. */
    val approxCutAreaFt2: Double,
    /** Fill: design above existing (ft·ft); material placed. */
    val approxFillAreaFt2: Double,
    val fillLimitedScale: Double,
)

/**
 * Design stays close to smoothed existing ground; lowers highs and raises lows toward the
 * tie chord between fixed ends. Balances cut/fill; fill cannot exceed cut from highs.
 */
fun applyAutoGradeProfile(
    center: List<Point3>,
    params: AutoGradeParams,
): Pair<List<Point3>, AutoGradeSummary> {
    require(center.size >= 2) { "Need at least two centerline points for auto-grade." }
    val stations = cumulativeStations(center)
    val zSurvey = center.map { it.elevation }
    val minM = params.minGradePct / 100.0
    val maxM = params.maxGradePct / 100.0
    val (zDesign, fillScale) = computeSmartDesignElevations(stations, zSurvey, minM, maxM)
    val graded = center.mapIndexed { i, p ->
        Point3(p.easting, p.northing, roundFeet3(zDesign[i]))
    }
    val (cutArea, fillArea) = cutFillAreaAlongProfile(stations, zSurvey, zDesign)
    val summary = AutoGradeSummary(
        gradePct = averageGradePct(stations, zDesign),
        totalLengthFt = stations.last(),
        deltaElevFt = roundFeet3(zDesign.last() - zDesign.first()),
        approxCutAreaFt2 = roundFeet3(cutArea),
        approxFillAreaFt2 = roundFeet3(fillArea),
        fillLimitedScale = fillScale,
    )
    return graded to summary
}

/** Soft symmetric slope cap when [maxGradePct] is set low; otherwise very wide. */
fun symmetricGradeSlopeBounds(params: AutoGradeParams): Pair<Double, Double> {
    val maxM = params.maxGradePct / 100.0
    return (-maxM) to maxM
}

internal fun computeSmartDesignElevations(
    stations: List<Double>,
    zSurvey: List<Double>,
    minM: Double,
    maxM: Double,
): Pair<DoubleArray, Double> {
    val n = zSurvey.size
    val z0 = zSurvey[0]
    val zEnd = zSurvey[n - 1]
    val zChord = chordElevations(stations, z0, zEnd)
    val zSmooth = smoothInterior(zSurvey.toDoubleArray(), passes = 12, fixEnds = true)
    val useTightGradeCap = maxM < 0.25
    val (zMin, zMax) = if (useTightGradeCap) {
        gradeFeasibleEnvelope(stations, z0, zEnd, -maxM, maxM)
    } else {
        null to null
    }

    var bestZ = zSmooth
    var bestScore = Double.POSITIVE_INFINITY
    for (candidate in levelCandidates(0.5, 1.0)) {
        val z = buildLeveledProfile(zChord, zSmooth, candidate, zMin, zMax)
        val score = profileObjective(stations, zSurvey, zSmooth, z, maxM)
        if (score < bestScore) {
            bestScore = score
            bestZ = z
        }
    }

    var zOut = smoothInterior(bestZ, passes = 4, fixEnds = true)
    if (useTightGradeCap && zMin != null && zMax != null) {
        zOut = projectToGradeEnvelope(zOut, zMin, zMax)
        zOut = smoothGradesGently(stations, zOut, z0, zEnd, maxM)
        if (minM > 0.0) {
            zOut = enforceMinDrainageGrade(stations, zOut, z0, zEnd, minM)
        }
    } else {
        if (minM > 0.0) {
            zOut = nudgeMinDrainageGrade(stations, zOut, z0, zEnd, minM)
        }
        zOut = smoothInterior(zOut, passes = 2, fixEnds = true)
    }
    zOut[0] = z0
    zOut[n - 1] = zEnd
    val (zBalanced, fillScale) = limitFillToAvailableCut(stations, zSurvey, zOut, z0, zEnd)
    return zBalanced to fillScale
}

/**
 * Follow existing shape [zSmooth] but pull highs down and lows up toward [zChord].
 * [level]=1 matches smoothed EG; lower values level more aggressively.
 */
internal fun buildLeveledProfile(
    zChord: DoubleArray,
    zSmooth: DoubleArray,
    level: Double,
    zMin: DoubleArray?,
    zMax: DoubleArray?,
): DoubleArray {
    val n = zChord.size
    val t = level.coerceIn(0.0, 1.0)
    return DoubleArray(n) { i ->
        var z = zChord[i] + t * (zSmooth[i] - zChord[i])
        if (zMin != null && zMax != null) z = z.coerceIn(zMin[i], zMax[i])
        z
    }
}

internal fun levelCandidates(lo: Double, hi: Double): List<Double> {
    val steps = listOf(
        lo,
        lo + (hi - lo) * 0.25,
        lo + (hi - lo) * 0.5,
        lo + (hi - lo) * 0.75,
        hi,
        0.55,
        0.65,
        0.75,
        0.85,
        0.92,
        0.97,
        1.0,
    )
    return steps.map { it.coerceIn(0.5, 1.0) }.distinct().sorted()
}

internal fun chordElevations(stations: List<Double>, z0: Double, zEnd: Double): DoubleArray {
    val lengthFt = stations.last()
    if (lengthFt <= 1e-9) return DoubleArray(stations.size) { z0 }
    return DoubleArray(stations.size) { i ->
        z0 + (zEnd - z0) * (stations[i] / lengthFt)
    }
}

internal fun smoothInterior(z: DoubleArray, passes: Int, fixEnds: Boolean): DoubleArray {
    if (z.size <= 2 || passes <= 0) return z.copyOf()
    val n = z.size
    val z0 = z[0]
    val zEnd = z[n - 1]
    var cur = z.copyOf()
    repeat(passes) {
        val next = cur.copyOf()
        for (i in 1 until n - 1) {
            next[i] = 0.12 * cur[i - 1] + 0.76 * cur[i] + 0.12 * cur[i + 1]
        }
        if (fixEnds) {
            next[0] = z0
            next[n - 1] = zEnd
        }
        cur = next
    }
    return cur
}

/** Light grade smoothing for a continuous road (not tied to max slope %). */
internal fun smoothGradesGently(
    stations: List<Double>,
    z: DoubleArray,
    z0: Double,
    zEnd: Double,
    maxM: Double,
): DoubleArray {
    val n = z.size
    if (n <= 2) return z.copyOf()
    val gradeCount = n - 1
    var grades = DoubleArray(gradeCount) { i ->
        val ds = stations[i + 1] - stations[i]
        if (ds < 1e-9) 0.0 else (z[i + 1] - z[i]) / ds
    }
    repeat(10) {
        val next = grades.copyOf()
        for (i in 1 until gradeCount - 1) {
            next[i] = 0.2 * grades[i - 1] + 0.6 * grades[i] + 0.2 * grades[i + 1]
        }
        grades = next
    }
    if (maxM < 0.25) {
        grades = grades.map { it.coerceIn(-maxM, maxM) }.toDoubleArray()
    }
    return integrateGradesToEnds(stations, grades, z0, zEnd)
}

/** Light drainage nudge without re-integrating the whole profile (used when max grade cap is off). */
internal fun nudgeMinDrainageGrade(
    stations: List<Double>,
    z: DoubleArray,
    z0: Double,
    zEnd: Double,
    minM: Double,
): DoubleArray {
    if (minM <= 0.0 || z.size <= 2) return z
    val n = z.size
    val out = z.copyOf()
    val fallbackDir = sign(zEnd - z0).let { if (it == 0.0) 1.0 else it }
    for (i in 0 until n - 1) {
        val ds = stations[i + 1] - stations[i]
        if (ds < 0.5) continue
        var grade = (out[i + 1] - out[i]) / ds
        if (abs(grade) < minM) {
            val dir = sign(grade).let { if (it == 0.0) fallbackDir else it }
            grade = dir * minM
            out[i + 1] = out[i] + grade * ds
        }
    }
    out[0] = z0
    out[n - 1] = zEnd
    return out
}

/** Nudge nearly-flat segments to at least [minM], keeping each segment's climb/descent direction. */
internal fun enforceMinDrainageGrade(
    stations: List<Double>,
    z: DoubleArray,
    z0: Double,
    zEnd: Double,
    minM: Double,
): DoubleArray {
    if (minM <= 0.0 || z.size <= 2) return z
    val n = z.size
    val out = z.copyOf()
    val fallbackDir = sign(zEnd - z0).let { if (it == 0.0) 1.0 else it }
    for (i in 0 until n - 1) {
        val ds = stations[i + 1] - stations[i]
        if (ds < 1e-9) continue
        var grade = (out[i + 1] - out[i]) / ds
        if (abs(grade) < minM) {
            val dir = sign(grade).let { if (it == 0.0) fallbackDir else it }
            grade = dir * minM
            out[i + 1] = out[i] + grade * ds
        }
    }
    out[0] = z0
    out[n - 1] = zEnd
    return out
}

internal fun integrateGradesToEnds(
    stations: List<Double>,
    grades: DoubleArray,
    z0: Double,
    zEnd: Double,
): DoubleArray {
    val n = stations.size
    val z = DoubleArray(n)
    z[0] = z0
    for (i in 0 until n - 1) {
        val ds = stations[i + 1] - stations[i]
        z[i + 1] = z[i] + grades[i] * ds
    }
    val lengthFt = stations.last()
    if (lengthFt <= 1e-9) {
        z[n - 1] = zEnd
        return z
    }
    val err = zEnd - z[n - 1]
    for (i in 1 until n) {
        z[i] += err * (stations[i] / lengthFt)
    }
    z[0] = z0
    z[n - 1] = zEnd
    return z
}

internal fun gradeFeasibleEnvelope(
    stations: List<Double>,
    z0: Double,
    zEnd: Double,
    minM: Double,
    maxM: Double,
): Pair<DoubleArray, DoubleArray> {
    val n = stations.size
    val zMaxF = DoubleArray(n)
    val zMinF = DoubleArray(n)
    zMaxF[0] = z0
    zMinF[0] = z0
    for (i in 0 until n - 1) {
        val ds = stations[i + 1] - stations[i]
        if (ds < 1e-9) {
            zMaxF[i + 1] = zMaxF[i]
            zMinF[i + 1] = zMinF[i]
            continue
        }
        zMaxF[i + 1] = zMaxF[i] + maxM * ds
        zMinF[i + 1] = zMinF[i] + minM * ds
    }
    val zMaxB = DoubleArray(n)
    val zMinB = DoubleArray(n)
    zMaxB[n - 1] = zEnd
    zMinB[n - 1] = zEnd
    for (i in n - 2 downTo 0) {
        val ds = stations[i + 1] - stations[i]
        if (ds < 1e-9) {
            zMaxB[i] = zMaxB[i + 1]
            zMinB[i] = zMinB[i + 1]
            continue
        }
        zMaxB[i] = zMaxB[i + 1] - minM * ds
        zMinB[i] = zMinB[i + 1] - maxM * ds
    }
    val zMin = DoubleArray(n) { i -> max(zMinF[i], zMinB[i]) }
    val zMax = DoubleArray(n) { i -> min(zMaxF[i], zMaxB[i]) }
    zMin[0] = z0
    zMax[0] = z0
    zMin[n - 1] = zEnd
    zMax[n - 1] = zEnd
    for (i in 0 until n) {
        if (zMin[i] > zMax[i]) {
            val mid = (zMin[i] + zMax[i]) / 2.0
            zMin[i] = mid
            zMax[i] = mid
        }
    }
    return zMin to zMax
}

internal fun projectToGradeEnvelope(z: DoubleArray, zMin: DoubleArray, zMax: DoubleArray): DoubleArray {
    return DoubleArray(z.size) { i -> z[i].coerceIn(zMin[i], zMax[i]) }
}

internal fun profileObjective(
    stations: List<Double>,
    zSurvey: List<Double>,
    zSmoothedTarget: DoubleArray,
    zDesign: DoubleArray,
    maxM: Double,
): Double {
    val (cut, fill) = cutFillAreaAlongProfile(stations, zSurvey, zDesign)
    val overFill = max(0.0, fill - cut)
    val imbalance = abs(cut - fill)
    val roughness = profileRoughnessPenalty(zDesign)
    var fit = 0.0
    for (i in zDesign.indices) {
        val d = zDesign[i] - zSmoothedTarget[i]
        fit += d * d
    }
    var gradePenalty = 0.0
    if (maxM < 0.25) {
        for (i in 0 until zDesign.size - 1) {
            val ds = stations[i + 1] - stations[i]
            if (ds < 1e-9) continue
            val g = abs((zDesign[i + 1] - zDesign[i]) / ds)
            if (g > maxM) gradePenalty += (g - maxM) * ds * 100.0
        }
    }
    return imbalance * 30.0 +
        overFill * 25.0 +
        roughness * 2.0 +
        fit * 1.5 +
        gradePenalty
}

internal fun limitFillToAvailableCut(
    stations: List<Double>,
    zSurvey: List<Double>,
    zDesign: DoubleArray,
    z0: Double,
    zEnd: Double,
): Pair<DoubleArray, Double> {
    val n = zDesign.size
    val (cut0, fill0) = cutFillAreaAlongProfile(stations, zSurvey, zDesign)
    if (fill0 <= cut0 + 1e-6) return zDesign.copyOf() to 1.0

    var lo = 0.0
    var hi = 1.0
    repeat(32) {
        val mid = (lo + hi) / 2.0
        val (cutMid, fillMid) = cutFillAreaAlongProfile(
            stations,
            zSurvey,
            applyFillScale(zSurvey, zDesign, mid, z0, zEnd),
        )
        if (fillMid > cutMid) lo = mid else hi = mid
    }
    val zOut = applyFillScale(zSurvey, zDesign, hi, z0, zEnd)
    zOut[0] = z0
    zOut[n - 1] = zEnd
    return zOut to hi
}

internal fun applyFillScale(
    zSurvey: List<Double>,
    zDesign: DoubleArray,
    scale: Double,
    z0: Double,
    zEnd: Double,
): DoubleArray {
    val n = zDesign.size
    return DoubleArray(n) { i ->
        when (i) {
            0 -> z0
            n - 1 -> zEnd
            else -> {
                val fillDepth = fillDepthFt(zSurvey[i], zDesign[i])
                if (fillDepth > 0.0) {
                    zSurvey[i] + scale * fillDepth
                } else {
                    zDesign[i]
                }
            }
        }
    }
}

internal fun profileRoughnessPenalty(z: DoubleArray): Double {
    if (z.size < 3) return 0.0
    var sum = 0.0
    for (i in 1 until z.size - 1) {
        val curv = z[i + 1] - 2.0 * z[i] + z[i - 1]
        sum += curv * curv
    }
    return sum
}

/** Existing above design → cut depth (ft); dirt removed. */
internal fun cutDepthFt(zExisting: Double, zDesign: Double): Double =
    max(0.0, zExisting - zDesign)

/** Design above existing → fill depth (ft); dirt placed. */
internal fun fillDepthFt(zExisting: Double, zDesign: Double): Double =
    max(0.0, zDesign - zExisting)

/**
 * Cut/fill along chainage (ft·ft proxy).
 * Cut and fill are summed separately (never netted on one segment).
 */
internal fun cutFillAreaAlongProfile(
    stations: List<Double>,
    zSurvey: List<Double>,
    zDesign: DoubleArray,
): Pair<Double, Double> {
    var cut = 0.0
    var fill = 0.0
    for (i in 0 until zSurvey.size - 1) {
        val ds = stations[i + 1] - stations[i]
        if (ds < 0.5) continue
        val (segCut, segFill) = segmentCutFillArea(
            cutDepthFt(zSurvey[i], zDesign[i]),
            cutDepthFt(zSurvey[i + 1], zDesign[i + 1]),
            fillDepthFt(zSurvey[i], zDesign[i]),
            fillDepthFt(zSurvey[i + 1], zDesign[i + 1]),
            ds,
        )
        cut += segCut
        fill += segFill
    }
    return cut to fill
}

/**
 * Trapezoid cut/fill on one segment; splits at the crossing when one end is cut and one is fill.
 */
internal fun segmentCutFillArea(
    cut0: Double,
    cut1: Double,
    fill0: Double,
    fill1: Double,
    ds: Double,
): Pair<Double, Double> {
    if (ds <= 0.0) return 0.0 to 0.0

    val hasCut = cut0 > 1e-9 || cut1 > 1e-9
    val hasFill = fill0 > 1e-9 || fill1 > 1e-9
    if (!hasCut && !hasFill) return 0.0 to 0.0

    if (!hasFill) {
        return trapezoidArea(cut0, cut1, ds) to 0.0
    }
    if (!hasCut) {
        return 0.0 to trapezoidArea(fill0, fill1, ds)
    }

    val net0 = fill0 - cut0
    val net1 = fill1 - cut1
    if (net0 * net1 >= 0.0) {
        return trapezoidArea(cut0, cut1, ds) to trapezoidArea(fill0, fill1, ds)
    }

    val t = (net0 / (net0 - net1)).coerceIn(0.0, 1.0)
    val splitLen = t * ds
    val tailLen = ds - splitLen
    return if (net0 > 0.0) {
        trapezoidArea(0.0, cut1, tailLen) to trapezoidArea(fill0, 0.0, splitLen)
    } else {
        trapezoidArea(cut0, 0.0, splitLen) to trapezoidArea(0.0, fill1, tailLen)
    }
}

internal fun trapezoidArea(depth0: Double, depth1: Double, length: Double): Double =
    (depth0 + depth1) / 2.0 * length

internal fun averageGradePct(stations: List<Double>, z: DoubleArray): Double {
    var weighted = 0.0
    var length = 0.0
    for (i in 0 until z.size - 1) {
        val ds = stations[i + 1] - stations[i]
        if (ds <= 1e-9) continue
        weighted += ((z[i + 1] - z[i]) / ds) * ds
        length += ds
    }
    return if (length > 0.0) weighted / length * 100.0 else 0.0
}

internal fun countInteriorExtrema(z: DoubleArray): Int {
    if (z.size < 3) return 0
    var count = 0
    for (i in 1 until z.size - 1) {
        if (z[i] > z[i - 1] && z[i] > z[i + 1]) count++
        if (z[i] < z[i - 1] && z[i] < z[i + 1]) count++
    }
    return count
}
