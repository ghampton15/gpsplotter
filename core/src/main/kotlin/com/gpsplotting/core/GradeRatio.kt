package com.gpsplotting.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Road-style horizontal-to-vertical ratio (run : rise), e.g. 33% grade → "3 to 1".
 */
object GradeRatio {

    fun format(gradePercent: Double): String? {
        if (gradePercent.isNaN() || abs(gradePercent) < 1e-9) return null
        return formatHorizontalToVertical(100.0 / abs(gradePercent))
    }

    fun formatFromRiseRun(riseFt: Double, runFt: Double): String? {
        if (abs(riseFt) < 1e-9 || abs(runFt) < 1e-9) return null
        return formatHorizontalToVertical(abs(runFt) / abs(riseFt))
    }

    fun formatForInputs(riseFtStr: String, runStr: String, gradeStr: String): String? {
        val rise = parseDoubleLenient(riseFtStr)
        val run = parseDoubleLenient(runStr)
        if (rise != null && run != null) {
            formatFromRiseRun(rise, run)?.let { return it }
        }
        val grade = parseDoubleLenient(gradeStr) ?: return null
        return format(grade)
    }

    private fun formatHorizontalToVertical(horizontalPerVertical: Double): String? {
        if (horizontalPerVertical.isNaN() || horizontalPerVertical.isInfinite() || horizontalPerVertical <= 0) {
            return null
        }
        val rounded = horizontalPerVertical.roundToInt()
        if (rounded >= 1 && abs(horizontalPerVertical - rounded) < 0.12) {
            return "$rounded to 1"
        }
        val tenths = (horizontalPerVertical * 10).roundToInt()
        if (tenths >= 10 && tenths % 10 == 0) {
            return "${tenths / 10} to 1"
        }
        val display = "%.1f".format(horizontalPerVertical).trimEnd('0').trimEnd('.')
        return "$display to 1"
    }
}
