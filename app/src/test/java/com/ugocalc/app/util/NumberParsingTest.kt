package com.ugocalc.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberParsingTest {

    @Test
    fun `parses standard dot decimals and Italian comma decimals`() {
        assertEquals(12.5, "12.5".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(12.5, "12,5".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(0.0, "0".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(0.0, "0,0".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(42.0, "  42  ".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(3.1415, "  3,1415 ".parseLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(-5.25, "-5,25".parseLocaleDoubleOrNull()!!, 1e-9)
    }

    @Test
    fun `returns null for invalid, empty, or non-finite strings`() {
        assertNull("".parseLocaleDoubleOrNull())
        assertNull("   ".parseLocaleDoubleOrNull())
        assertNull("abc".parseLocaleDoubleOrNull())
        assertNull("12,3,4".parseLocaleDoubleOrNull())
        assertNull("12.3.4".parseLocaleDoubleOrNull())
        assertNull("NaN".parseLocaleDoubleOrNull())
        assertNull("Infinity".parseLocaleDoubleOrNull())
        assertNull("-Infinity".parseLocaleDoubleOrNull())
    }

    @Test
    fun `parsePositiveLocaleDoubleOrNull accepts strictly positive numbers only`() {
        assertEquals(0.1, "0,1".parsePositiveLocaleDoubleOrNull()!!, 1e-9)
        assertEquals(150.5, " 150,5 ".parsePositiveLocaleDoubleOrNull()!!, 1e-9)
        assertNull("0".parsePositiveLocaleDoubleOrNull())
        assertNull("0,0".parsePositiveLocaleDoubleOrNull())
        assertNull("-0.0".parsePositiveLocaleDoubleOrNull())
        assertNull("-1,5".parsePositiveLocaleDoubleOrNull())
        assertNull("NaN".parsePositiveLocaleDoubleOrNull())
        assertNull("Infinity".parsePositiveLocaleDoubleOrNull())
        assertNull("".parsePositiveLocaleDoubleOrNull())
    }
}
