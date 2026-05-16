package com.gpsplotting.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
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
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.RoadBuilder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream

@Composable
fun RoadBuilderScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var width by remember { mutableStateOf("12") }
    var crossSlope by remember { mutableStateOf("2") }
    var code by remember { mutableStateOf("CEN") }
    var useAllRows by remember { mutableStateOf(false) }
    var endCaps by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> uri = u }

    ToolScaffold("Road breaklines", nav) { padding ->
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
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    try {
                        val u = uri ?: error("Pick a CSV first.")
                        val rows = CsvIo.readSurveyRows(ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)))
                        val tag = when {
                            useAllRows -> null
                            code.isBlank() -> null
                            else -> code.trim().lowercase()
                        }
                        val result = RoadBuilder.buildBreaklinesFromSurveyCsv(
                            rows,
                            RoadBuilder.RoadBreaklinesParams(
                                totalWidthFt = width.toDouble(),
                                crossSlopePct = crossSlope.toDouble(),
                                codeTag = tag,
                                addEndCaps = endCaps,
                            ),
                        )
                        val stem = AppFiles.safeDisplayName(u).substringBeforeLast('.').ifEmpty { "export" }
                        val outFile = File(AppFiles.defaultOutputDir(ctx), "${stem}_breaklines.dxf")
                        FileOutputStream(outFile).use { os ->
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
                        }
                        status = "Saved:\n${outFile.absolutePath}"
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
