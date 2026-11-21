package com.ugocalc.app.ui.home

import com.ugocalc.app.data.local.db.entity.AnalysisHistoryEntity
import com.ugocalc.app.domain.glycemic.GlycemicCurveCalculator
import com.ugocalc.app.domain.glycemic.GlycemicProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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

    @Test
    fun `buildMealTimelineInput uses saved glycemicLoad and simulates daily timeline`() {
        val meal = AnalysisHistoryEntity(
            id = 1L,
            foodTitle = "Pranzo",
            analyzedAtEpochMillis = 1_700_000_000_000L,
            totalChoG = 60.0,
            totalGrassiG = 12.0,
            totalProteineG = 25.0,
            glycemicLoad = 29.4,
            modelDisplayNamesJson = "[]",
            detailsJson = "[]",
        )
        val input = buildMealTimelineInput(meal, Json { ignoreUnknownKeys = true })
        assertEquals(29.4, input.glycemicLoad, 1e-6)
        assertEquals(25.0, input.proteineG, 1e-6)

        val timeline = GlycemicCurveCalculator.simulateDayTimeline(listOf(input), GlycemicProfile())
        assertNotNull(timeline)
        assertEquals(29.4, timeline!!.totalDailyGl, 1e-6)
        assertTrue(timeline.peakMgDl > 90.0)
    }

    @Test
    fun `buildMealTimelineInput estimates glycemicLoad when legacy history entry has zero gl`() {
        val legacyMeal = AnalysisHistoryEntity(
            id = 2L,
            foodTitle = "Colazione legacy",
            analyzedAtEpochMillis = 1_700_000_000_000L,
            totalChoG = 40.0,
            totalGrassiG = 5.0,
            totalProteineG = 8.0,
            glycemicLoad = 0.0,
            modelDisplayNamesJson = "[]",
            detailsJson = "[]",
        )
        val input = buildMealTimelineInput(legacyMeal, Json { ignoreUnknownKeys = true })
        assertTrue(input.glycemicLoad > 0.0)
    }
}
