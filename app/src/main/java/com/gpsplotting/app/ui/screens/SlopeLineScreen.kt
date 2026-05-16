package com.gpsplotting.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.ElevationGrade
import com.gpsplotting.core.Point3
import com.gpsplotting.core.SlopeLine
import com.gpsplotting.core.normalizeSurveyNumber
import com.gpsplotting.core.parseDoubleLenient
import com.gpsplotting.core.roundFeet3
import java.util.Locale

@Composable
fun SlopeLineScreen(nav: NavHostController) {
    val ctx = LocalContext.current

    var riseStr by remember { mutableStateOf("") }
    var runStr by remember { mutableStateOf("") }
    var gradeStr by remember { mutableStateOf("") }

    var se by remember { mutableStateOf("") }
    var sn by remember { mutableStateOf("") }
    var sz by remember { mutableStateOf("") }
    var az by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var stakeStatus by remember { mutableStateOf("") }

    var pendingExport by remember { mutableStateOf<Pair<String, ByteArray>?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingExport
        pendingExport = null
        if (pending != null) {
            val (name, bytes) = pending
            stakeStatus = try {
                if (granted) {
                    "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsLegacy(ctx, name, bytes)}"
                } else {
                    "Storage permission denied — cannot save to Downloads on Android 9 and older."
                }
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
        }
    }

    ToolScaffold("Elevation grade / stake", nav) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                riseStr,
                {
                    riseStr = normalizeSurveyNumber(it)
                    val (a, b, c) = syncGradeTriple(riseStr, runStr, gradeStr)
                    riseStr = a
                    runStr = b
                    gradeStr = c
                },
                label = { Text("Rise — vertical distance (ft)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                runStr,
                {
                    runStr = normalizeSurveyNumber(it)
                    val (a, b, c) = syncGradeTriple(riseStr, runStr, gradeStr)
                    riseStr = a
                    runStr = b
                    gradeStr = c
                },
                label = { Text("Run — horizontal distance (ft)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                gradeStr,
                {
                    gradeStr = normalizeSurveyNumber(it)
                    val (a, b, c) = syncGradeTriple(riseStr, runStr, gradeStr)
                    riseStr = a
                    runStr = b
                    gradeStr = c
                },
                label = { Text("Grade (%)") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "Stake & export: uses rise and run with start position and azimuth (° clockwise from north). " +
                    "Optional station interval splits the polyline along the run.",
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(se, { se = normalizeSurveyNumber(it) }, label = { Text("Start E") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sn, { sn = normalizeSurveyNumber(it) }, label = { Text("Start N") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(sz, { sz = normalizeSurveyNumber(it) }, label = { Text("Start Z") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(az, { az = normalizeSurveyNumber(it) }, label = { Text("Azimuth") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(interval, { interval = normalizeSurveyNumber(it) }, label = { Text("Station interval (ft, optional)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    try {
                        val s = resolveGradeInputs(riseStr, runStr, gradeStr)
                        riseStr = fmt(s.riseFt)
                        runStr = fmt(s.runFt)
                        gradeStr = fmt(s.gradePercent)

                        val start = Point3(
                            parseCoord("Start E", se),
                            parseCoord("Start N", sn),
                            parseCoord("Start Z", sz),
                        )
                        val azimuth = parseCoord("Azimuth", az)
                        val intervalFt = parseDoubleLenient(interval)

                        val r = SlopeLine.stake(
                            start,
                            azimuth,
                            s.runFt,
                            s.riseFt,
                            intervalFt,
                        )

                        val bytes = AppFiles.writeDxfToByteArray { os ->
                            DxfWriter.writePolylinesDxf(
                                os,
                                listOf("STAKE_LINE" to r.stations),
                            )
                        }
                        val fileName = "stake_line_${System.currentTimeMillis()}.dxf"

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            stakeStatus =
                                "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsMediaStore(ctx, fileName, bytes)}"
                        } else {
                            val ok = ContextCompat.checkSelfPermission(
                                ctx,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (ok) {
                                stakeStatus =
                                    "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsLegacy(ctx, fileName, bytes)}"
                            } else {
                                pendingExport = fileName to bytes
                                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                stakeStatus = "Allow storage access to save the file to Downloads…"
                            }
                        }
                    } catch (e: Exception) {
                        stakeStatus = e.message ?: e.toString()
                    }
                },
            ) { Text("Export DXF") }
            Spacer(Modifier.height(8.dp))
            Text(stakeStatus)
        }
    }
}

private fun parseCoord(label: String, s: String): Double {
    val t = normalizeSurveyNumber(s)
    val v = t.toDoubleOrNull()
        ?: throw IllegalArgumentException("$label must be a valid number (got \"$s\").")
    return v
}

/**
 * When exactly two of rise/run/grade parse as numbers, fills the third.
 * With zero or one value, or all three filled, leaves inputs unchanged so typing isn’t overwritten mid-edit.
 */
private fun syncGradeTriple(riseStr: String, runStr: String, gradeStr: String): Triple<String, String, String> {
    val r = parseDoubleLenient(riseStr)
    val ru = parseDoubleLenient(runStr)
    val g = parseDoubleLenient(gradeStr)
    val cnt = listOf(r, ru, g).count { it != null }
    if (cnt != 2) return Triple(riseStr, runStr, gradeStr)
    return try {
        val s = ElevationGrade.solve(r, ru, g)
        Triple(
            if (r == null) fmt(s.riseFt) else riseStr.trim(),
            if (ru == null) fmt(s.runFt) else runStr.trim(),
            if (g == null) fmt(s.gradePercent) else gradeStr.trim(),
        )
    } catch (_: Exception) {
        Triple(riseStr, runStr, gradeStr)
    }
}

/** Parse two or three inputs; if three, must be mutually consistent (Omni-style). */
private fun resolveGradeInputs(riseStr: String, runStr: String, gradeStr: String): ElevationGrade.Solution {
    val rise = parseDoubleLenient(riseStr)
    val run = parseDoubleLenient(runStr)
    val grade = parseDoubleLenient(gradeStr)
    val n = listOf(rise, run, grade).count { it != null }
    require(n >= 2) { "Enter at least two of: rise, run, grade %." }
    return when (n) {
        2 -> ElevationGrade.solve(rise, run, grade)
        3 -> {
            require(ElevationGrade.isConsistent(rise!!, run!!, grade!!)) {
                "Values are inconsistent with Grade = (rise÷run)×100 — clear one field or fix the numbers."
            }
            ElevationGrade.Solution(rise, run, grade)
        }
        else -> error("Enter at least two of: rise, run, grade %.")
    }
}

private fun fmt(v: Double): String {
    val r = roundFeet3(v)
    return String.format(Locale.US, "%.4f", r).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}
