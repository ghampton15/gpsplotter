package com.gpsplotting.core

/**
 * Centerline → left/right edge breaklines with cross-slope (same geometry as [RoadBuilder/driveway_breaklines.py]).
 */
object RoadBuilder {

    fun rowMatchesEmlidCode(filterTagLower: String?, rowCode: String): Boolean {
        val rc = rowCode.trim().lowercase()
        if (filterTagLower == null) return true
        if (filterTagLower == "cen") {
            return rc == "cen" || Regex("^cen_\\d+$").matches(rc)
        }
        return rc == filterTagLower
    }

    fun filterCenterline(rows: List<SurveyPoint>, codeTag: String?): List<Point3> {
        val tag = codeTag?.trim()?.lowercase()
        val out = mutableListOf<Point3>()
        for (row in rows) {
            if (tag != null && !rowMatchesEmlidCode(tag, row.code)) continue
            out.add(row.toPoint3())
        }
        return out
    }

    fun offsetEdges(
        center: List<Point3>,
        halfWidth: Double,
        crossSlopePct: Double,
    ): Pair<List<Point3>, List<Point3>> {
        require(halfWidth > 0) { "Half-width must be positive." }
        require(crossSlopePct >= 0) { "Cross-slope percentage must be non-negative." }
        val dz = halfWidth * (crossSlopePct / 100.0)
        val tList = tangents(center)
        val left = mutableListOf<Point3>()
        val right = mutableListOf<Point3>()
        for (i in center.indices) {
            val (e, n, zc) = Triple(center[i].easting, center[i].northing, center[i].elevation)
            val (te, tn) = tList[i]
            val le = -tn
            val ln = te
            val re = tn
            val rn = -te
            left.add(
                Point3(
                    e + halfWidth * le,
                    n + halfWidth * ln,
                    zc - dz,
                ),
            )
            right.add(
                Point3(
                    e + halfWidth * re,
                    n + halfWidth * rn,
                    zc - dz,
                ),
            )
        }
        return left to right
    }

    data class RoadBreaklinesParams(
        val totalWidthFt: Double,
        val crossSlopePct: Double,
        val codeTag: String? = "cen",
        val layerLeft: String = "DRIVEWAY_LEFT",
        val layerCenter: String = "DRIVEWAY_CENTER",
        val layerRight: String = "DRIVEWAY_RIGHT",
        val layerEndCaps: String = "DRIVEWAY_END_CAPS",
        val addEndCaps: Boolean = true,
        val autoGrade: AutoGradeParams? = null,
    )

    fun buildBreaklinesFromSurveyCsv(rows: List<SurveyPoint>, params: RoadBreaklinesParams): RoadBreaklinesResult {
        val raw = filterCenterline(rows, params.codeTag)
        val centerSurvey = dedupeConsecutive(raw)
        require(centerSurvey.size >= 2) {
            val hint = if (params.codeTag == null) " (no Code filter)" else " (code=${params.codeTag})"
            "Need at least two centerline points$hint. Found ${centerSurvey.size}."
        }
        val autoGradeSummary = if (params.autoGrade != null) {
            val (graded, summary) = applyAutoGradeProfile(centerSurvey, params.autoGrade)
            buildFromCenter(graded, params, summary)
        } else {
            buildFromCenter(centerSurvey, params, null)
        }
        return autoGradeSummary
    }

    private fun buildFromCenter(
        center: List<Point3>,
        params: RoadBreaklinesParams,
        autoGradeSummary: AutoGradeSummary?,
    ): RoadBreaklinesResult {
        val halfW = params.totalWidthFt / 2.0
        val (left, right) = offsetEdges(center, halfW, params.crossSlopePct)
        return RoadBreaklinesResult(
            left = left,
            center = center,
            right = right,
            layerLeft = params.layerLeft,
            layerCenter = params.layerCenter,
            layerRight = params.layerRight,
            layerEndCaps = params.layerEndCaps,
            addEndCaps = params.addEndCaps,
            autoGradeSummary = autoGradeSummary,
        )
    }

    data class RoadBreaklinesResult(
        val left: List<Point3>,
        val center: List<Point3>,
        val right: List<Point3>,
        val layerLeft: String,
        val layerCenter: String,
        val layerRight: String,
        val layerEndCaps: String,
        val addEndCaps: Boolean,
        val autoGradeSummary: AutoGradeSummary? = null,
    )
}
