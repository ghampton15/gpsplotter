package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class LandXmlCrsTest {

    @Test
    fun `6445 has wkt and easting first plan order`() {
        assertNotNull(LandXmlCrs.ogcWktForEpsg(6445))
        assertEquals(
            LandXmlWriter.PlanOrder.EastingNorthingElevation,
            LandXmlCrs.planOrderForEpsg(6445),
        )
    }
}
