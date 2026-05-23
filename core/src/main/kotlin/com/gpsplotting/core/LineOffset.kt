package com.gpsplotting.core

/**
 * Open polyline offsets: filter by code (CSV order), perpendicular curb stakes, vertical shift.
 */
object LineOffset {

    /** Left/right relative to walking the line from first CSV point to last. */
    enum class LineSide {
        Left,
        Right,
    }

    fun filterByCode(rows: List<SurveyPoint>, code: String): List<SurveyPoint> {
        val codeTrim = code.trim()
        require(codeTrim.isNotEmpty()) { "Enter a point code to filter the line." }
        return rows.filter { it.code.trim().equals(codeTrim, ignoreCase = true) }
    }

    /** Parallel offset perpendicular to the line at each vertex (same convention as [RoadBuilder.offsetEdges]). */
    fun offsetPerpendicular(
        points: List<SurveyPoint>,
        distanceFeet: Double,
        side: LineSide,
    ): List<SurveyPoint> {
        requireLine(points)
        val pts3 = points.map { it.toPoint3() }
        val tList = tangents(pts3)
        return points.mapIndexed { i, p ->
            val (te, tn) = tList[i]
            val (offsetE, offsetN) = when (side) {
                LineSide.Left -> -tn to te
                LineSide.Right -> tn to -te
            }
            p.copy(
                easting = roundFeet3(p.easting + distanceFeet * offsetE),
                northing = roundFeet3(p.northing + distanceFeet * offsetN),
            )
        }
    }

    data class CurbStakeLine(
        val layerName: String,
        val points: List<SurveyPoint>,
    )

    /**
     * One or two separate stake polylines (for DXF layers). Does not concatenate lines.
     * [setback2Feet] null = single line; else offset1 and offset2 as separate lists.
     */
    fun buildCurbStakeLines(
        points: List<SurveyPoint>,
        setback1Feet: Double,
        setback2Feet: Double?,
        side: LineSide,
        deltaElevationFeet: Double,
    ): List<CurbStakeLine> {
        requireLine(points)
        val use1 = setback1Feet != 0.0 || setback2Feet == null
        val use2 = setback2Feet != null
        require(use1 || use2 || deltaElevationFeet != 0.0) {
            "Enter a setback and/or vertical offset."
        }
        val lines = mutableListOf<CurbStakeLine>()
        if (use1) {
            lines.add(
                CurbStakeLine(
                    layerName = "OS_1",
                    points = stakesWithSuffix(
                        offsetPerpendicular(points, setback1Feet, side),
                        deltaElevationFeet,
                        suffixIndex = 1,
                    ),
                ),
            )
        }
        if (use2) {
            lines.add(
                CurbStakeLine(
                    layerName = "OS_2",
                    points = stakesWithSuffix(
                        offsetPerpendicular(points, setback2Feet!!, side),
                        deltaElevationFeet,
                        suffixIndex = 2,
                    ),
                ),
            )
        }
        return lines
    }

    /** Flat point list (CSV); use [buildCurbStakeLines] when lines must stay separate. */
    fun buildCurbStakes(
        points: List<SurveyPoint>,
        setback1Feet: Double,
        setback2Feet: Double?,
        side: LineSide,
        deltaElevationFeet: Double,
    ): List<SurveyPoint> =
        buildCurbStakeLines(points, setback1Feet, setback2Feet, side, deltaElevationFeet)
            .flatMap { it.points }

    fun offsetElevation(points: List<SurveyPoint>, deltaFeet: Double): List<SurveyPoint> =
        buildCurbStakes(
            points,
            setback1Feet = 0.0,
            setback2Feet = null,
            side = LineSide.Right,
            deltaElevationFeet = deltaFeet,
        )

    private fun stakesWithSuffix(
        points: List<SurveyPoint>,
        deltaElevationFeet: Double,
        suffixIndex: Int,
    ): List<SurveyPoint> =
        points.map { p ->
            p.copy(
                pointName = stakePointName(p.pointName, suffixIndex),
                elevation = roundFeet3(p.elevation + deltaElevationFeet),
            )
        }

    private fun stakePointName(name: String, suffixIndex: Int): String {
        val base = name
            .removeSuffix("_offset1")
            .removeSuffix("_offset2")
            .removeSuffix("_OFFSET")
        return "${base}_offset$suffixIndex"
    }

    private fun requireLine(points: List<SurveyPoint>) {
        require(points.size >= 2) { "Need at least two points on the line (found ${points.size})." }
    }
}
