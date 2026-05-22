package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Reproduces user CEN_1 sample for cut/fill diagnostics. */
class UserCsvCutFillTest {

    private val elevations = listOf(
        321.225, 321.454, 321.743, 322.096, 322.462, 322.753, 323.056, 323.361,
        324.108, 324.112, 324.881, 325.068, 325.044, 325.247, 325.465, 325.629,
        326.070, 326.311, 326.582, 326.792, 326.883, 326.860, 326.708,
    )

    private val eastings = listOf(
        377979.408, 377986.896, 377993.597, 378000.953, 378012.745, 378020.591,
        378029.312, 378037.810, 378048.443, 378048.444, 378060.382, 378067.845,
        378077.865, 378085.523, 378099.741, 378107.567, 378124.471, 378132.374,
        378149.059, 378162.750, 378176.080, 378186.958, 378201.753,
    )

    private val northings = listOf(
        924387.312, 924394.701, 924400.858, 924407.841, 924418.979, 924426.299,
        924434.613, 924442.705, 924452.741, 924452.772, 924464.094, 924471.226,
        924480.652, 924487.893, 924501.297, 924508.851, 924524.869, 924532.369,
        924548.114, 924561.049, 924573.690, 924584.019, 924598.150,
    )

    private fun centerline(): List<Point3> =
        elevations.indices.map { i ->
            Point3(eastings[i], northings[i], elevations[i])
        }

    @Test
    fun `user csv cut fill diagnostics`() {
        val center = centerline()
        val stations = cumulativeStations(center)
        val lengthFt = stations.last()
        val zSurvey = center.map { it.elevation }
        val z0 = zSurvey.first()
        val zEnd = zSurvey.last()
        val zChord = chordElevations(stations, z0, zEnd)
        val zSmooth = smoothInterior(zSurvey.toDoubleArray(), passes = 12, fixEnds = true)
        val leveled = buildLeveledProfile(zChord, zSmooth, 0.85, null, null)
        val afterSmooth = smoothInterior(leveled, passes = 4, fixEnds = true)
        val afterGrades = smoothGradesGently(stations, afterSmooth, z0, zEnd, 1.0)
        val afterDrain = enforceMinDrainageGrade(stations, afterGrades, z0, zEnd, 0.003)
        fun maxFill(z: DoubleArray): Double =
            z.indices.maxOfOrNull { fillDepthFt(zSurvey[it], z[it]) } ?: 0.0
        val (graded, summary) = applyAutoGradeProfile(
            center,
            AutoGradeParams(minGradePct = 0.3, maxGradePct = 100.0),
        )
        val zDesign = graded.map { it.elevation }.toDoubleArray()
        val deltaSurvey = zSurvey.last() - zSurvey.first()
        val deltaDesign = zDesign.last() - zDesign.first()

        var maxCutDepth = 0.0
        var maxFillDepth = 0.0
        for (i in zSurvey.indices) {
            maxCutDepth = maxOf(maxCutDepth, cutDepthFt(zSurvey[i], zDesign[i]))
            maxFillDepth = maxOf(maxFillDepth, fillDepthFt(zSurvey[i], zDesign[i]))
        }

        println("lengthFt=$lengthFt")
        println("survey ΔZ=$deltaSurvey design ΔZ=$deltaDesign")
        println("survey min-max=${zSurvey.minOrNull()}-${zSurvey.maxOrNull()}")
        println("cut ft·ft=${summary.approxCutAreaFt2} fill ft·ft=${summary.approxFillAreaFt2}")
        println("max cut depth at a shot=$maxCutDepth ft, max fill depth=$maxFillDepth ft")
        println("avg cut depth along road=${summary.approxCutAreaFt2 / lengthFt}")
        println("avg fill depth along road=${summary.approxFillAreaFt2 / lengthFt}")
        println("net dirt surplus (cut-fill)=${summary.approxCutAreaFt2 - summary.approxFillAreaFt2}")

        assertTrue(lengthFt > 200.0)
        val gross = summary.approxCutAreaFt2 + summary.approxFillAreaFt2
        assertTrue(
            maxFillDepth < 3.0 && gross < lengthFt * 0.5,
            "cut=${summary.approxCutAreaFt2} fill=${summary.approxFillAreaFt2} " +
                "maxFill=$maxFillDepth len=$lengthFt (user sample ~33/14 ft·ft vs ΔZ≈5.5 ft)",
        )
    }
}
