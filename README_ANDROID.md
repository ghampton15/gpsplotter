# GPS Plotting (Android)

Personal-use native Android app (Kotlin + Jetpack Compose) that ports the repo Python utilities (`OffsetCalc`, pad offsets, driveway breaklines) and adds slope-line / sloping-plane tools with CSV, DXF, and LandXML export.

Coordinates are **passed through** as exported by Emlid Flow (e.g. EPSG:6445 ft US horizontal, NAVD88 ft US vertical). No datum transforms are performed in-app.

## Build

Requirements: **JDK 17**, Android SDK (install via Android Studio).

```powershell
$env:JAVA_HOME="<path-to-jdk-17>"
.\gradlew.bat :app:assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk` on your device (USB or side-load).

Outputs from tools are written under:

`Android/data/com.gpsplotting.app/files/Documents/GPSPlotting/`

(accessible from the device Files app / “Android/data/…” depending on OEM).

## Tests

```powershell
.\gradlew.bat :core:test
```

Pure JVM tests live in `:core` (no emulator required).

## Manual QA (Emlid Flow)

Use the checklist below on your phone after installing the APK:

1. Export a small CSV from Emlid Flow (Name, Easting, Northing, Elevation, Code).
2. **Offset / Pad offsets / Road breaklines**: pick the CSV, run, confirm files appear in `GPSPlotting` folder; import DXF or CSV back into Flow where applicable.
3. **LandXML surfaces**: export `surface_*.xml` from Sloping plane tools and import as a surface in Flow; verify TIN displays and elevations look sane at a known corner.

If LandXML import fails, confirm coordinate order matches your Flow version (this writer uses **Easting Northing Elevation** in each `<P>`).

## Gradle note

If Gradle fails with a JDK version message (e.g. unsupported JDK 25), point `JAVA_HOME` to JDK 17 or add `org.gradle.java.home` in `gradle.properties` locally (do not commit secrets).
