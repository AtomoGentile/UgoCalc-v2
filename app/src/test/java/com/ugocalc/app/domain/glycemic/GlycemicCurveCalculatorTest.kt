package com.ugocalc.app.domain.glycemic

import com.ugocalc.app.domain.model.CalculatedFoodLine
import com.ugocalc.app.domain.model.Confidence
import com.ugocalc.app.domain.model.FoodMatch
import com.ugocalc.app.domain.model.NormalizedFoodEntry
import com.ugocalc.app.domain.model.RecognizedFood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlycemicCurveCalculatorTest {

    private fun line(
        nome: String,
        weightG: Double,
        choPer100g: Double,
        grassiPer100g: Double,
        proteinePer100g: Double = 0.0,
        indiceGlicemico: Int? = null,
        excluded: Boolean = false,
    ): CalculatedFoodLine {
        val entry = NormalizedFoodEntry(
            nome = nome,
            choPer100g = choPer100g,
            grassiPer100g = grassiPer100g,
            proteinePer100g = proteinePer100g,
            indiceGlicemico = indiceGlicemico,
        )
        return CalculatedFoodLine(
            recognizedFood = RecognizedFood(nome = nome, pesoStimatoG = weightG, commento = "", confidenza = Confidence.ALTO),
            match = if (excluded) null else FoodMatch(nome, nome, false, Confidence.ALTO),
            tableEntry = if (excluded) null else entry,
        )
    }

    @Test
    fun `classifyGlycemicLoad categorizes low, medium, and high thresholds accurately`() {
        assertEquals(GlycemicLoadLevel.BASSO, GlycemicCurveCalculator.classifyGlycemicLoad(0.0))
        assertEquals(GlycemicLoadLevel.BASSO, GlycemicCurveCalculator.classifyGlycemicLoad(10.0))
        assertEquals(GlycemicLoadLevel.MEDIO, GlycemicCurveCalculator.classifyGlycemicLoad(10.1))
        assertEquals(GlycemicLoadLevel.MEDIO, GlycemicCurveCalculator.classifyGlycemicLoad(19.9))
        assertEquals(GlycemicLoadLevel.ALTO, GlycemicCurveCalculator.classifyGlycemicLoad(20.0))
        assertEquals(GlycemicLoadLevel.ALTO, GlycemicCurveCalculator.classifyGlycemicLoad(42.5))
    }

    @Test
    fun `GlycemicIndexResolver respects explicit GI, zero-CHO rule, and specific Italian-English heuristics`() {
        val explicit = GlycemicIndexResolver.resolve(
            NormalizedFoodEntry("Pasta di semola", 75.0, 1.5, 11.0, indiceGlicemico = 49)
        )
        assertEquals(49, explicit.gi)
        assertFalse(explicit.isEstimated)

        val zeroCho = GlycemicIndexResolver.resolve(
            NormalizedFoodEntry("Petto di pollo", 0.0, 1.5, 30.0, indiceGlicemico = null)
        )
        assertEquals(0, zeroCho.gi)
        assertFalse(zeroCho.isEstimated)

        val brownRice = GlycemicIndexResolver.resolve(NormalizedFoodEntry("Riso integrale cotto", 25.5, 0.8))
        val whiteRice = GlycemicIndexResolver.resolve(NormalizedFoodEntry("Riso bianco cotto", 28.0, 0.2))
        assertTrue(brownRice.isEstimated)
        assertTrue(whiteRice.isEstimated)
        assertTrue("Brown rice GI (${brownRice.gi}) should be lower than white rice (${whiteRice.gi})", brownRice.gi < whiteRice.gi)

        val sweetPotato = GlycemicIndexResolver.resolve(NormalizedFoodEntry("Patate dolci al forno", 20.0, 0.1))
        val bakedPotato = GlycemicIndexResolver.resolve(NormalizedFoodEntry("Patate al forno", 21.0, 4.5))
        assertTrue("Sweet potato GI (${sweetPotato.gi}) should be lower than baked potato (${bakedPotato.gi})", sweetPotato.gi < bakedPotato.gi)

        val unknown = GlycemicIndexResolver.resolve(NormalizedFoodEntry("Alimento esotico ignoto", 30.0, 2.0))
        assertEquals(55, unknown.gi)
        assertTrue(unknown.isEstimated)
    }

    @Test
    fun `high-GI meal peaks earlier and higher than low-GI meal with equal CHO`() {
        val profile = GlycemicProfile()
        val highGiMeal = listOf(
            line("Gallette di riso", weightG = 75.0, choPer100g = 80.0, grassiPer100g = 1.0, proteinePer100g = 6.0, indiceGlicemico = 85)
        ) // 60g CHO, GI 85
        val lowGiMeal = listOf(
            line("Lenticchie bollite", weightG = 300.0, choPer100g = 20.0, grassiPer100g = 0.5, proteinePer100g = 6.0, indiceGlicemico = 30)
        ) // 60g CHO, GI 30

        val highResult = GlycemicCurveCalculator.simulateMeal(highGiMeal, profile)
        val lowResult = GlycemicCurveCalculator.simulateMeal(lowGiMeal, profile)

        assertEquals(25, highResult.points.size)
        assertEquals(0, highResult.points.first().minutesFromMeal)
        assertEquals(240, highResult.points.last().minutesFromMeal)
        assertEquals(GlycemicLoadLevel.ALTO, highResult.glycemicLoadLevel)
        assertEquals(GlycemicLoadLevel.MEDIO, lowResult.glycemicLoadLevel)
        assertTrue(
            "High-GI peak time (${highResult.peakTimeMinutes}m) should be earlier than low-GI (${lowResult.peakTimeMinutes}m)",
            highResult.peakTimeMinutes < lowResult.peakTimeMinutes,
        )
        assertTrue(
            "High-GI peak (${highResult.peakMgDl}) should be higher than low-GI (${lowResult.peakMgDl})",
            highResult.peakMgDl > lowResult.peakMgDl,
        )
        assertFalse(highResult.hasEstimatedGiFoods)
    }

    @Test
    fun `fat and protein delay gastric peak and produce FPU late wave (pizza effect)`() {
        val profile = GlycemicProfile(
            preset = GlycemicProfilePreset.SENSIBILITA_MODERATA,
            baselineGlucoseMgDl = 100.0,
            carbSensitivityMgDlPer10g = 28.0,
            targetPostPrandialMaxMgDl = 160.0,
        )
        val leanCarbs = listOf(
            line("Pane bianco", weightG = 100.0, choPer100g = 60.0, grassiPer100g = 0.5, proteinePer100g = 6.0, indiceGlicemico = 70)
        )
        val pizzaEffectMeal = listOf(
            line("Pizza margherita", weightG = 200.0, choPer100g = 30.0, grassiPer100g = 16.0, proteinePer100g = 15.0, indiceGlicemico = 70)
        ) // same 60g CHO & GI 70, but 32g fat + 30g protein -> FPU = (288 + 120)/100 = 4.08

        val leanResult = GlycemicCurveCalculator.simulateMeal(leanCarbs, profile)
        val pizzaResult = GlycemicCurveCalculator.simulateMeal(pizzaEffectMeal, profile)

        assertTrue("Expected FPU >= 3.5 for pizza meal, got ${pizzaResult.fpuCount}", pizzaResult.fpuCount >= 3.5)
        assertTrue(
            "Pizza meal should peak later (${pizzaResult.peakTimeMinutes}m) than lean carbs (${leanResult.peakTimeMinutes}m)",
            pizzaResult.peakTimeMinutes > leanResult.peakTimeMinutes,
        )
        val pizzaPoint180 = pizzaResult.points.first { it.minutesFromMeal == 180 }
        val leanPoint180 = leanResult.points.first { it.minutesFromMeal == 180 }
        assertTrue("Expected delayed FPU delta at 180m > 10 mg/dL", pizzaPoint180.fatProteinDeltaMgDl > 10.0)
        assertTrue(
            "Pizza glucose at 180m (${pizzaResult.glucoseAt180MinMgDl}) should exceed lean meal (${leanResult.glucoseAt180MinMgDl})",
            pizzaResult.glucoseAt180MinMgDl > leanResult.glucoseAt180MinMgDl,
        )
        assertEquals(0.0, leanPoint180.fatProteinDeltaMgDl, 0.001)
    }

    @Test
    fun `zero-carb meals produce zero GL and flat curve when FPU is negligible`() {
        val profile = GlycemicProfile(baselineGlucoseMgDl = 92.0)
        val water = listOf(line("Acqua", weightG = 250.0, choPer100g = 0.0, grassiPer100g = 0.0, proteinePer100g = 0.0, indiceGlicemico = 0))
        val waterResult = GlycemicCurveCalculator.simulateMeal(water, profile)

        assertEquals(0.0, waterResult.totalGlycemicLoad, 1e-6)
        assertEquals(GlycemicLoadLevel.BASSO, waterResult.glycemicLoadLevel)
        assertEquals(92.0, waterResult.peakMgDl, 1e-6)
        assertEquals(0.0, waterResult.deltaPeakMgDl, 1e-6)
        assertEquals(0, waterResult.peakTimeMinutes)
        assertTrue(waterResult.points.all { it.glucoseMgDl == 92.0 })

        val proteinFatMeal = listOf(
            line("Salmone", weightG = 200.0, choPer100g = 0.0, grassiPer100g = 12.0, proteinePer100g = 22.0, indiceGlicemico = 0)
        )
        val pfResult = GlycemicCurveCalculator.simulateMeal(proteinFatMeal, profile)
        assertEquals(0.0, pfResult.totalGlycemicLoad, 1e-6)
        assertTrue(pfResult.fpuCount > 3.5)
        assertTrue("Expected mild late FPU rise for high-protein/fat zero-carb meal", pfResult.deltaPeakMgDl > 5.0)
        assertTrue("Peak for zero-carb FPU meal should occur late (~175 min)", pfResult.peakTimeMinutes >= 150)
    }

    @Test
    fun `simulateFromTotals supports both explicit GL and legacy zero-GL history records`() {
        val profile = GlycemicProfile()
        val explicit = GlycemicCurveCalculator.simulateFromTotals(
            choG = 50.0,
            grassiG = 10.0,
            proteineG = 15.0,
            glycemicLoad = 35.0,
            profile = profile,
        )
        assertEquals(35.0, explicit.totalGlycemicLoad, 1e-6)
        assertEquals(70.0, explicit.effectiveMealGi, 1e-6)
        assertFalse(explicit.hasEstimatedGiFoods)

        // Legacy migrated entry has glycemicLoad == 0.0 despite choG > 0 -> falls back to effectiveGi = 55
        val legacy = GlycemicCurveCalculator.simulateFromTotals(
            choG = 50.0,
            grassiG = 10.0,
            proteineG = 0.0,
            glycemicLoad = 0.0,
            profile = profile,
        )
        assertEquals(27.5, legacy.totalGlycemicLoad, 1e-6)
        assertEquals(55.0, legacy.effectiveMealGi, 1e-6)
        assertTrue(legacy.hasEstimatedGiFoods)
        assertTrue(legacy.peakMgDl > profile.baselineGlucoseMgDl)
    }

    @Test
    fun `simulateDayTimeline returns null when empty and superimposes multiple meals chronologically`() {
        val profile = GlycemicProfile(baselineGlucoseMgDl = 90.0, targetPostPrandialMaxMgDl = 140.0)
        assertNull(GlycemicCurveCalculator.simulateDayTimeline(emptyList(), profile))

        val baseTime = 1_700_000_000_000L
        val lunch = MealTimelineInput(
            title = "Pranzo",
            analyzedAtEpochMillis = baseTime + 4 * 3_600_000L,
            choG = 70.0,
            grassiG = 15.0,
            proteineG = 25.0,
            glycemicLoad = 38.0,
        )
        val breakfast = MealTimelineInput(
            title = "Colazione",
            analyzedAtEpochMillis = baseTime,
            choG = 40.0,
            grassiG = 6.0,
            proteineG = 10.0,
            glycemicLoad = 22.0,
        )

        // Pass out of chronological order to verify sorting
        val timeline = GlycemicCurveCalculator.simulateDayTimeline(listOf(lunch, breakfast), profile)
        assertNotNull(timeline)
        timeline!!

        assertEquals(2, timeline.mealMarkers.size)
        assertEquals("Colazione", timeline.mealMarkers[0].second)
        assertEquals("Pranzo", timeline.mealMarkers[1].second)
        assertEquals(baseTime - 15 * 60_000L, timeline.points.first().epochMillis)
        assertEquals(baseTime + 4 * 3_600_000L + 240 * 60_000L, timeline.points.last().epochMillis)
        assertEquals(90.0, timeline.points.first().glucoseMgDl, 1e-6)
        assertEquals(60.0, timeline.totalDailyGl, 1e-6)
        assertEquals(140.0, timeline.targetMaxMgDl, 1e-6)
        assertTrue("Expected daytime peak well above baseline", timeline.peakMgDl > 130.0)
    }
}
