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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.LineOffset
import com.gpsplotting.core.PadOffsetCalculator
import com.gpsplotting.core.toPoint3
import com.gpsplotting.core.normalizeSurveyNumber
import com.gpsplotting.core.parseDoubleLenient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

private const val MODE_PADS = 0
private const val MODE_LINE = 1

private const val LINE_SIDE_LEFT = 0
private const val LINE_SIDE_RIGHT = 1

private const val INCHES_PER_FOOT = 12.0

@Composable
fun OffsetScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var prefix by remember { mutableStateOf("BLDG_") }
    var padDoubleStake by remember { mutableStateOf(false) }
    var padSetback1 by remember { mutableStateOf("5") }
    var padSetback2 by remember { mutableStateOf("10") }
    var status by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }

    var lineCode by remember { mutableStateOf("") }
    var lineSide by remember { mutableIntStateOf(LINE_SIDE_RIGHT) }
    var lineDoubleStake by remember { mutableStateOf(true) }
    var lineSetback1 by remember { mutableStateOf("2") }
    var lineSetback2 by remember { mutableStateOf("4") }
    var lineVerticalDist by remember { mutableStateOf("0") }
    var lineVerticalInInches by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> uri = u }
    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    if (showHelp) {
        OffsetHelpDialog(mode = mode, onDismiss = { showHelp = false })
    }

    ToolScaffold(
        title = "Offsets",
        nav = nav,
        actions = {
            TextButton(onClick = { showHelp = true }) {
                Text("?", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            PrimaryTabRow(selectedTabIndex = mode) {
                Tab(
                    selected = mode == MODE_PADS,
                    onClick = { mode = MODE_PADS },
                    text = { Text("Building pads") },
                )
                Tab(
                    selected = mode == MODE_LINE,
                    onClick = { mode = MODE_LINE },
                    text = { Text("Line offset") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { pick.launch(arrayOf("*/*")) }) { Text("Pick CSV") }
            Text(uri?.let { AppFiles.safeDisplayName(it) } ?: "No file")

            when (mode) {
                MODE_LINE -> {
                    OutlinedTextField(
                        value = lineCode,
                        onValueChange = { lineCode = it },
                        label = { Text("Point code (curb line)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Stake side (walking CSV order)", style = MaterialTheme.typography.bodySmall)
                    PrimaryTabRow(selectedTabIndex = lineSide) {
                        Tab(
                            selected = lineSide == LINE_SIDE_LEFT,
                            onClick = { lineSide = LINE_SIDE_LEFT },
                            text = { Text("Left") },
                        )
                        Tab(
                            selected = lineSide == LINE_SIDE_RIGHT,
                            onClick = { lineSide = LINE_SIDE_RIGHT },
                            text = { Text("Right") },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Double stake")
                        Switch(checked = lineDoubleStake, onCheckedChange = { lineDoubleStake = it })
                    }
                    OutlinedTextField(
                        value = lineSetback1,
                        onValueChange = { lineSetback1 = normalizeSurveyNumber(it) },
                        label = {
                            Text(if (lineDoubleStake) "Setback 1 (ft, signed)" else "Setback (ft, signed)")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    if (lineDoubleStake) {
                        OutlinedTextField(
                            value = lineSetback2,
                            onValueChange = { lineSetback2 = normalizeSurveyNumber(it) },
                            label = { Text("Setback 2 (ft, signed)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = decimalKeyboard,
                        )
                    }
                    LineOffsetDistanceField(
                        value = lineVerticalDist,
                        onValueChange = { lineVerticalDist = it },
                        label = if (lineVerticalInInches) "Vertical (in, signed)" else "Vertical (ft, signed)",
                        inInches = lineVerticalInInches,
                        keyboardOptions = decimalKeyboard,
                        showUnitToggle = true,
                        onToggleUnit = { lineVerticalInInches = !lineVerticalInInches },
                    )
                }
                else -> {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Double stake")
                        Switch(checked = padDoubleStake, onCheckedChange = { padDoubleStake = it })
                    }
                    OutlinedTextField(
                        value = padSetback1,
                        onValueChange = { padSetback1 = normalizeSurveyNumber(it) },
                        label = {
                            Text(if (padDoubleStake) "Setback 1 (ft)" else "Offset distance (ft)")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    if (padDoubleStake) {
                        OutlinedTextField(
                            value = padSetback2,
                            onValueChange = { padSetback2 = normalizeSurveyNumber(it) },
                            label = { Text("Setback 2 (ft)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = decimalKeyboard,
                        )
                    }
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = { prefix = it },
                        label = { Text("Code prefix") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    try {
                        val u = uri ?: error("Pick a CSV first.")
                        val rows = CsvIo.readSurveyRows(
                            ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)),
                        )
                        val stem = AppFiles.safeDisplayName(u).substringBeforeLast('.').ifEmpty { "export" }
                        when (mode) {
                            MODE_PADS -> {
                                val setback1 = parseOffsetDistanceToFeet(padSetback1, inInches = false, "Setback 1")
                                val setback2 = if (padDoubleStake) {
                                    parseOffsetDistanceToFeet(padSetback2, inInches = false, "Setback 2")
                                } else {
                                    null
                                }
                                val pads = PadOffsetCalculator.groupByBuildingPrefix(rows, prefix.uppercase())
                                require(pads.isNotEmpty()) {
                                    "No rows with Code starting with ${prefix.uppercase()}"
                                }
                                if (padDoubleStake) {
                                    val polylines = PadOffsetCalculator.buildPadOffsetPolylines(
                                        pads,
                                        setback1,
                                        setback2,
                                    ).map { (layer, pts) -> layer to pts.map { it.toPoint3() } }
                                    val name = "$stem-offset.dxf"
                                    val bytes = AppFiles.writeDxfToByteArray { os ->
                                        DxfWriter.writePolylinesDxf(os, polylines)
                                    }
                                    val path = AppFiles.saveBytesToPublicDownloads(
                                        ctx,
                                        name,
                                        bytes,
                                        "application/acad",
                                    )
                                    status = "Saved DXF (${pads.size} pad(s), OS_1 / OS_2):\n$path"
                                } else {
                                    val outPts = PadOffsetCalculator.generateOffsets(pads, setback1)
                                    val name = "${stem}_pad_offsets.csv"
                                    val bytes = ByteArrayOutputStream().also { os ->
                                        CsvIo.writeOffsetCsv(os, outPts)
                                    }.toByteArray()
                                    val path = AppFiles.saveBytesToPublicDownloads(ctx, name, bytes, "text/csv")
                                    status = "Saved:\n$path"
                                }
                            }
                            MODE_LINE -> {
                                val line = LineOffset.filterByCode(rows, lineCode)
                                require(line.isNotEmpty()) {
                                    "No points with code “${lineCode.trim()}”."
                                }
                                val side = if (lineSide == LINE_SIDE_LEFT) {
                                    LineOffset.LineSide.Left
                                } else {
                                    LineOffset.LineSide.Right
                                }
                                val setback1 = parseOffsetDistanceToFeet(lineSetback1, inInches = false, "Setback 1")
                                val setback2 = if (lineDoubleStake) {
                                    parseOffsetDistanceToFeet(lineSetback2, inInches = false, "Setback 2")
                                } else {
                                    null
                                }
                                val vertical = parseOptionalOffsetDistanceToFeet(lineVerticalDist, lineVerticalInInches)
                                val stakeLines = LineOffset.buildCurbStakeLines(
                                    line,
                                    setback1,
                                    setback2,
                                    side,
                                    vertical,
                                )
                                val polylines = stakeLines.map { stakeLine ->
                                    stakeLine.layerName to stakeLine.points.map { it.toPoint3() }
                                }
                                val name = "$stem-offset.dxf"
                                val bytes = AppFiles.writeDxfToByteArray { os ->
                                    DxfWriter.writePolylinesDxf(os, polylines)
                                }
                                val path = AppFiles.saveBytesToPublicDownloads(
                                    ctx,
                                    name,
                                    bytes,
                                    "application/acad",
                                )
                                val layerList = stakeLines.joinToString { it.layerName }
                                status = "Saved DXF ($layerList):\n$path"
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

@Composable
private fun LineOffsetDistanceField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    inInches: Boolean,
    keyboardOptions: KeyboardOptions,
    showUnitToggle: Boolean,
    onToggleUnit: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(if (inInches) it.trim() else normalizeSurveyNumber(it)) },
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            keyboardOptions = keyboardOptions,
        )
        if (showUnitToggle) {
            TextButton(onClick = onToggleUnit) {
                Text(if (inInches) "Feet" else "Inches")
            }
        }
    }
}

private fun parseOffsetDistanceToFeet(display: String, inInches: Boolean, label: String): Double {
    val raw = display.trim()
    if (raw.isEmpty()) throw IllegalArgumentException("Enter $label.")
    val v = parseDoubleLenient(raw)
        ?: throw IllegalArgumentException("$label must be a valid number.")
    return if (inInches) v / INCHES_PER_FOOT else v
}

private fun parseOptionalOffsetDistanceToFeet(display: String, inInches: Boolean): Double {
    val raw = display.trim()
    if (raw.isEmpty()) return 0.0
    val v = parseDoubleLenient(raw)
        ?: throw IllegalArgumentException("Vertical offset must be a valid number.")
    return if (inInches) v / INCHES_PER_FOOT else v
}

@Composable
private fun OffsetHelpDialog(mode: Int, onDismiss: () -> Unit) {
    val title = when (mode) {
        MODE_PADS -> "Building pads help"
        else -> "Line offset help"
    }
    val body = when (mode) {
        MODE_PADS ->
            "Rows whose Code starts with the prefix are grouped (e.g. BLDG_A, BLDG_B); order within each pad matters.\n\n" +
                "Single stake exports a CSV of offset corner points (_OFFSET).\n\n" +
                "Double stake exports a DXF with OS_1 / OS_2 layers — one ring per pad on each layer so rings are not connected."
        else ->
            "Parallel curb stakes: offset perpendicular to the line. Left/right is along CSV point order (first point → last).\n\n" +
                "Exports DXF with separate layers (OS_1 / OS_2) so double-stake lines are not connected."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
