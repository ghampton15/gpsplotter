package com.gpsplotting.core

/**
 * OGC WKT for LandXML [CoordinateSystem ogcWktCode] so importers (e.g. Emlid Flow) apply the
 * correct axis order and US survey foot units — not just a bare [epsgCode].
 */
object LandXmlCrs {

    /** EPSG 6445 — NAD83(2011) / Georgia East (ft US); easting, northing axis order. */
    private const val WKT_6445 =
        "PROJCRS[\"NAD83(2011) / Georgia East (ftUS)\"," +
            "GEOGCS[\"NAD83(2011)\"," +
            "DATUM[\"NAD83_National_Spatial_Reference_System_2011\"," +
            "SPHEROID[\"GRS 1980\",6378137,298.257222101]]," +
            "PRIMEM[\"Greenwich\",0]," +
            "UNIT[\"degree\",0.0174532925199433]]," +
            "CONVERSION[\"SPCS83 Georgia East zone (US survey foot)\"," +
            "METHOD[\"Transverse Mercator\"]," +
            "PARAMETER[\"Latitude of natural origin\",30]," +
            "PARAMETER[\"Longitude of natural origin\",-82.1666666666667]," +
            "PARAMETER[\"Scale factor at natural origin\",0.9999]," +
            "PARAMETER[\"False easting\",656166.667]," +
            "PARAMETER[\"False northing\",0]]," +
            "CS[Cartesian,2]," +
            "AXIS[\"easting\",east]," +
            "AXIS[\"northing\",north]," +
            "ID[\"EPSG\",6445]]"

    fun ogcWktForEpsg(epsgCode: Int): String? = when (epsgCode) {
        6445 -> WKT_6445
        else -> null
    }

    /**
     * LandXML &lt;P&gt; axis order for the horizontal CRS. EPSG 6445 is easting-first;
     * some national grids (e.g. SWEREF99 TM) are northing-first in Emlid exports.
     */
    fun planOrderForEpsg(epsgCode: Int): LandXmlWriter.PlanOrder = when (epsgCode) {
        6445 -> LandXmlWriter.PlanOrder.EastingNorthingElevation
        else -> LandXmlWriter.PlanOrder.EastingNorthingElevation
    }
}
