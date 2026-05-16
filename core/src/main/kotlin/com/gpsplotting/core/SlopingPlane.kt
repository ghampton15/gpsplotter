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
