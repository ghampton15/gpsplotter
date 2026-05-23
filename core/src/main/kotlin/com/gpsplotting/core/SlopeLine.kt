package com.gpsplotting.core

/**
 * Stake a 3D polyline using horizontal **run** and vertical **rise** (Omni-style: grade % = rise/run×100).
 */
object SlopeLine {

    data class StakeResult(
        val stations: List<Point3>,
    )

    /**
     * From [start], move horizontally [runFt] along [azimuthDegClockwiseFromNorth] and apply total elevation change [riseFt].
     * Stations are linearly interpolated: at fraction t along the horizontal path, ΔZ = t × riseFt.
     */
    fun stake(
        start: Point3,
        azimuthDegClockwiseFromNorth: Double,
        runFt: Double,
        riseFt: Double,
        stationIntervalFt: Double? = null,
    ): StakeResult {
        require(runFt >= 0) { "Run (horizontal distance) must be non-negative." }
        val (dE, dN) = azimuthToDeltaEn(azimuthDegClockwiseFromNorth, runFt)
        val end = Point3(
            roundFeet3(start.easting + dE),
            roundFeet3(start.northing + dN),
            roundFeet3(start.elevation + riseFt),
        )
        val interval = stationIntervalFt
        if (interval == null || interval <= 0 || runFt == 0.0) {
            return StakeResult(listOf(start, end))
        }
        val pts = mutableListOf<Point3>()
        var d = 0.0
        while (d < runFt - 1e-9) {
            val t = d / runFt
            pts.add(
                Point3(
                    roundFeet3(start.easting + dE * t),
                    roundFeet3(start.northing + dN * t),
                    roundFeet3(start.elevation + riseFt * t),
                ),
            )
            d += interval
        }
        pts.add(end)
        return StakeResult(pts)
    }

    /**
     * Line from [start] to [end] plan position with elevation at start from survey.
     * [gradePercent] is signed along start → end (+ rises toward end, − falls toward end).
     */
    fun stakeBetweenPoints(
        start: Point3,
        end: Point3,
        gradePercent: Double,
        stationIntervalFt: Double? = null,
    ): StakeResult {
        val runFt = horizontalDistance(start, end)
        require(runFt > 1e-9) { "Start and end have the same plan position (zero run)." }
        val riseFt = runFt * (gradePercent / 100.0)
        val azimuth = inverseAzimuthDegrees(start, end)
        return stake(start, azimuth, runFt, riseFt, stationIntervalFt)
    }
}
