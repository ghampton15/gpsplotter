package com.gpsplotting.core

import kotlin.math.hypot

/**
 * Planar surface Z(E,N) = a*E + b*N + c for grading helpers.
 */
data class PlaneModel(
    /** dZ/dE (ft per ft). */
    val slopeE: Double,
    /** dZ/dN (ft per ft). */
    val slopeN: Double,
    val interceptZ: Double,
    val anchorE: Double = 0.0,
    val anchorN: Double = 0.0,
) {
    fun zAt(easting: Double, northing: Double): Double =
        interceptZ + slopeE * (easting - anchorE) + slopeN * (northing - anchorN)

    fun zAt(p: Point3): Double = zAt(p.easting, p.northing)

    /** Magnitude of gradient |∇Z| = sqrt(se^2+sn^2); grade% along steepest descent ~ 100*|∇Z|. */
    fun gradientMagnitude(): Double = hypot(slopeE, slopeN)

    fun maxGradePercent(): Double = 100.0 * gradientMagnitude()
}

object SlopingPlane {

    const val MATCH_CORNER_CODE = "Match"

    enum class PadSurfaceMode {
        /** Four corners keep surveyed Z (quad TIN). */
        MatchCorners,
        /** Constant grade; 0 or 1 Match tie-in. */
        SlopingPlane,
    }

    data class BuildPadSurfaceResult(
        val corners: List<Point3>,
        val detailLines: List<String>,
        val plane: PlaneModel? = null,
        val azimuthDegClockwiseFromNorth: Double? = null,
    )

    fun isMatchCornerCode(code: String): Boolean =
        code.trim().equals(MATCH_CORNER_CODE, ignoreCase = true)

    fun surveyCornersToPoint3(corners: List<SurveyPoint>): List<Point3> =
        corners.map {
            Point3(roundFeet3(it.easting), roundFeet3(it.northing), roundFeet3(it.elevation))
        }

    /** Plane through [through] with grade % along [azimuthDegClockwiseFromNorth]. */
    fun planeThroughPointWithGrade(
        through: Point3,
        azimuthDegClockwiseFromNorth: Double,
        gradePercent: Double,
    ): PlaneModel = planeFromAnchorSingleSlope(through, azimuthDegClockwiseFromNorth, gradePercent)

    fun buildPadSurfaceCorners(
        corners: List<SurveyPoint>,
        start: SurveyPoint?,
        end: SurveyPoint?,
        gradePercent: Double,
        mode: PadSurfaceMode,
    ): BuildPadSurfaceResult {
        require(corners.size == 4) { "Need exactly four pad corners." }
        val matchCount = corners.count { isMatchCornerCode(it.code) }
        when (matchCount) {
            2, 3 -> error(
                "Use four Match corners, or one Match with slope A, slope B, and grade %.",
            )
        }
        return when (mode) {
            PadSurfaceMode.MatchCorners -> buildMatchCorners(corners)
            PadSurfaceMode.SlopingPlane -> buildSlopingPlane(corners, start, end, gradePercent, matchCount)
        }
    }

    private fun buildMatchCorners(corners: List<SurveyPoint>): BuildPadSurfaceResult {
        require(corners.all { isMatchCornerCode(it.code) }) {
            "Match corners mode requires all four corners coded $MATCH_CORNER_CODE."
        }
        val pts = surveyCornersToPoint3(corners)
        val warnings = coplanarityWarning(pts)
        return BuildPadSurfaceResult(
            corners = pts,
            detailLines = listOf("Surface: surveyed Z at all four corners (quad TIN).") + warnings,
        )
    }

    private fun buildSlopingPlane(
        corners: List<SurveyPoint>,
        start: SurveyPoint?,
        end: SurveyPoint?,
        gradePercent: Double,
        matchCount: Int,
    ): BuildPadSurfaceResult {
        val a = start ?: error("slope A is required for sloping plane export.")
        val b = end ?: error("slope B is required for sloping plane export.")
        val anchorPt = Point3(a.easting, a.northing, a.elevation)
        val endPt = Point3(b.easting, b.northing, b.elevation)
        val azimuth = inverseAzimuthDegrees(anchorPt, endPt)
        val ens = corners.map { it.easting to it.northing }
        val plane = when (matchCount) {
            0 -> planeFromAnchorSingleSlope(anchorPt, azimuth, gradePercent)
            1 -> {
                val matchPt = corners.single { isMatchCornerCode(it.code) }
                planeThroughPointWithGrade(
                    Point3(matchPt.easting, matchPt.northing, matchPt.elevation),
                    azimuth,
                    gradePercent,
                )
            }
            else -> error(
                "Use four Match corners, or one Match with slope A, slope B, and grade %.",
            )
        }
        val cornerPts = cornersWithPlaneZ(ens, plane)
        val modeLabel = if (matchCount == 0) {
            "Sloping plane from slope A"
        } else {
            "Plane through Match tie-in"
        }
        return BuildPadSurfaceResult(
            corners = cornerPts,
            detailLines = listOf(
                modeLabel,
                "Azimuth A→B: ${"%.4f".format(azimuth)}° CW from N",
                "Grade: ${"%.3f".format(plane.maxGradePercent())}%",
            ),
            plane = plane,
            azimuthDegClockwiseFromNorth = azimuth,
        )
    }

    private fun coplanarityWarning(corners: List<Point3>): List<String> {
        if (corners.size < 4) return emptyList()
        return try {
            val plane = fitPlaneThroughThreePoints(corners[0], corners[1], corners[2])
            val maxDelta = corners.maxOf { kotlin.math.abs(plane.zAt(it) - it.elevation) }
            if (maxDelta > 0.05) {
                listOf(
                    "Corners are not quite coplanar (max ΔZ ${"%.3f".format(maxDelta)} ft); " +
                        "TIN uses two flat triangles.",
                )
            } else {
                emptyList()
            }
        } catch (_: IllegalArgumentException) {
            emptyList()
        }
    }

    /** Fit Z = a*E + b*N + c through three non-collinear points (unique plane unless degenerate). */
    fun fitPlaneThroughThreePoints(p1: Point3, p2: Point3, p3: Point3): PlaneModel =
        solvePlaneLinear(
            p1.easting, p1.northing, p1.elevation,
            p2.easting, p2.northing, p2.elevation,
            p3.easting, p3.northing, p3.elevation,
        )

    private fun solvePlaneLinear(
        e1: Double, n1: Double, z1: Double,
        e2: Double, n2: Double, z2: Double,
        e3: Double, n3: Double, z3: Double,
    ): PlaneModel {
        // Gaussian elimination for a,b,c in  e*a + n*b + c = z
        val m = arrayOf(
            doubleArrayOf(e1, n1, 1.0, z1),
            doubleArrayOf(e2, n2, 1.0, z2),
            doubleArrayOf(e3, n3, 1.0, z3),
        )
        for (col in 0..2) {
            var pivot = col
            for (r in col + 1 until 3) {
                if (kotlin.math.abs(m[r][col]) > kotlin.math.abs(m[pivot][col])) pivot = r
            }
            val tmp = m[col]
            m[col] = m[pivot]
            m[pivot] = tmp
            val piv = m[col][col]
            require(kotlin.math.abs(piv) > 1e-12) { "Cannot fit plane (singular matrix)." }
            for (j in col..3) m[col][j] /= piv
            for (r in 0 until 3) {
                if (r == col) continue
                val f = m[r][col]
                if (kotlin.math.abs(f) < 1e-15) continue
                for (j in col..3) m[r][j] -= f * m[col][j]
            }
        }
        val a = m[0][3]
        val b = m[1][3]
        return PlaneModel(
            slopeE = a,
            slopeN = b,
            interceptZ = z1,
            anchorE = e1,
            anchorN = n1,
        )
    }

    data class FixThreeSolveFourResult(
        val plane: PlaneModel,
        val fourthCornerIndex: Int,
        val measuredZ: Double,
        val planeZ: Double,
        val deltaZ: Double,
        val cornersZComputed: List<Point3>,
    )

    /**
     * Hold three corners at measured elevations; fourth corner gets plane Z from the fitted plane.
     * [cornerIndicesHeld] must be three distinct indices 0..3.
     */
    fun fixThreeSolveFour(corners: List<Point3>, fourthFreeIndex: Int): FixThreeSolveFourResult {
        require(corners.size == 4) { "Need exactly four corners." }
        require(fourthFreeIndex in 0..3) { "fourthFreeIndex must be 0..3." }
        val held = (0..3).filter { it != fourthFreeIndex }.take(3)
        val p1 = corners[held[0]]
        val p2 = corners[held[1]]
        val p3 = corners[held[2]]
        val plane = fitPlaneThroughThreePoints(p1, p2, p3)
        val free = corners[fourthFreeIndex]
        val planeZ = plane.zAt(free)
        val measuredZ = free.elevation
        val updatedCorners = corners.mapIndexed { i, p ->
            if (i == fourthFreeIndex) Point3(p.easting, p.northing, roundFeet3(planeZ)) else p
        }
        return FixThreeSolveFourResult(
            plane = plane,
            fourthCornerIndex = fourthFreeIndex,
            measuredZ = measuredZ,
            planeZ = planeZ,
            deltaZ = planeZ - measuredZ,
            cornersZComputed = updatedCorners,
        )
    }

    /**
     * Single main-axis slope from anchor: Z increases along [mainAzimuthDeg] by [mainSlopePercent]/100 per ft horizontal.
     */
    fun planeFromAnchorSingleSlope(
        anchor: Point3,
        mainAzimuthDegClockwiseFromNorth: Double,
        mainSlopePercent: Double,
    ): PlaneModel {
        val r = Math.toRadians(mainAzimuthDegClockwiseFromNorth)
        val uE = kotlin.math.sin(r)
        val uN = kotlin.math.cos(r)
        val g = mainSlopePercent / 100.0
        return PlaneModel(
            slopeE = g * uE,
            slopeN = g * uN,
            interceptZ = anchor.elevation,
            anchorE = anchor.easting,
            anchorN = anchor.northing,
        )
    }

    /**
     * Rotary-laser style: main slope along [mainAzimuthDeg], cross slope along main+90°.
     * Z = Za + (mainPct/100)*projMain + (crossPct/100)*projCross.
     */
    fun planeFromAnchorDualSlope(
        anchor: Point3,
        mainAzimuthDegClockwiseFromNorth: Double,
        mainSlopePercent: Double,
        crossSlopePercent: Double,
    ): PlaneModel {
        val rm = Math.toRadians(mainAzimuthDegClockwiseFromNorth)
        val uMainE = kotlin.math.sin(rm)
        val uMainN = kotlin.math.cos(rm)
        val rc = Math.toRadians(mainAzimuthDegClockwiseFromNorth + 90.0)
        val uCrossE = kotlin.math.sin(rc)
        val uCrossN = kotlin.math.cos(rc)
        val gm = mainSlopePercent / 100.0
        val gc = crossSlopePercent / 100.0
        val slopeE = gm * uMainE + gc * uCrossE
        val slopeN = gm * uMainN + gc * uCrossN
        return PlaneModel(
            slopeE = slopeE,
            slopeN = slopeN,
            interceptZ = anchor.elevation,
            anchorE = anchor.easting,
            anchorN = anchor.northing,
        )
    }

    /** Apply plane Z to each corner (plan positions fixed). */
    fun cornersWithPlaneZ(cornersEn: List<Pair<Double, Double>>, plane: PlaneModel): List<Point3> =
        cornersEn.map { (e, n) ->
            Point3(roundFeet3(e), roundFeet3(n), roundFeet3(plane.zAt(e, n)))
        }

    /**
     * Axis-aligned rectangle in plan from min/max E,N and corner sampling order [sw, se, ne, nw] optional —
     * here: four corners in survey order around the pad.
     */
    fun triangulateQuad(points: List<Point3>): Pair<IntArray, IntArray> {
        require(points.size == 4) { "Quad needs 4 points." }
        // Two triangles: 0-1-2 and 0-2-3 (indices 1-based in LandXML)
        val f1 = intArrayOf(1, 2, 3)
        val f2 = intArrayOf(1, 3, 4)
        return f1 to f2
    }
}
