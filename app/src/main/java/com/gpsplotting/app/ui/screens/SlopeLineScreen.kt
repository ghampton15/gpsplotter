package com.gpsplotting.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.DxfWriter
import com.gpsplotting.core.ElevationGrade
import com.gpsplotting.core.GradeRatio
import com.gpsplotting.core.GradeTripleSync
import com.gpsplotting.core.InchFraction
import com.gpsplotting.core.Point3
import com.gpsplotting.core.SlopeLine
import com.gpsplotting.core.SurveyPoint
import com.gpsplotting.core.normalizeSurveyNumber
import com.gpsplotting.core.toPoint3
import com.gpsplotting.core.parseDoubleLenient
import com.gpsplotting.core.roundFeet3
import java.io.ByteArrayInputStream
import java.util.Locale

@Composable
fun SlopeLineScreen(nav: NavHostController) {
    val ctx = LocalContext.current

    var riseFtStr by remember { mutableStateOf("") }
    /** What the rise field shows while typing (feet or inches/fractions text). */
    var riseEditStr by remember { mutableStateOf("") }
    var runStr by remember { mutableStateOf("") }
    var gradeStr by remember { mutableStateOf("") }
    var gradeSyncAnchor by remember { mutableStateOf(GradeTripleSync.Anchor.Auto) }
    var riseInInches by remember { mutableStateOf(false) }
    var riseLocked by remember { mutableStateOf(false) }
    var runLocked by remember { mutableStateOf(false) }
    var gradeLocked by remember { mutableStateOf(false) }
    var gradeFieldFocused by remember { mutableStateOf(false) }

    var se by remember { mutableStateOf("") }
    var sn by remember { mutableStateOf("") }
    var sz by remember { mutableStateOf("") }
    var az by remember { mutableStateOf("") }
    var interval by remember { mutableStateOf("") }
    var stakeStatus by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    var stakeTab by remember { mutableIntStateOf(0) }

    var startCsvUri by remember { mutableStateOf<Uri?>(null) }
    var startCsvCode by remember { mutableStateOf("") }
    var startCsvLoaded by remember { mutableStateOf<SurveyPoint?>(null) }
    val pickStartCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        startCsvUri = u
        startCsvLoaded = null
    }

    var dualCsvUri by remember { mutableStateOf<Uri?>(null) }
    var dualStartPt by remember { mutableStateOf<SurveyPoint?>(null) }
    var dualEndPt by remember { mutableStateOf<SurveyPoint?>(null) }
    var dualGradePct by remember { mutableStateOf("") }
    var dualInterval by remember { mutableStateOf("") }
    val pickDualCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        dualCsvUri = u
        dualStartPt = null
        dualEndPt = null
    }

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

    val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    fun lockedFields(): Set<GradeTripleSync.Field> = buildSet {
        if (riseLocked) add(GradeTripleSync.Field.Rise)
        if (runLocked) add(GradeTripleSync.Field.Run)
        if (gradeLocked) add(GradeTripleSync.Field.Grade)
    }

    fun applyGradeField(editing: GradeTripleSync.Field, raw: String) {
        if (editing == GradeTripleSync.Field.Rise && riseLocked) return
        if (editing == GradeTripleSync.Field.Run && runLocked) return
        if (editing == GradeTripleSync.Field.Grade && gradeLocked) return

        if (raw.isBlank()) {
            when (editing) {
                GradeTripleSync.Field.Rise -> {
                    riseFtStr = ""
                    riseEditStr = ""
                }
                GradeTripleSync.Field.Run -> runStr = ""
                GradeTripleSync.Field.Grade -> gradeStr = ""
            }
            return
        }

        val riseFt = when (editing) {
            GradeTripleSync.Field.Rise -> {
                val parsed = parseRiseDisplayToFeetStr(raw, riseInInches)
                when {
                    parsed.isNotEmpty() -> parsed
                    raw.isBlank() -> ""
                    else -> riseFtStr
                }
            }
            else -> riseFtStr
        }
        val run = if (editing == GradeTripleSync.Field.Run) normalizeSurveyNumber(raw) else runStr
        val grade = if (editing == GradeTripleSync.Field.Grade) {
            normalizeSurveyNumber(raw.substringBefore(" (").substringBefore("(").trim())
        } else {
            gradeStr
        }
        gradeSyncAnchor = GradeTripleSync.resolveAnchor(gradeSyncAnchor, editing, riseFt, run, grade)
        val (riseFtOut, runOut, gradeOut) = GradeTripleSync.syncWithLocks(
            riseFt,
            run,
            grade,
            editing,
            lockedFields(),
            gradeSyncAnchor,
        )
        riseFtStr = riseFtOut
        if (editing != GradeTripleSync.Field.Rise) {
            riseEditStr = formatRiseForDisplay(riseFtOut, riseInInches)
        }
        runStr = runOut
        gradeStr = gradeOut
    }

    if (showHelp) {
        SlopeLineHelpDialog { showHelp = false }
    }

    ToolScaffold(
        title = "Elevation grade / stake",
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    riseEditStr,
                    {
                        riseEditStr = if (riseInInches) it.trim() else normalizeSurveyNumber(it)
                        applyGradeField(GradeTripleSync.Field.Rise, riseEditStr)
                    },
                    label = { Text(if (riseInInches) "Rise (in)" else "Rise (ft)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = decimalKeyboard,
                    readOnly = riseLocked,
                )
                TextButton(
                    onClick = {
                        riseInInches = !riseInInches
                        riseEditStr = formatRiseForDisplay(riseFtStr, riseInInches)
                    },
                ) {
                    Text(if (riseInInches) "Feet" else "Inches")
                }
                LockToggle(locked = riseLocked, onToggle = { riseLocked = !riseLocked })
            }
            GradeLockedField(
                value = runStr,
                onValueChange = { applyGradeField(GradeTripleSync.Field.Run, it) },
                label = "Run (ft)",
                locked = runLocked,
                onToggleLock = { runLocked = !runLocked },
                keyboardOptions = decimalKeyboard,
            )
            val gradeRatio = GradeRatio.formatForInputs(riseFtStr, runStr, gradeStr)
            val gradeFieldText = when {
                gradeFieldFocused || gradeStr.isBlank() || gradeRatio == null -> gradeStr
                else -> "$gradeStr ($gradeRatio)"
            }
            GradeLockedField(
                value = gradeFieldText,
                onValueChange = { applyGradeField(GradeTripleSync.Field.Grade, it) },
                label = "Grade (%)",
                locked = gradeLocked,
                onToggleLock = { gradeLocked = !gradeLocked },
                keyboardOptions = decimalKeyboard,
                modifier = Modifier.onFocusChanged { gradeFieldFocused = it.isFocused },
            )

            Spacer(Modifier.height(20.dp))
            PrimaryTabRow(selectedTabIndex = stakeTab) {
                Tab(selected = stakeTab == 0, onClick = { stakeTab = 0 }, text = { Text("Single point") })
                Tab(selected = stakeTab == 1, onClick = { stakeTab = 1 }, text = { Text("Dual point") })
            }
            Spacer(Modifier.height(12.dp))

            when (stakeTab) {
                0 -> {
                    Button(onClick = { pickStartCsv.launch(arrayOf("text/*", "text/csv", "*/*")) }) {
                        Text("Pick CSV for start")
                    }
                    Text(startCsvUri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
                    OutlinedTextField(
                        startCsvCode,
                        { startCsvCode = it },
                        label = { Text("Start point code") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            try {
                                val pt = loadSurveyPointByCode(ctx, startCsvUri, startCsvCode)
                                startCsvLoaded = pt
                                se = fmt(pt.easting)
                                sn = fmt(pt.northing)
                                sz = fmt(pt.elevation)
                                stakeStatus =
                                    "Start loaded: ${pt.pointName}  E ${roundFeet3(pt.easting)}  " +
                                        "N ${roundFeet3(pt.northing)}  Z ${roundFeet3(pt.elevation)}"
                            } catch (e: Exception) {
                                startCsvLoaded = null
                                stakeStatus = e.message ?: e.toString()
                            }
                        },
                    ) { Text("Load start from CSV") }
                    startCsvLoaded?.let { pt ->
                        Text(
                            "CSV start: ${pt.pointName} (${pt.code}) — edit fields below if needed",
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        se,
                        { se = normalizeSurveyNumber(it) },
                        label = { Text("Start E") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    OutlinedTextField(
                        sn,
                        { sn = normalizeSurveyNumber(it) },
                        label = { Text("Start N") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    OutlinedTextField(
                        sz,
                        { sz = normalizeSurveyNumber(it) },
                        label = { Text("Start Z") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    OutlinedTextField(
                        az,
                        { az = normalizeSurveyNumber(it) },
                        label = { Text("Azimuth") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    OutlinedTextField(
                        interval,
                        { interval = normalizeSurveyNumber(it) },
                        label = { Text("Station interval (ft, optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val s = resolveGradeInputs(riseFtStr, runStr, gradeStr)
                                riseFtStr = fmt(s.riseFt)
                                riseEditStr = formatRiseForDisplay(riseFtStr, riseInInches)
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
                                exportStakeDxf(
                                    ctx,
                                    "stake_line_${System.currentTimeMillis()}.dxf",
                                    r.stations,
                                    { stakeStatus = it },
                                    { pendingExport = it },
                                    permissionLauncher,
                                )
                            } catch (e: Exception) {
                                stakeStatus = e.message ?: e.toString()
                            }
                        },
                    ) { Text("Export DXF") }
                }
                1 -> {
                    Button(onClick = { pickDualCsv.launch(arrayOf("text/*", "text/csv", "*/*")) }) {
                        Text("Pick CSV")
                    }
                    Text(dualCsvUri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
                    Text(
                        "Start = code A, end = code B (case-insensitive)",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Button(
                        onClick = {
                            try {
                                val rows = readSurveyCsv(ctx, dualCsvUri)
                                dualStartPt = findSurveyPointByCode(rows, DUAL_START_CODE, "Start")
                                dualEndPt = findSurveyPointByCode(rows, DUAL_END_CODE, "End")
                                stakeStatus =
                                    "Loaded start ${dualStartPt!!.pointName} and end ${dualEndPt!!.pointName}"
                            } catch (e: Exception) {
                                dualStartPt = null
                                dualEndPt = null
                                stakeStatus = e.message ?: e.toString()
                            }
                        },
                    ) { Text("Load both points") }
                    if (dualStartPt != null && dualEndPt != null) {
                        Text(
                            "Start: ${dualStartPt!!.pointName} (${dualStartPt!!.code})  " +
                                "E ${roundFeet3(dualStartPt!!.easting)}  N ${roundFeet3(dualStartPt!!.northing)}  " +
                                "Z ${roundFeet3(dualStartPt!!.elevation)}",
                        )
                        Text(
                            "End: ${dualEndPt!!.pointName} (${dualEndPt!!.code})  " +
                                "E ${roundFeet3(dualEndPt!!.easting)}  N ${roundFeet3(dualEndPt!!.northing)}  " +
                                "Z ${roundFeet3(dualEndPt!!.elevation)} (plan; Z adjusted on export)",
                        )
                        TextButton(
                            onClick = {
                                val sPt = dualStartPt
                                val ePt = dualEndPt
                                if (sPt != null && ePt != null) {
                                    dualStartPt = ePt
                                    dualEndPt = sPt
                                }
                            },
                        ) {
                            Text("Swap start ↔ end")
                        }
                    }
                    OutlinedTextField(
                        dualGradePct,
                        { dualGradePct = normalizeSurveyNumber(it) },
                        label = { Text("Grade % (start → end)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    OutlinedTextField(
                        dualInterval,
                        { dualInterval = normalizeSurveyNumber(it) },
                        label = { Text("Station interval (ft, optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = decimalKeyboard,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            try {
                                val startPt = dualStartPt ?: error("Load start and end points from CSV.")
                                val endPt = dualEndPt ?: error("Load start and end points from CSV.")
                                val grade = parseCoord("Grade %", dualGradePct)
                                val intervalFt = parseDoubleLenient(dualInterval)
                                val r = SlopeLine.stakeBetweenPoints(
                                    startPt.toPoint3(),
                                    endPt.toPoint3(),
                                    grade,
                                    intervalFt,
                                )
                                exportStakeDxf(
                                    ctx,
                                    "stake_dual_${System.currentTimeMillis()}.dxf",
                                    r.stations,
                                    { stakeStatus = it },
                                    { pendingExport = it },
                                    permissionLauncher,
                                )
                            } catch (e: Exception) {
                                stakeStatus = e.message ?: e.toString()
                            }
                        },
                    ) { Text("Export DXF") }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(stakeStatus)
        }
    }
}

@Composable
private fun GradeLockedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    locked: Boolean,
    onToggleLock: () -> Unit,
    keyboardOptions: KeyboardOptions,
    modifier: Modifier = Modifier,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value,
            onValueChange,
            label = { Text(label) },
            modifier = modifier.weight(1f),
            keyboardOptions = keyboardOptions,
            readOnly = locked,
            singleLine = true,
        )
        LockToggle(locked = locked, onToggle = onToggleLock)
    }
}

@Composable
private fun LockToggle(locked: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (locked) Icons.Filled.Lock else Icons.Outlined.LockOpen,
            contentDescription = if (locked) "Unlock" else "Lock",
        )
    }
}

private const val INCHES_PER_FOOT = 12.0
private const val DUAL_START_CODE = "A"
private const val DUAL_END_CODE = "B"

private fun formatRiseForDisplay(feetStr: String, inInches: Boolean): String {
    if (feetStr.isBlank()) return ""
    val ft = parseDoubleLenient(feetStr) ?: return feetStr
    return if (inInches) InchFraction.format(ft * INCHES_PER_FOOT) else fmt(ft)
}

private fun parseRiseDisplayToFeetStr(display: String, inInches: Boolean): String {
    if (display.isBlank()) return ""
    return if (inInches) {
        val inches = InchFraction.parseLenient(display) ?: return ""
        fmt(inches / INCHES_PER_FOOT)
    } else {
        val feet = parseDoubleLenient(display) ?: return ""
        fmt(feet)
    }
}

private fun parseCoord(label: String, s: String): Double {
    val t = normalizeSurveyNumber(s)
    val v = t.toDoubleOrNull()
        ?: throw IllegalArgumentException("$label must be a valid number (got \"$s\").")
    return v
}

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

private fun readSurveyCsv(ctx: Context, uri: Uri?): List<SurveyPoint> {
    val u = uri ?: error("Pick a CSV first.")
    return CsvIo.readSurveyRows(ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)))
}

private fun findSurveyPointByCode(rows: List<SurveyPoint>, code: String, label: String): SurveyPoint {
    val codeTrim = code.trim()
    require(codeTrim.isNotEmpty()) { "Enter the $label point code." }
    val matches = rows.filter { it.code.trim().equals(codeTrim, ignoreCase = true) }
    require(matches.size == 1) {
        "$label code “$codeTrim”: need exactly 1 point (found ${matches.size})."
    }
    return matches.single()
}

private fun loadSurveyPointByCode(ctx: Context, uri: Uri?, code: String): SurveyPoint =
    findSurveyPointByCode(readSurveyCsv(ctx, uri), code, "Start")

private fun exportStakeDxf(
    ctx: Context,
    fileName: String,
    stations: List<Point3>,
    setStatus: (String) -> Unit,
    setPending: (Pair<String, ByteArray>?) -> Unit,
    permissionLauncher: ActivityResultLauncher<String>,
) {
    val bytes = AppFiles.writeDxfToByteArray { os ->
        DxfWriter.writePolylinesDxf(os, listOf("STAKE_LINE" to stations))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setStatus("Saved to Downloads:\n${AppFiles.saveBytesToDownloadsMediaStore(ctx, fileName, bytes, "application/acad")}")
    } else {
        val ok = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) {
            setStatus("Saved to Downloads:\n${AppFiles.saveBytesToDownloadsLegacy(ctx, fileName, bytes)}")
        } else {
            setPending(fileName to bytes)
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            setStatus("Allow storage access to save the file to Downloads…")
        }
    }
}

@Composable
private fun SlopeLineHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Slope Calc help") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Single point\n" +
                        "Uses rise/run/grade above with a start position and azimuth (° clockwise from north). " +
                        "Optional CSV loads one point into Start E/N/Z (editable). Station interval splits the polyline.\n\n" +
                        "Dual point\n" +
                        "Independent of the calculator. Pick a CSV with point A (start) and B (end), one row each " +
                        "(case-insensitive codes). Grade % is signed along start → end: positive rises toward end, " +
                        "negative falls toward end. Start elevation is held from CSV; end Z is computed from grade and " +
                        "horizontal distance. Use Swap start ↔ end if direction is reversed.\n\n" +
                        "Export DXF — saves a 3D stake line to Downloads.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
