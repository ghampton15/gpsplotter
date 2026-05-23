package com.gpsplotting.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.navigation.NavHostController
import androidx.activity.result.ActivityResultLauncher
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.LandXmlCrs
import com.gpsplotting.core.LandXmlWriter
import com.gpsplotting.core.Point3
import com.gpsplotting.core.SlopingPlane
import com.gpsplotting.core.SurveyPoint
import com.gpsplotting.core.normalizeSurveyNumber
import com.gpsplotting.core.parseDoubleLenient
import com.gpsplotting.core.roundFeet3
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Fixed CRS for sloping-plane LandXML (Emlid Flow EPSG 6445 / NAVD88). */
private const val SLOPING_PLANE_CRS_EPSG = 6445
private const val SLOPING_PLANE_CRS_DESC = "NAD83(2011) / Georgia East (ftUS)"
private const val SLOPING_PLANE_VERTICAL_CRS_EPSG = 6360
private const val SLOPING_PLANE_VERTICAL_CRS_DESC = "NAVD88 height (ft US), GEOID18"

/**
 * Empirical plan shift (ft) so TIN vertices align with corner stakes in Emlid Flow.
 * Matches post-export LandXML fix: first &lt;P&gt; axis +1.9, second +0.85 (N-first export).
 */
private const val SLOPING_PLANE_LANDXML_SHIFT_NORTHING_FT = 1.9
private const val SLOPING_PLANE_LANDXML_SHIFT_EASTING_FT = 0.85

/** Other two pad corners (case-insensitive). Start/end use [SLOPE_START_CODE] / [SLOPE_END_CODE]. */
private const val PAD_CORNER_CODE = "slope"
private const val SLOPE_START_CODE = "slope A"
private const val SLOPE_END_CODE = "slope B"
private val MATCH_CORNER_CODE = SlopingPlane.MATCH_CORNER_CODE

private enum class SurfaceModeTab {
    Auto,
    SlopingPlane,
    MatchCorners,
}

private data class SlopingPlaneLoad(
    val corners: List<SurveyPoint>,
    val start: SurveyPoint?,
    val end: SurveyPoint?,
    val matchCount: Int,
)

@Composable
fun SlopingPlaneScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var status by remember { mutableStateOf("") }
    var showHelp by remember { mutableStateOf(false) }

    /** Off = raw ENZ in file; Flow project CRS must be 6445 (recommended for Emlid). */
    var embedCrsInLandXml by remember { mutableStateOf(false) }
    /** Only if embed CRS on: some tools expect northing before easting in each P element. */
    var landXmlNorthingFirst by remember { mutableStateOf(true) }
    /**
     * Corrects Emlid Flow LandXML import when project uses US survey ft (6445) but file uses int foot.
     * Off if Flow and export both use international feet.
     */
    var applySurveyFootAlignShift by remember { mutableStateOf(false) }

    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var csvLoaded by remember { mutableStateOf<SlopingPlaneLoad?>(null) }
    var csvGradePct by remember { mutableStateOf("") }
    var surfaceModeTab by remember { mutableIntStateOf(SurfaceModeTab.Auto.ordinal) }
    val pickCsv = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        csvUri = u
        csvLoaded = null
        status = ""
    }

    var pendingLandXml by remember { mutableStateOf<PendingLandXml?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingLandXml
        pendingLandXml = null
        if (pending != null) {
            status = try {
                val prefix = if (pending.detail.isNotBlank()) pending.detail + "\n" else ""
                if (granted) {
                    prefix +
                        "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsLegacy(ctx, pending.fileName, pending.bytes)}"
                } else {
                    prefix +
                        "Storage permission denied — cannot save to Downloads on Android 9 and older."
                }
            } catch (e: Exception) {
                e.message ?: e.toString()
            }
        }
    }

    if (showHelp) {
        SlopingPlaneHelpDialog { showHelp = false }
    }

    ToolScaffold(
        title = "Sloping plane",
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            RowSwitch(
                "Embed CRS in LandXML file",
                embedCrsInLandXml,
            ) { embedCrsInLandXml = it }
            if (embedCrsInLandXml) {
                RowSwitch(
                    "Northing first in file",
                    landXmlNorthingFirst,
                ) { landXmlNorthingFirst = it }
            }
            RowSwitch(
                "Survey foot alignment shift",
                applySurveyFootAlignShift,
            ) { applySurveyFootAlignShift = it }
            Spacer(Modifier.height(12.dp))
            Button(onClick = { pickCsv.launch(arrayOf("text/*", "text/csv", "*/*")) }) { Text("Pick CSV") }
            Text(csvUri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
            Text(
                "Pad: 4 corners coded $PAD_CORNER_CODE, $SLOPE_START_CODE, $SLOPE_END_CODE, or $MATCH_CORNER_CODE.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Button(
                onClick = {
                    try {
                        val u = csvUri ?: error("Pick a CSV first.")
                        val all = CsvIo.readSurveyRows(
                            ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)),
                        )
                        val load = loadSlopingPlaneFromCsv(all)
                        csvLoaded = load
                        val autoMode = resolveEffectiveSurfaceMode(SurfaceModeTab.Auto, load.matchCount)
                        status = buildLoadStatus(load, autoMode)
                    } catch (e: Exception) {
                        csvLoaded = null
                        status = e.message ?: e.toString()
                    }
                },
            ) { Text("Load pad from CSV") }
            csvLoaded?.let { load ->
                Spacer(Modifier.height(12.dp))
                PrimaryTabRow(selectedTabIndex = surfaceModeTab) {
                    Tab(
                        selected = surfaceModeTab == SurfaceModeTab.Auto.ordinal,
                        onClick = { surfaceModeTab = SurfaceModeTab.Auto.ordinal },
                        text = { Text("Auto") },
                    )
                    Tab(
                        selected = surfaceModeTab == SurfaceModeTab.SlopingPlane.ordinal,
                        onClick = { surfaceModeTab = SurfaceModeTab.SlopingPlane.ordinal },
                        text = { Text("Sloping plane") },
                    )
                    Tab(
                        selected = surfaceModeTab == SurfaceModeTab.MatchCorners.ordinal,
                        onClick = { surfaceModeTab = SurfaceModeTab.MatchCorners.ordinal },
                        text = { Text("Match corners") },
                    )
                }
                val tab = SurfaceModeTab.entries[surfaceModeTab]
                val effectiveMode = resolveEffectiveSurfaceMode(tab, load.matchCount)
                Text(
                    "Export mode: ${modeLabel(effectiveMode)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text("Order around pad (CSV row order among corner codes):")
                load.corners.forEachIndexed { i, p ->
                    val role = when {
                        SlopingPlane.isMatchCornerCode(p.code) -> " — Match"
                        p.code.trim().equals(SLOPE_START_CODE, ignoreCase = true) -> " — start"
                        p.code.trim().equals(SLOPE_END_CODE, ignoreCase = true) -> " — end"
                        else -> ""
                    }
                    Text(
                        "${i + 1}. ${p.pointName} (${p.code})$role  " +
                            "E ${roundFeet3(p.easting)}  N ${roundFeet3(p.northing)}  Z ${roundFeet3(p.elevation)}",
                    )
                }
                if (effectiveMode == SlopingPlane.PadSurfaceMode.SlopingPlane) {
                    OutlinedTextField(
                        csvGradePct,
                        { csvGradePct = normalizeSurveyNumber(it) },
                        label = { Text("Target slope % along A→B") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Button(
                    onClick = {
                        try {
                            validateModeForExport(tab, load)
                            val grade = if (effectiveMode == SlopingPlane.PadSurfaceMode.MatchCorners) {
                                0.0
                            } else {
                                parseReq("Slope %", csvGradePct)
                            }
                            val built = SlopingPlane.buildPadSurfaceCorners(
                                load.corners,
                                load.start,
                                load.end,
                                grade,
                                effectiveMode,
                            )
                            val epsg = SLOPING_PLANE_CRS_EPSG
                            val desc = SLOPING_PLANE_CRS_DESC
                            val vEpsg = SLOPING_PLANE_VERTICAL_CRS_EPSG
                            val vDesc = SLOPING_PLANE_VERTICAL_CRS_DESC
                            val bytes = landXmlQuadBytes(
                                "SurveyCsvPlane",
                                built.corners,
                                epsg,
                                desc,
                                vEpsg,
                                vDesc,
                                embedCrsInLandXml,
                                landXmlNorthingFirst,
                                applySurveyFootAlignShift,
                            )
                            val detail = built.detailLines.joinToString("\n")
                            saveLandXmlToDownloads(
                                ctx,
                                "surface_survey_csv_${System.currentTimeMillis()}.xml",
                                bytes,
                                detail,
                                { status = it },
                                { pendingLandXml = it },
                                permissionLauncher,
                            )
                        } catch (e: Exception) {
                            status = e.message ?: e.toString()
                        }
                    },
                ) { Text("Export LandXML") }
            }

            Spacer(Modifier.height(8.dp))
            Text(status)
        }
    }
}

private data class PendingLandXml(val fileName: String, val bytes: ByteArray, val detail: String)

private fun landXmlQuadBytes(
    surfaceName: String,
    corners: List<Point3>,
    epsgCode: Int,
    crsDescription: String?,
    verticalEpsgCode: Int?,
    verticalCrsDescription: String?,
    embedCrs: Boolean,
    northingFirst: Boolean,
    applySurveyFootAlignShift: Boolean,
): ByteArray =
    ByteArrayOutputStream().also {
        val planOrder = when {
            !embedCrs -> LandXmlWriter.PlanOrder.NorthingEastingElevation
            northingFirst -> LandXmlWriter.PlanOrder.NorthingEastingElevation
            else -> LandXmlCrs.planOrderForEpsg(epsgCode)
        }
        val shiftE = if (applySurveyFootAlignShift) SLOPING_PLANE_LANDXML_SHIFT_EASTING_FT else 0.0
        val shiftN = if (applySurveyFootAlignShift) SLOPING_PLANE_LANDXML_SHIFT_NORTHING_FT else 0.0
        LandXmlWriter.writeQuadTin(
            it,
            surfaceName,
            corners,
            "GPSPlotting",
            if (embedCrs) epsgCode else null,
            crsDescription,
            if (embedCrs) verticalEpsgCode else null,
            verticalCrsDescription,
            planOrder,
            planShiftEastingFt = shiftE,
            planShiftNorthingFt = shiftN,
        )
    }.toByteArray()

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

private fun saveLandXmlToDownloads(
    ctx: Context,
    fileName: String,
    bytes: ByteArray,
    detail: String,
    setStatus: (String) -> Unit,
    setPending: (PendingLandXml?) -> Unit,
    permissionLauncher: ActivityResultLauncher<String>,
) {
    val prefix = if (detail.isNotBlank()) "$detail\n" else ""
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        setStatus(
            prefix + "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsMediaStore(ctx, fileName, bytes, "application/xml")}",
        )
    } else {
        val ok = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
        if (ok) {
            setStatus(
                prefix + "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsLegacy(ctx, fileName, bytes)}",
            )
        } else {
            setPending(PendingLandXml(fileName, bytes, detail))
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            setStatus(prefix + "Allow storage access to save the file to Downloads…")
        }
    }
}

private fun parseReq(label: String, s: String): Double =
    parseDoubleLenient(s) ?: throw IllegalArgumentException("$label must be a valid number.")

private fun isPadCornerCode(code: String): Boolean {
    val c = code.trim()
    return c.equals(PAD_CORNER_CODE, ignoreCase = true) ||
        c.equals(SLOPE_START_CODE, ignoreCase = true) ||
        c.equals(SLOPE_END_CODE, ignoreCase = true) ||
        SlopingPlane.isMatchCornerCode(c)
}

private fun resolveEffectiveSurfaceMode(
    tab: SurfaceModeTab,
    matchCount: Int,
): SlopingPlane.PadSurfaceMode =
    when (tab) {
        SurfaceModeTab.Auto ->
            if (matchCount == 4) SlopingPlane.PadSurfaceMode.MatchCorners
            else SlopingPlane.PadSurfaceMode.SlopingPlane
        SurfaceModeTab.SlopingPlane -> SlopingPlane.PadSurfaceMode.SlopingPlane
        SurfaceModeTab.MatchCorners -> SlopingPlane.PadSurfaceMode.MatchCorners
    }

private fun modeLabel(mode: SlopingPlane.PadSurfaceMode): String = when (mode) {
    SlopingPlane.PadSurfaceMode.MatchCorners -> "Match corners"
    SlopingPlane.PadSurfaceMode.SlopingPlane -> "Sloping plane"
}

private fun buildLoadStatus(load: SlopingPlaneLoad, autoMode: SlopingPlane.PadSurfaceMode): String {
    val modePart = "Auto → ${modeLabel(autoMode)}"
    return if (load.matchCount == 4) {
        "Loaded 4× $MATCH_CORNER_CODE corners. $modePart"
    } else {
        val startName = load.start?.pointName ?: "?"
        val endName = load.end?.pointName ?: "?"
        "Loaded pad: start $startName, end $endName" +
            (if (load.matchCount == 1) ", 1× $MATCH_CORNER_CODE tie-in" else "") +
            ". $modePart"
    }
}

private fun validateModeForExport(tab: SurfaceModeTab, load: SlopingPlaneLoad) {
    when (tab) {
        SurfaceModeTab.MatchCorners -> {
            require(load.matchCount == 4) {
                "Match corners mode requires all four corners coded $MATCH_CORNER_CODE."
            }
        }
        SurfaceModeTab.SlopingPlane -> {
            require(load.matchCount < 4) {
                "Sloping plane mode cannot be used with four Match corners. Use Auto or Match corners."
            }
        }
        SurfaceModeTab.Auto -> Unit
    }
}

private fun findSurveyPointByCode(rows: List<SurveyPoint>, code: String, label: String): SurveyPoint {
    val codeTrim = code.trim()
    val matches = rows.filter { it.code.trim().equals(codeTrim, ignoreCase = true) }
    require(matches.size == 1) {
        "$label code “$codeTrim”: need exactly 1 point (found ${matches.size})."
    }
    return matches.single()
}

private fun loadSlopingPlaneFromCsv(rows: List<SurveyPoint>): SlopingPlaneLoad {
    val corners = rows.filter { isPadCornerCode(it.code) }
    require(corners.size == 4) {
        "Need exactly 4 pad corners coded $PAD_CORNER_CODE, $SLOPE_START_CODE, $SLOPE_END_CODE, or " +
            "$MATCH_CORNER_CODE (found ${corners.size})."
    }
    val matchCount = corners.count { SlopingPlane.isMatchCornerCode(it.code) }
    if (matchCount == 4) {
        return SlopingPlaneLoad(corners, start = null, end = null, matchCount = 4)
    }
    val start = findSurveyPointByCode(rows, SLOPE_START_CODE, "Start")
    val end = findSurveyPointByCode(rows, SLOPE_END_CODE, "End")
    require(
        start.easting != end.easting || start.northing != end.northing,
    ) { "$SLOPE_START_CODE and $SLOPE_END_CODE must be different plan positions." }
    require(corners.any { it.code.trim().equals(SLOPE_START_CODE, ignoreCase = true) }) {
        "One corner must be coded $SLOPE_START_CODE."
    }
    require(corners.any { it.code.trim().equals(SLOPE_END_CODE, ignoreCase = true) }) {
        "One corner must be coded $SLOPE_END_CODE."
    }
    return SlopingPlaneLoad(corners, start, end, matchCount)
}

@Composable
private fun SlopingPlaneHelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sloping plane help") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "LandXML & Emlid Flow\n" +
                        "Set your Emlid Flow project to EPSG 6445 before import.\n" +
                        "With Embed CRS off, each point is northing, easting, elevation (ft), " +
                        "like Kubla exports. Turn on Embed CRS only if your importer needs WKT in the file.\n\n" +
                        "Survey foot alignment shift — adds +1.9 ft northing and +0.85 ft easting to surface " +
                        "vertices in the LandXML file. Use when your Flow project is on EPSG 6445 (US survey ft) " +
                        "and imported surfaces sit slightly off stakes; leave off if Flow and export both use " +
                        "international feet.\n\n" +
                        "Embed CRS — writes coordinate-system metadata into the XML (EPSG 6445 / NAVD88).\n" +
                        "Northing first — only when Embed CRS is on; overrides default easting-first axis order.\n\n" +
                        "Surface modes (Auto / Sloping plane / Match corners)\n" +
                        "Auto: four Match corners → surveyed Z at each corner (quad TIN). Otherwise sloping plane.\n\n" +
                        "Sloping plane — slope A anchors Z (or one Match tie-in at surveyed Z), slope B sets direction, " +
                        "grade % along A→B; other corners get plane Z.\n\n" +
                        "Match corners — all four corners coded Match; surveyed elevations unchanged (sheet over four pegs).\n\n" +
                        "Survey CSV — Code, Easting, Northing, Elevation (Name optional). Exactly four pad rows using " +
                        "slope, slope A, slope B, and/or Match. Sloping layouts need slope A and slope B on two corners; " +
                        "four× Match needs no slope A/B. Two or three Match corners are not supported.\n\n" +
                        "Order around pad — row order of the four corners defines the TIN quad.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}
