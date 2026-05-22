package com.gpsplotting.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.app.ui.components.RoadProfileChart
import com.gpsplotting.core.AutoGradeParams
import com.gpsplotting.core.AutoGradeSummary
import com.gpsplotting.core.applyAutoGradeProfile
import com.gpsplotting.core.CenterlineProfile
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.RoadBuilder
import com.gpsplotting.core.buildCenterlineProfile
import com.gpsplotting.core.parseDoubleLenient
import com.gpsplotting.core.previewAutogradeElevations
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@Composable
fun RoadBuilderV2Screen(nav: NavHostController) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var width by remember { mutableStateOf("12") }
    var crossSlope by remember { mutableStateOf("2") }
    var code by remember { mutableStateOf("CEN") }
    var useAllRows by remember { mutableStateOf(false) }
    var endCaps by remember { mutableStateOf(true) }
    var minGrade by remember { mutableStateOf("0.3") }
    var maxGrade by remember { mutableStateOf("100") }
    var status by remember { mutableStateOf("") }
    var loadedProfile by remember { mutableStateOf<CenterlineProfile?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        uri = u
        loadedProfile = null
    }

    ToolScaffold("Road Builder V2 (AutoGrade)", nav) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pick centerline CSV") }
            Text(uri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
            OutlinedTextField(
                width,
                { width = it },
                label = { Text("Total width (ft)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                crossSlope,
                { crossSlope = it },
                label = { Text("Cross-slope (%)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(code, { code = it }, label = { Text("Code filter (blank = all)") }, modifier = Modifier.fillMaxWidth())
            RowSwitch("Use all rows (ignore Code)", useAllRows) { useAllRows = it }
            RowSwitch("End caps", endCaps) { endCaps = it }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    try {
                        val u = uri ?: error("Pick a CSV first.")
                        val rows = CsvIo.readSurveyRows(ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)))
                        val tag = codeTag(useAllRows, code)
                        loadedProfile = buildCenterlineProfile(rows, tag)
                        status = "Profile loaded: ${loadedProfile!!.stations.size} centerline shots."
                    } catch (e: Exception) {
                        loadedProfile = null
                        status = e.message ?: e.toString()
                    }
                },
            ) { Text("Load profile") }
            loadedProfile?.let { profile ->
                val autogradePreview = run {
                    val minPct = parseDoubleLenient(minGrade)
                    val maxPct = parseDoubleLenient(maxGrade)
                    if (minPct == null || maxPct == null || minPct > maxPct) {
                        null
                    } else {
                        try {
                            previewAutogradeElevations(
                                profile,
                                AutoGradeParams(minGradePct = minPct, maxGradePct = maxPct),
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                val autogradeLabel = autogradePreview?.let {
                    val minPct = parseDoubleLenient(minGrade)!!
                    val maxPct = parseDoubleLenient(maxGrade)!!
                    val summary = try {
                        applyAutogradeSummary(profile, minPct, maxPct)
                    } catch (_: Exception) {
                        null
                    }
                    if (summary != null) {
                        val len = profile.lengthFt
                        val dz = profile.elevationDeltaFt
                        val avgCut = if (len > 0) summary.approxCutAreaFt2 / len else 0.0
                        val avgFill = if (len > 0) summary.approxFillAreaFt2 / len else 0.0
                        buildString {
                            append("EG ΔZ ${"%.1f".format(dz)} ft / ${"%.0f".format(len)} ft road; ")
                            append("cut ${"%.0f".format(summary.approxCutAreaFt2)} / ")
                            append("fill ${"%.0f".format(summary.approxFillAreaFt2)} ft·ft ")
                            append("(~${"%.2f".format(avgCut)} / ${"%.2f".format(avgFill)} ft avg depth)")
                            if (maxPct < 25.0) {
                                append(", ±${"%.1f".format(maxPct)}% cap")
                            }
                            if (summary.fillLimitedScale < 0.999) {
                                append(" (fill capped, ")
                                append("${"%.0f".format(summary.fillLimitedScale * 100)}%)")
                            }
                        }
                    } else {
                        "Best-fit smooth profile preview"
                    }
                }
                Spacer(Modifier.height(8.dp))
                RoadProfileChart(
                    profile = profile,
                    modifier = Modifier.fillMaxWidth(),
                    designElevationsFt = autogradePreview,
                    designLabel = autogradeLabel,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Auto-grade profile", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                minGrade,
                { minGrade = it },
                label = { Text("Min grade (%) — drainage") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            OutlinedTextField(
                maxGrade,
                { maxGrade = it },
                label = { Text("Max grade (%) — optional cap (100 = off)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    try {
                        val u = uri ?: error("Pick a CSV first.")
                        val widthFt = parseDoubleLenient(width) ?: error("Invalid total width.")
                        val crossPct = parseDoubleLenient(crossSlope) ?: error("Invalid cross-slope.")
                        val minPct = parseDoubleLenient(minGrade) ?: error("Invalid min grade %.")
                        val maxPct = parseDoubleLenient(maxGrade) ?: error("Invalid max grade %.")
                        require(minPct <= maxPct) { "Min grade % must be <= max grade %." }
                        val rows = CsvIo.readSurveyRows(ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)))
                        val tag = codeTag(useAllRows, code)
                        val result = RoadBuilder.buildBreaklinesFromSurveyCsv(
                            rows,
                            RoadBuilder.RoadBreaklinesParams(
                                totalWidthFt = widthFt,
                                crossSlopePct = crossPct,
                                codeTag = tag,
                                addEndCaps = endCaps,
                                autoGrade = AutoGradeParams(
                                    minGradePct = minPct,
                                    maxGradePct = maxPct,
                                ),
                            ),
                        )
                        val summary = result.autoGradeSummary
                            ?: error("Auto-grade summary missing.")
                        val stem = AppFiles.safeDisplayName(u).substringBeforeLast('.').ifEmpty { "export" }
                        val name = "${stem}_breaklines_v2.dxf"
                        val bytes = ByteArrayOutputStream().also { os ->
                            DxfWriter.writeBreaklinesDxf(
                                os,
                                result.left,
                                result.center,
                                result.right,
                                DxfWriter.BreaklineLayers(
                                    left = result.layerLeft,
                                    center = result.layerCenter,
                                    right = result.layerRight,
                                    endCaps = result.layerEndCaps,
                                ),
                                addEndCaps = result.addEndCaps,
                            )
                        }.toByteArray()
                        val path = AppFiles.saveBytesToPublicDownloads(ctx, name, bytes, "application/acad")
                        val gradeStr = (summary.gradePct * 10.0).roundToInt() / 10.0
                        val lenStr = (summary.totalLengthFt * 10.0).roundToInt() / 10.0
                        val dzStr = summary.deltaElevFt
                        val cutStr = (summary.approxCutAreaFt2 * 10.0).roundToInt() / 10.0
                        val fillStr = (summary.approxFillAreaFt2 * 10.0).roundToInt() / 10.0
                        status = buildString {
                            appendLine("Grade: $gradeStr%")
                            appendLine("Road length: $lenStr ft")
                            appendLine("Survey rise start→end: $dzStr ft")
                            appendLine("Gross cut (shave highs): $cutStr ft·ft")
                            appendLine("Gross fill (raise lows): $fillStr ft·ft")
                            appendLine("(ft·ft = cut/fill depth × ft along road; gross, not net ΔZ)")
                            if (summary.fillLimitedScale < 0.999) {
                                appendLine(
                                    "Fill capped to ${(summary.fillLimitedScale * 100).toInt()}% " +
                                        "(not enough cut from existing)",
                                )
                            }
                            appendLine("Saved:")
                            append(path)
                        }
                    } catch (e: Exception) {
                        status = e.message ?: e.toString()
                    }
                },
            ) { Text("Create DXF") }
            Spacer(Modifier.height(8.dp))
            Text(status)
        }
    }
}

private fun applyAutogradeSummary(
    profile: CenterlineProfile,
    minPct: Double,
    maxPct: Double,
): AutoGradeSummary? =
    try {
        applyAutoGradeProfile(profile.centerline, AutoGradeParams(minPct, maxPct)).second
    } catch (_: Exception) {
        null
    }

private fun codeTag(useAllRows: Boolean, code: String): String? = when {
    useAllRows -> null
    code.isBlank() -> null
    else -> code.trim().lowercase()
}

@Composable
private fun RowSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
