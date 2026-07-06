package com.ugocalc.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MotionSampleBufferTest {

    @Test
    fun `an empty buffer has no peak near any timestamp`() {
        val buffer = MotionSampleBuffer()
        assertNull(buffer.peakNear(1_000L))
    }

    @Test
    fun `the highest sample within the window is returned`() {
        val buffer = MotionSampleBuffer()
        buffer.record(1_000L, 0.2)
        buffer.record(1_100L, 3.5)
        buffer.record(1_200L, 1.0)
        assertEquals(3.5, buffer.peakNear(1_100L, windowMillis = 200L)!!, 0.0001)
    }

    @Test
    fun `a sample exactly at the window edge is included`() {
        val buffer = MotionSampleBuffer()
        buffer.record(1_000L, 2.0)
        assertEquals(2.0, buffer.peakNear(1_500L, windowMillis = 500L)!!, 0.0001)
    }

    @Test
    fun `a sample just outside the window is excluded`() {
        val buffer = MotionSampleBuffer()
        buffer.record(1_000L, 2.0)
        assertNull(buffer.peakNear(1_501L, windowMillis = 500L))
    }

    @Test
    fun `samples older than the retention window are dropped as new ones arrive`() {
        val buffer = MotionSampleBuffer(retentionMillis = 1_000L)
        buffer.record(0L, 9.0)
        buffer.record(2_000L, 0.1) // supera la ritenzione: il campione a t=0 va scartato
        assertNull(buffer.peakNear(0L, windowMillis = 100L))
    }
}
