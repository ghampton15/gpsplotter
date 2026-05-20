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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.AutoGradeParams
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.RoadBuilder
import com.gpsplotting.core.parseDoubleLenient
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
    var minGrade by remember { mutableStateOf("1") }
    var maxGrade by remember { mutableStateOf("12") }
    var status by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> uri = u }

    ToolScaffold("Road Builder V2 (AutoGrade)", nav) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pick centerline CSV") }
            Text(uri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
            OutlinedTextField(width, { width = it }, label = { Text("Total width (ft)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(crossSlope, { crossSlope = it }, label = { Text("Cross-slope (%)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(code, { code = it }, label = { Text("Code filter (blank = all)") }, modifier = Modifier.fillMaxWidth())
            RowSwitch("Use all rows (ignore Code)", useAllRows) { useAllRows = it }
            RowSwitch("End caps", endCaps) { endCaps = it }
            Spacer(Modifier.height(8.dp))
            Text("Auto-grade profile", style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
            OutlinedTextField(minGrade, { minGrade = it }, label = { Text("Min grade (%)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(maxGrade, { maxGrade = it }, label = { Text("Max grade (%)") }, modifier = Modifier.fillMaxWidth())
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
                        val tag = when {
                            useAllRows -> null
                            code.isBlank() -> null
                            else -> code.trim().lowercase()
                        }
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
                        status = buildString {
                            appendLine("Grade: $gradeStr%")
                            appendLine("Length: $lenStr ft")
                            appendLine("ΔZ: $dzStr ft")
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
