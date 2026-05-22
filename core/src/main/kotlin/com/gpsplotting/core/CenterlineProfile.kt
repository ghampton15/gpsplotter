package com.gpsplotting.core

/**
 * Longitudinal profile along a surveyed centerline (station vs elevation).
 */
data class ProfileStation(
    val index: Int,
    val stationFt: Double,
    val elevationFt: Double,
)

data class CenterlineProfile(
    val centerline: List<Point3>,
    val stations: List<ProfileStation>,
) {
    val lengthFt: Double get() = stations.lastOrNull()?.stationFt ?: 0.0
    val minElevationFt: Double get() = stations.minOfOrNull { it.elevationFt } ?: 0.0
    val maxElevationFt: Double get() = stations.maxOfOrNull { it.elevationFt } ?: 0.0
    /** Max surveyed Z minus min surveyed Z along the centerline. */
    val elevationDeltaFt: Double get() = maxElevationFt - minElevationFt
}

/** Filter CSV rows, dedupe, and build chainage + survey Z for profile display. */
fun buildCenterlineProfile(rows: List<SurveyPoint>, codeTag: String?): CenterlineProfile {
    val raw = RoadBuilder.filterCenterline(rows, codeTag)
    val center = dedupeConsecutive(raw)
    require(center.size >= 2) {
        val hint = if (codeTag == null) " (no Code filter)" else " (code=$codeTag)"
        "Need at least two centerline points$hint. Found ${center.size}."
    }
    val chainage = cumulativeStations(center)
    val stations = center.mapIndexed { i, p ->
        ProfileStation(
            index = i,
            stationFt = chainage[i],
            elevationFt = p.elevation,
        )
    }
    return CenterlineProfile(centerline = center, stations = stations)
}

/** Proposed centerline Z from autograde settings (same stations as survey). */
fun previewAutogradeElevations(
    profile: CenterlineProfile,
    params: AutoGradeParams,
): List<Double> {
    val (graded, _) = applyAutoGradeProfile(profile.centerline, params)
    return graded.map { it.elevation }
}

/** Grade % between consecutive profile stations (length = stations.size - 1). */
fun segmentGradesPct(profile: CenterlineProfile): List<Double> {
    val s = profile.stations
    if (s.size < 2) return emptyList()
    return (0 until s.lastIndex).map { i ->
        val run = s[i + 1].stationFt - s[i].stationFt
        if (run <= 0.0) 0.0 else (s[i + 1].elevationFt - s[i].elevationFt) / run * 100.0
    }
}
