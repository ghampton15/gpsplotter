package com.gpsplotting.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.core.content.ContextCompat
import android.content.Context
import androidx.navigation.NavHostController
import androidx.activity.result.ActivityResultLauncher
import com.gpsplotting.app.ui.ToolScaffold
import com.gpsplotting.app.util.AppFiles
import com.gpsplotting.core.CsvIo
import com.gpsplotting.core.LandXmlWriter
import com.gpsplotting.core.Point3
import com.gpsplotting.core.SlopingPlane
import com.gpsplotting.core.SurveyPoint
import com.gpsplotting.core.inverseAzimuthDegrees
import com.gpsplotting.core.normalizeSurveyNumber
import com.gpsplotting.core.parseDoubleLenient
import com.gpsplotting.core.roundFeet3
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@Composable
fun SlopingPlaneScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }

    /** Same EPSG as Emlid Flow project CRS — required so surfaces aren’t placed as lon/lat. */
    var crsEpsg by remember { mutableStateOf("6445") }
    /** Default label matches epsg.io/6445 (NAD83(2011) / Georgia East ft US). */
    var crsDesc by remember { mutableStateOf("NAD83(2011) / Georgia East (ftUS)") }
    /** NAVD88 orthometric heights in ft US — EPSG 6360 per epsg.io/6360 (GEOID18 applied when surveying Z). */
    var verticalCrsEpsg by remember { mutableStateOf("6360") }
    var verticalCrsDesc by remember { mutableStateOf("NAVD88 height (ft US), GEOID18") }

    // Survey CSV → four coded points, A/B direction, target grade
    var csvUri by remember { mutableStateOf<Uri?>(null) }
    var csvCode by remember { mutableStateOf("") }
    var csvLoaded by remember { mutableStateOf<List<SurveyPoint>?>(null) }
    var csvPointA1 by remember { mutableStateOf("1") }
    var csvPointB1 by remember { mutableStateOf("2") }
    var csvGradePct by remember { mutableStateOf("") }
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

    // Fix 3 / solve 4
    var c00e by remember { mutableStateOf("") }
    var c00n by remember { mutableStateOf("") }
    var c00z by remember { mutableStateOf("") }
    var c01e by remember { mutableStateOf("") }
    var c01n by remember { mutableStateOf("") }
    var c01z by remember { mutableStateOf("") }
    var c02e by remember { mutableStateOf("") }
    var c02n by remember { mutableStateOf("") }
    var c02z by remember { mutableStateOf("") }
    var c03e by remember { mutableStateOf("") }
    var c03n by remember { mutableStateOf("") }
    var c03z by remember { mutableStateOf("") }
    var freeIdx by remember { mutableStateOf("") }

    // Target single slope
    var ae by remember { mutableStateOf("") }
    var an by remember { mutableStateOf("") }
    var az by remember { mutableStateOf("") }
    var mainAz by remember { mutableStateOf("") }
    var mainPct by remember { mutableStateOf("") }

    // Dual slope rectangle bounds
    var de by remember { mutableStateOf("") }
    var dn by remember { mutableStateOf("") }
    var dz by remember { mutableStateOf("") }
    var dMainAz by remember { mutableStateOf("") }
    var dMainPct by remember { mutableStateOf("") }
    var dCrossPct by remember { mutableStateOf("") }
    var minE by remember { mutableStateOf("") }
    var maxE by remember { mutableStateOf("") }
    var minN by remember { mutableStateOf("") }
    var maxN by remember { mutableStateOf("") }

    ToolScaffold("Sloping plane", nav) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Survey CSV") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Fix 3") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Target") })
                Tab(selected = tab == 3, onClick = { tab = 3 }, text = { Text("Dual") })
            }
            Spacer(Modifier.height(12.dp))

            Text(
                "LandXML needs horizontal CRS (EPSG 6445) and vertical CRS for elevations. Points are ENZ in ft US. " +
                    "GEOID18 is applied in Emlid when collecting Z; EPSG 6360 is NAVD88 height in US survey feet.",
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                crsEpsg,
                { crsEpsg = normalizeSurveyNumber(it) },
                label = { Text("Horizontal CRS EPSG") },
                supportingText = {
                    Text("Default 6445 = NAD83(2011) / Georgia East (ft US). epsg.io/6445")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                crsDesc,
                { crsDesc = it },
                label = { Text("Horizontal CRS description (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                verticalCrsEpsg,
                { verticalCrsEpsg = normalizeSurveyNumber(it) },
                label = { Text("Vertical CRS EPSG (elevations)") },
                supportingText = {
                    Text("Default 6360 = NAVD88 height (ft US). Clear to omit vertical metadata. epsg.io/6360")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                verticalCrsDesc,
                { verticalCrsDesc = it },
                label = { Text("Vertical CRS note (optional)") },
                supportingText = {
                    Text("GEOID18 corrects GNSS to NAVD88; stored Z values are NAVD88 ft US.")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            when (tab) {
                0 -> {
                    Text(
                        "Import a CSV with columns Code, Easting, Northing, Elevation (Name optional). " +
                            "Enter the code for your four pad corners, load, then pick which numbered corner is A (anchor Z) and B " +
                            "(direction A→B). Z at corners follows a single slope along that azimuth at your target % " +
                            "(same sign convention as Target: Z rises when moving A→B if % is positive).",
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { pickCsv.launch(arrayOf("text/*", "text/csv", "*/*")) }) { Text("Pick CSV") }
                    Text(csvUri?.let { AppFiles.safeDisplayName(it) } ?: "No file")
                    OutlinedTextField(
                        csvCode,
                        { csvCode = it },
                        label = { Text("Point code (exact match, case-insensitive)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            try {
                                val u = csvUri ?: error("Pick a CSV first.")
                                val all = CsvIo.readSurveyRows(
                                    ByteArrayInputStream(AppFiles.readBytes(ctx.contentResolver, u)),
                                )
                                val codeTrim = csvCode.trim()
                                require(codeTrim.isNotEmpty()) { "Enter the code used on the four corners in Flow." }
                                val filtered = all.filter {
                                    it.code.trim().equals(codeTrim, ignoreCase = true)
                                }
                                require(filtered.size == 4) {
                                    "Need exactly 4 points with that code (found ${filtered.size})."
                                }
                                csvLoaded = filtered
                                status =
                                    "Loaded 4 points: ${filtered.joinToString { "${it.pointName} (${it.code})" }}"
                            } catch (e: Exception) {
                                csvLoaded = null
                                status = e.message ?: e.toString()
                            }
                        },
                    ) { Text("Load 4 points") }
                    csvLoaded?.let { pts ->
                        Spacer(Modifier.height(8.dp))
                        Text("Order around pad (CSV row order):")
                        pts.forEachIndexed { i, p ->
                            Text("${i + 1}. ${p.pointName}  E ${roundFeet3(p.easting)}  N ${roundFeet3(p.northing)}  Z ${roundFeet3(p.elevation)}")
                        }
                        OutlinedTextField(
                            csvPointA1,
                            { csvPointA1 = normalizeSurveyNumber(it) },
                            label = { Text("Point A (1–4)") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            csvPointB1,
                            { csvPointB1 = normalizeSurveyNumber(it) },
                            label = { Text("Point B (1–4), defines direction from A") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            csvGradePct,
                            { csvGradePct = normalizeSurveyNumber(it) },
                            label = { Text("Target slope % along A→B") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                try {
                                    val a1 = parseReq("Point A", csvPointA1).toInt()
                                    val b1 = parseReq("Point B", csvPointB1).toInt()
                                    require(a1 in 1..4 && b1 in 1..4) { "A and B must be 1–4." }
                                    val ai = a1 - 1
                                    val bi = b1 - 1
                                    require(ai != bi) { "Point A and B must be different." }
                                    val list = pts
                                    val a = list[ai]
                                    val b = list[bi]
                                    val anchor = Point3(a.easting, a.northing, a.elevation)
                                    val azimuth = inverseAzimuthDegrees(
                                        Point3(a.easting, a.northing, a.elevation),
                                        Point3(b.easting, b.northing, b.elevation),
                                    )
                                    val plane = SlopingPlane.planeFromAnchorSingleSlope(
                                        anchor,
                                        azimuth,
                                        parseReq("Slope %", csvGradePct),
                                    )
                                    val ens = list.map { it.easting to it.northing }
                                    val corners = SlopingPlane.cornersWithPlaneZ(ens, plane)
                                    val epsg = parseCrsEpsg(crsEpsg)
                                    val desc = crsDesc.trim().takeIf { it.isNotEmpty() }
                                    val (vEpsg, vDesc) = verticalCrsArgs(verticalCrsEpsg, verticalCrsDesc)
                                    val bytes = landXmlQuadBytes("SurveyCsvPlane", corners, epsg, desc, vEpsg, vDesc)
                                    val detail =
                                        "Azimuth A→B: ${"%.4f".format(azimuth)}° CW from N\n" +
                                            "Est. max grade ${"%.3f".format(plane.maxGradePercent())}%"
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
                }
                1 -> {
                    Text("Four corners (order around pad). Enter measured Z for all; choose which corner index is solved (0–3).")
                    cornerFields(
                        c00e, c00n, c00z, { c00e = it }, { c00n = it }, { c00z = it },
                        c01e, c01n, c01z, { c01e = it }, { c01n = it }, { c01z = it },
                        c02e, c02n, c02z, { c02e = it }, { c02n = it }, { c02z = it },
                        c03e, c03n, c03z, { c03e = it }, { c03n = it }, { c03z = it },
                    )
                    OutlinedTextField(freeIdx, { freeIdx = normalizeSurveyNumber(it) }, label = { Text("Solved corner index (0–3)") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            try {
                                val corners = listOf(
                                    Point3(parseReq("Corner 0 E", c00e), parseReq("Corner 0 N", c00n), parseReq("Corner 0 Z", c00z)),
                                    Point3(parseReq("Corner 1 E", c01e), parseReq("Corner 1 N", c01n), parseReq("Corner 1 Z", c01z)),
                                    Point3(parseReq("Corner 2 E", c02e), parseReq("Corner 2 N", c02n), parseReq("Corner 2 Z", c02z)),
                                    Point3(parseReq("Corner 3 E", c03e), parseReq("Corner 3 N", c03n), parseReq("Corner 3 Z", c03z)),
                                )
                                val idx = normalizeSurveyNumber(freeIdx).toIntOrNull()
                                    ?: throw IllegalArgumentException("Enter solved corner index (0–3).")
                                require(idx in 0..3) { "Index must be 0–3" }
                                val r = SlopingPlane.fixThreeSolveFour(corners, idx)
                                val epsg = parseCrsEpsg(crsEpsg)
                                val desc = crsDesc.trim().takeIf { it.isNotEmpty() }
                                val (vEpsg, vDesc) = verticalCrsArgs(verticalCrsEpsg, verticalCrsDesc)
                                val bytes = landXmlQuadBytes("Fix3Plane", r.cornersZComputed, epsg, desc, vEpsg, vDesc)
                                val detail =
                                    "Plane Z at free corner: ${"%.3f".format(r.planeZ)} (meas ${"%.3f".format(r.measuredZ)}), Δ ${"%.3f".format(r.deltaZ)}\n" +
                                        "Max slope ~ ${"%.3f".format(r.plane.maxGradePercent())}%"
                                saveLandXmlToDownloads(
                                    ctx,
                                    "surface_fix3_${System.currentTimeMillis()}.xml",
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
                2 -> {
                    Text("Anchor + single slope direction; corners use plan coords only (Z computed).")
                    OutlinedTextField(ae, { ae = normalizeSurveyNumber(it) }, label = { Text("Anchor E") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(an, { an = normalizeSurveyNumber(it) }, label = { Text("Anchor N") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(az, { az = normalizeSurveyNumber(it) }, label = { Text("Anchor Z") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mainAz, { mainAz = normalizeSurveyNumber(it) }, label = { Text("Azimuth ° CW from N") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(mainPct, { mainPct = normalizeSurveyNumber(it) }, label = { Text("Slope % along azimuth") }, modifier = Modifier.fillMaxWidth())
                    cornerPlanFields(
                        c00e, c00n, { c00e = it }, { c00n = it },
                        c01e, c01n, { c01e = it }, { c01n = it },
                        c02e, c02n, { c02e = it }, { c02n = it },
                        c03e, c03n, { c03e = it }, { c03n = it },
                    )
                    Button(
                        onClick = {
                            try {
                                val anchor = Point3(
                                    parseReq("Anchor E", ae),
                                    parseReq("Anchor N", an),
                                    parseReq("Anchor Z", az),
                                )
                                val plane = SlopingPlane.planeFromAnchorSingleSlope(
                                    anchor,
                                    parseReq("Azimuth", mainAz),
                                    parseReq("Slope %", mainPct),
                                )
                                val ens = listOf(
                                    parseReq("Corner 0 E", c00e) to parseReq("Corner 0 N", c00n),
                                    parseReq("Corner 1 E", c01e) to parseReq("Corner 1 N", c01n),
                                    parseReq("Corner 2 E", c02e) to parseReq("Corner 2 N", c02n),
                                    parseReq("Corner 3 E", c03e) to parseReq("Corner 3 N", c03n),
                                )
                                val pts = SlopingPlane.cornersWithPlaneZ(ens, plane)
                                val epsg = parseCrsEpsg(crsEpsg)
                                val desc = crsDesc.trim().takeIf { it.isNotEmpty() }
                                val (vEpsg, vDesc) = verticalCrsArgs(verticalCrsEpsg, verticalCrsDesc)
                                val bytes = landXmlQuadBytes("TargetPlane", pts, epsg, desc, vEpsg, vDesc)
                                val detail = "Est. max grade ${"%.3f".format(plane.maxGradePercent())}%"
                                saveLandXmlToDownloads(
                                    ctx,
                                    "surface_target_${System.currentTimeMillis()}.xml",
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
                3 -> {
                    Text("Dual slope from anchor; rectangle defined by min/max E and N.")
                    OutlinedTextField(de, { de = normalizeSurveyNumber(it) }, label = { Text("Anchor E") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dn, { dn = normalizeSurveyNumber(it) }, label = { Text("Anchor N") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dz, { dz = normalizeSurveyNumber(it) }, label = { Text("Anchor Z") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dMainAz, { dMainAz = normalizeSurveyNumber(it) }, label = { Text("Main azimuth ° CW from N") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dMainPct, { dMainPct = normalizeSurveyNumber(it) }, label = { Text("Main slope %") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(dCrossPct, { dCrossPct = normalizeSurveyNumber(it) }, label = { Text("Cross slope %") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(minE, { minE = normalizeSurveyNumber(it) }, label = { Text("Min E") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(maxE, { maxE = normalizeSurveyNumber(it) }, label = { Text("Max E") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(minN, { minN = normalizeSurveyNumber(it) }, label = { Text("Min N") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(maxN, { maxN = normalizeSurveyNumber(it) }, label = { Text("Max N") }, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            try {
                                val anchor = Point3(
                                    parseReq("Anchor E", de),
                                    parseReq("Anchor N", dn),
                                    parseReq("Anchor Z", dz),
                                )
                                val plane = SlopingPlane.planeFromAnchorDualSlope(
                                    anchor,
                                    parseReq("Main azimuth", dMainAz),
                                    parseReq("Main slope %", dMainPct),
                                    parseReq("Cross slope %", dCrossPct),
                                )
                                val miE = parseReq("Min E", minE)
                                val maE = parseReq("Max E", maxE)
                                val miN = parseReq("Min N", minN)
                                val maN = parseReq("Max N", maxN)
                                val quad = listOf(
                                    miE to miN,
                                    maE to miN,
                                    maE to maN,
                                    miE to maN,
                                ).map { (e, n) ->
                                    Point3(roundFeet3(e), roundFeet3(n), roundFeet3(plane.zAt(e, n)))
                                }
                                val epsg = parseCrsEpsg(crsEpsg)
                                val desc = crsDesc.trim().takeIf { it.isNotEmpty() }
                                val (vEpsg, vDesc) = verticalCrsArgs(verticalCrsEpsg, verticalCrsDesc)
                                val bytes = landXmlQuadBytes(
                                    "DualSlopePlane",
                                    quad,
                                    epsg,
                                    desc,
                                    vEpsg,
                                    vDesc,
                                )
                                saveLandXmlToDownloads(
                                    ctx,
                                    "surface_dual_${System.currentTimeMillis()}.xml",
                                    bytes,
                                    "",
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
            }
            Spacer(Modifier.height(8.dp))
            Text(status)
        }
    }
}

private data class PendingLandXml(val fileName: String, val bytes: ByteArray, val detail: String)

private fun parseCrsEpsg(s: String): Int =
    s.trim().toIntOrNull()
        ?: throw IllegalArgumentException(
            "Enter CRS EPSG code (same as your Emlid Flow project coordinate system).",
        )

/** Returns vertical EPSG (null if field cleared) and optional description for LandXML. */
private fun verticalCrsArgs(verticalEpsgStr: String, verticalDescStr: String): Pair<Int?, String?> {
    val trimmed = verticalEpsgStr.trim()
    if (trimmed.isEmpty()) return null to verticalDescStr.trim().takeIf { it.isNotEmpty() }
    val code = trimmed.toIntOrNull()
        ?: throw IllegalArgumentException("Vertical CRS EPSG must be a whole number (e.g. 6360).")
    return code to verticalDescStr.trim().takeIf { it.isNotEmpty() }
}

private fun landXmlQuadBytes(
    surfaceName: String,
    corners: List<Point3>,
    epsgCode: Int,
    crsDescription: String?,
    verticalEpsgCode: Int?,
    verticalCrsDescription: String?,
): ByteArray =
    ByteArrayOutputStream().also {
        LandXmlWriter.writeQuadTin(
            it,
            surfaceName,
            corners,
            "GPSPlotting",
            epsgCode,
            crsDescription,
            verticalEpsgCode,
            verticalCrsDescription,
            LandXmlWriter.PlanOrder.EastingNorthingElevation,
        )
    }.toByteArray()

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
            prefix + "Saved to Downloads:\n${AppFiles.saveBytesToDownloadsMediaStore(ctx, fileName, bytes)}",
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

@Composable
private fun cornerFields(
    e0: String, n0: String, z0: String, se0: (String) -> Unit, sn0: (String) -> Unit, sz0: (String) -> Unit,
    e1: String, n1: String, z1: String, se1: (String) -> Unit, sn1: (String) -> Unit, sz1: (String) -> Unit,
    e2: String, n2: String, z2: String, se2: (String) -> Unit, sn2: (String) -> Unit, sz2: (String) -> Unit,
    e3: String, n3: String, z3: String, se3: (String) -> Unit, sn3: (String) -> Unit, sz3: (String) -> Unit,
) {
    Text("Corner 0"); rowEnz(e0, n0, z0, se0, sn0, sz0)
    Text("Corner 1"); rowEnz(e1, n1, z1, se1, sn1, sz1)
    Text("Corner 2"); rowEnz(e2, n2, z2, se2, sn2, sz2)
    Text("Corner 3"); rowEnz(e3, n3, z3, se3, sn3, sz3)
}

@Composable
private fun rowEnz(e: String, n: String, z: String, se: (String) -> Unit, sn: (String) -> Unit, sz: (String) -> Unit) {
    OutlinedTextField(e, { se(normalizeSurveyNumber(it)) }, label = { Text("E") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(n, { sn(normalizeSurveyNumber(it)) }, label = { Text("N") }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(z, { sz(normalizeSurveyNumber(it)) }, label = { Text("Z") }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun cornerPlanFields(
    e0: String, n0: String, se0: (String) -> Unit, sn0: (String) -> Unit,
    e1: String, n1: String, se1: (String) -> Unit, sn1: (String) -> Unit,
    e2: String, n2: String, se2: (String) -> Unit, sn2: (String) -> Unit,
    e3: String, n3: String, se3: (String) -> Unit, sn3: (String) -> Unit,
) {
    Text("Corner 0 plan"); OutlinedTextField(e0, { se0(normalizeSurveyNumber(it)) }, label = { Text("E") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(n0, { sn0(normalizeSurveyNumber(it)) }, label = { Text("N") }, modifier = Modifier.fillMaxWidth())
    Text("Corner 1 plan"); OutlinedTextField(e1, { se1(normalizeSurveyNumber(it)) }, label = { Text("E") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(n1, { sn1(normalizeSurveyNumber(it)) }, label = { Text("N") }, modifier = Modifier.fillMaxWidth())
    Text("Corner 2 plan"); OutlinedTextField(e2, { se2(normalizeSurveyNumber(it)) }, label = { Text("E") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(n2, { sn2(normalizeSurveyNumber(it)) }, label = { Text("N") }, modifier = Modifier.fillMaxWidth())
    Text("Corner 3 plan"); OutlinedTextField(e3, { se3(normalizeSurveyNumber(it)) }, label = { Text("E") }, modifier = Modifier.fillMaxWidth()); OutlinedTextField(n3, { sn3(normalizeSurveyNumber(it)) }, label = { Text("N") }, modifier = Modifier.fillMaxWidth())
}
