package com.gpsplotting.app.ui.screens

import android.net.Uri
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.OffsetCalculator
import com.gpsplotting.core.PadOffsetCalculator
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Composable
fun OffsetScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var offsetFt by remember { mutableStateOf("5") }
    var prefix by remember { mutableStateOf("BLDG_") }
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> uri = u }

    ToolScaffold("Offsets", nav) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PrimaryTabRow(selectedTabIndex = mode) {
                Tab(
                    selected = mode == 0,
                    onClick = { mode = 0 },
                    text = { Text("Closed polygon") },
                )
                Tab(
                    selected = mode == 1,
                    onClick = { mode = 1 },
                    text = { Text("Building pads") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when (mode) {
                    0 -> "All CSV rows in order form one closed loop (same as original polygon offset)."
                    else -> "Rows whose Code starts with the prefix are grouped (e.g. BLDG_A, BLDG_B); order within each pad matters."
                },
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pick CSV") }
            Text(uri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
            OutlinedTextField(
                value = offsetFt,
                onValueChange = { offsetFt = it },
                label = { Text("Offset distance (ft)") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == 1) {
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it },
                    label = { Text("Code prefix") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    try {
                        val u = uri ?: error("Pick a CSV first.")
                        val rows = CsvIo.readSurveyRows(ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)))
                        val dist = offsetFt.toDouble()
                        val stem = AppFiles.safeDisplayName(u).substringBeforeLast('.').ifEmpty { "export" }
                        when (mode) {
                            0 -> {
                                val outPts = OffsetCalculator.generateOffsets(rows, dist)
                                val name = "${stem}_offsets.csv"
                                val bytes = ByteArrayOutputStream().also { os -> CsvIo.writeOffsetCsv(os, outPts) }.toByteArray()
                                val path = AppFiles.saveBytesToPublicDownloads(ctx, name, bytes, "text/csv")
                                status = "Saved:\n$path"
                            }
                            else -> {
                                val pads = PadOffsetCalculator.groupByBuildingPrefix(rows, prefix.uppercase())
                                require(pads.isNotEmpty()) { "No rows with Code starting with ${prefix.uppercase()}" }
                                val outPts = PadOffsetCalculator.generateOffsets(pads, dist)
                                val name = "${stem}_pad_offsets.csv"
                                val bytes = ByteArrayOutputStream().also { os -> CsvIo.writeOffsetCsv(os, outPts) }.toByteArray()
                                val path = AppFiles.saveBytesToPublicDownloads(ctx, name, bytes, "text/csv")
                                status = "Saved:\n$path"
                            }
                        }
                    } catch (e: Exception) {
                        status = e.message ?: e.toString()
                    }
                },
            ) { Text("Run & save") }
            Spacer(Modifier.height(8.dp))
            Text(status)
        }
    }
}
