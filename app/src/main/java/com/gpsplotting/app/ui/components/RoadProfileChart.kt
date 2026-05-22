package com.gpsplotting.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gpsplotting.core.CenterlineProfile
import kotlin.math.max
import kotlin.math.min

@Composable
fun RoadProfileChart(
    profile: CenterlineProfile,
    modifier: Modifier = Modifier,
    designElevationsFt: List<Double>? = null,
    designLabel: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surveyColor = colorScheme.primary
    val designColor = Color(0xFF00FF44)
    val gridColor = colorScheme.onSurface.copy(alpha = 0.12f)
    val axisColor = colorScheme.onSurface.copy(alpha = 0.45f)

    val stations = profile.stations
    val s0 = stations.first().stationFt
    val s1 = stations.last().stationFt
    val zSurveyLo = profile.minElevationFt
    val zSurveyHi = profile.maxElevationFt
    val zDesignLo = designElevationsFt?.minOrNull()
    val zDesignHi = designElevationsFt?.maxOrNull()
    val zLo = min(zSurveyLo, zDesignLo ?: zSurveyLo)
    val zHi = max(zSurveyHi, zDesignHi ?: zSurveyHi)
    val showDesign = designElevationsFt != null &&
        designElevationsFt.size == stations.size &&
        designLabel != null

    Column(modifier) {
        Text("Profile", style = MaterialTheme.typography.titleSmall)
        Text(
            "Δ elev (survey min→max): ${"%.2f".format(profile.elevationDeltaFt)} ft",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "Length ${"%.1f".format(profile.lengthFt)} ft · " +
                "Survey Z ${"%.2f".format(zSurveyLo)} – ${"%.2f".format(zSurveyHi)} ft · " +
                "${stations.size} shots",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant,
        )
        if (showDesign) {
            val dLo = designElevationsFt!!.min()
            val dHi = designElevationsFt.max()
            Text(
                designLabel!!,
                style = MaterialTheme.typography.bodySmall,
                color = designColor,
            )
            Text(
                "Design Z ${"%.2f".format(dLo)} – ${"%.2f".format(dHi)} ft · " +
                    "Δ ${"%.2f".format(dHi - dLo)} ft",
                style = MaterialTheme.typography.bodySmall,
                color = designColor,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        ) {
            val padLeft = 8f
            val padRight = 8f
            val padTop = 8f
            val padBottom = 8f
            val plotW = size.width - padLeft - padRight
            val plotH = size.height - padTop - padBottom
            if (plotW <= 0f || plotH <= 0f) return@Canvas

            val sRange = max(s1 - s0, 1.0)
            val zPad = max((zHi - zLo) * 0.1, 0.5)
            val zBottom = zLo - zPad
            val zTop = zHi + zPad
            val zRange = max(zTop - zBottom, 1.0)

            fun toX(station: Double): Float =
                padLeft + ((station - s0) / sRange * plotW).toFloat()

            fun toY(elev: Double): Float =
                padTop + plotH - ((elev - zBottom) / zRange * plotH).toFloat()

            fun drawPolyline(elevations: List<Double>, color: androidx.compose.ui.graphics.Color, width: Float) {
                val path = Path()
                stations.forEachIndexed { i, st ->
                    val pt = Offset(toX(st.stationFt), toY(elevations[i]))
                    if (i == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
                }
                drawPath(path, color, style = Stroke(width = width))
                stations.forEachIndexed { i, st ->
                    drawCircle(
                        color,
                        radius = if (width > 2.5f) 6f else 4f,
                        center = Offset(toX(st.stationFt), toY(elevations[i])),
                    )
                }
            }

            for (i in 0..4) {
                val y = padTop + plotH * i / 4f
                drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + plotW, y), strokeWidth = 1f)
            }
            for (i in 0..4) {
                val x = padLeft + plotW * i / 4f
                drawLine(gridColor, Offset(x, padTop), Offset(x, padTop + plotH), strokeWidth = 1f)
            }

            drawLine(axisColor, Offset(padLeft, padTop), Offset(padLeft, padTop + plotH), strokeWidth = 2f)
            drawLine(
                axisColor,
                Offset(padLeft, padTop + plotH),
                Offset(padLeft + plotW, padTop + plotH),
                strokeWidth = 2f,
            )

            drawPolyline(stations.map { it.elevationFt }, surveyColor, 3f)
            if (showDesign) {
                drawPolyline(designElevationsFt!!, designColor, 3.5f)
            }
        }
        Text(
            buildString {
                append("Blue = surveyed · ")
                if (showDesign) append("Green = autograde preview · ")
                append("Station 0 → ${"%.1f".format(s1)} ft (ft US)")
            },
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant,
        )
    }
}
