package com.ugocalc.app.domain.inference.m3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalDataTest {

    @Test
    fun `known densities resolve to their table value and carry a non-default source`() {
        val riso = PhysicalData.density("riso")
        assertEquals(0.85, riso.valore, 1e-9)
        assertFalse(riso.isDefault)
        assertEquals("riso cotto", riso.fonte)

        assertEquals(1.05, PhysicalData.density("bistecca").valore, 1e-9)
        assertEquals(0.28, PhysicalData.density("pane").valore, 1e-9)
    }

    @Test
    fun `unknown food falls back to the default density flagged as default`() {
        val ignoto = PhysicalData.density("sostanza aliena")
        assertEquals(0.90, ignoto.valore, 1e-9)
        assertTrue(ignoto.isDefault)
    }

    @Test
    fun `unit weights resolve per piece`() {
        assertEquals(150.0, PhysicalData.unitWeight("mela").valore, 1e-9)
        assertEquals(55.0, PhysicalData.unitWeight("uovo").valore, 1e-9)
        assertTrue(PhysicalData.unitWeight("frutto misterioso").isDefault)
    }

    @Test
    fun `container capacities resolve in ml`() {
        assertEquals(200.0, PhysicalData.capacity("bicchiere").valore, 1e-9)
        assertEquals(350.0, PhysicalData.capacity("ciotola").valore, 1e-9)
        assertTrue(PhysicalData.capacity("recipiente ignoto").isDefault)
    }

    @Test
    fun `lookup ignores case and accents`() {
        assertEquals(1.00, PhysicalData.density("Caffè").valore, 1e-9)
        assertEquals(0.95, PhysicalData.density("PURÈ").valore, 1e-9)
        assertEquals(150.0, PhysicalData.unitWeight("Mela").valore, 1e-9)
    }
}
