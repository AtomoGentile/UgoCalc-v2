package com.ugocalc.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeViewModelTest {

    @Test
    fun `greetingForHour returns Italian time-of-day greeting across all 24 hours`() {
        for (hour in 5..12) {
            assertEquals("Expected Buongiorno at hour $hour", "Buongiorno", greetingForHour(hour))
        }
        for (hour in 13..17) {
            assertEquals("Expected Buon pomeriggio at hour $hour", "Buon pomeriggio", greetingForHour(hour))
        }
        for (hour in listOf(0, 1, 2, 3, 4, 18, 19, 20, 21, 22, 23)) {
            assertEquals("Expected Buonasera at hour $hour", "Buonasera", greetingForHour(hour))
        }
    }
}
