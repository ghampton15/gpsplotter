package com.gpsplotting.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InchFractionTest {

    @Test
    fun `formats inches as tape measure fraction`() {
        assertEquals("5 1/2", InchFraction.format(5.5))
        assertEquals("1/2", InchFraction.format(0.5))
        assertEquals("12", InchFraction.format(12.0))
        assertEquals("0", InchFraction.format(0.0))
    }

    @Test
    fun `parses fraction and decimal inch input`() {
        assertEquals(5.5, InchFraction.parseLenient("5 1/2")!!, 1e-9)
        assertEquals(5.5, InchFraction.parseLenient("5-1/2")!!, 1e-9)
        assertEquals(0.5, InchFraction.parseLenient("1/2")!!, 1e-9)
        assertEquals(12.5, InchFraction.parseLenient("12.5")!!, 1e-9)
    }

    @Test
    fun `round trip through sixteenths`() {
        val inches = 3.375 // 3 3/8
        assertEquals("3 3/8", InchFraction.format(inches))
    }

    @Test
    fun `invalid partial fraction returns null`() {
        assertNull(InchFraction.parseLenient("5 1"))
    }
}
