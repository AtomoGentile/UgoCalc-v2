package com.ugocalc.app.domain.inference.m3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalDataTest {

    private val data = PhysicalData()

    @Test
    fun `known densities resolve to their table value and carry a non-default source`() {
        val riso = data.density("riso")
        assertEquals(0.82, riso.valore, 1e-9)
        assertFalse(riso.isDefault)
        assertEquals("riso cotto (CREA)", riso.fonte)

        assertEquals(1.04, data.density("bistecca").valore, 1e-9)
        assertEquals(0.45, data.density("pane").valore, 1e-9)
    }

    @Test
    fun `unknown food falls back to the default density flagged as default`() {
        val ignoto = data.density("sostanza aliena")
        assertEquals(0.90, ignoto.valore, 1e-9)
        assertTrue(ignoto.isDefault)
    }

    @Test
    fun `unit weights resolve per piece`() {
        assertEquals(150.0, data.unitWeight("mela").valore, 1e-9)
        assertEquals(55.0, data.unitWeight("uovo").valore, 1e-9)
        assertTrue(data.unitWeight("frutto misterioso").isDefault)
    }

    @Test
    fun `container capacities resolve in ml`() {
        assertEquals(200.0, data.capacity("bicchiere").valore, 1e-9)
        assertEquals(350.0, data.capacity("ciotola").valore, 1e-9)
        assertTrue(data.capacity("recipiente ignoto").isDefault)
    }

    @Test
    fun `lookup ignores case and accents`() {
        assertEquals(1.00, data.density("Caffè").valore, 1e-9)
        assertEquals(0.92, data.density("PURÈ").valore, 1e-9)
        assertEquals(150.0, data.unitWeight("Mela").valore, 1e-9)
    }
}
