package com.gpsplotting.core

import kotlin.math.abs

/**
 * Elevation grade like [Omni elevation grade](https://www.omnicalculator.com/construction/elevation-grade):
 * **Grade (%) = (rise / run) × 100**, where rise is vertical distance and run is horizontal distance.
 * No angle-of-elevation — that is left to field software if needed.
 */
object ElevationGrade {

    data class Solution(
        /** Vertical distance (ft). */
        val riseFt: Double,
        /** Horizontal distance (ft). */
        val runFt: Double,
        /** Grade in percent. */
        val gradePercent: Double,
    )

    /**
     * Given exactly two of [rise], [run], or [gradePercent] (non-null), computes the third.
     */
    fun solve(
        rise: Double?,
        run: Double?,
        gradePercent: Double?,
    ): Solution {
        val present = listOf(rise, run, gradePercent).count { it != null }
        require(present == 2) { "Enter exactly two of: rise, run, grade %." }

        return when {
            rise != null && run != null -> {
                require(abs(run) > 1e-12) { "Run cannot be zero." }
                Solution(rise, run, 100.0 * rise / run)
            }
            rise != null && gradePercent != null -> {
                require(abs(gradePercent) > 1e-12) { "Grade % is zero — run would be undefined for a non-zero rise." }
                val rRun = rise / (gradePercent / 100.0)
                Solution(rise, rRun, gradePercent)
            }
            run != null && gradePercent != null -> {
                require(abs(run) > 1e-12) { "Run cannot be zero." }
                val rRise = run * (gradePercent / 100.0)
                Solution(rRise, run, gradePercent)
            }
            else -> error("Enter exactly two of: rise, run, grade %.")
        }
    }

    /** True if the three values satisfy grade ≈ 100×rise/run (within tolerance). */
    fun isConsistent(rise: Double, run: Double, gradePercent: Double, epsRatio: Double = 1e-5): Boolean {
        if (abs(run) <= 1e-12) return abs(rise) <= 1e-12 && abs(gradePercent) <= 1e-12
        val expectedGrade = 100.0 * rise / run
        return abs(expectedGrade - gradePercent) <= maxOf(1e-9, abs(expectedGrade) * epsRatio)
    }

    /**
     * “1 in N” horizontal for one unit of rise: N = run / rise (Omni-style wording).
     * Returns null if rise is (near) zero.
     */
    fun oneInHorizontalForUnitRise(runFt: Double, riseFt: Double): Double? {
        if (abs(riseFt) <= 1e-12) return null
        return runFt / riseFt
    }
}
