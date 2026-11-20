package com.ugocalc.app.domain.inference.m3

import com.ugocalc.app.data.local.db.dao.PhysicalDataDao
import com.ugocalc.app.data.local.db.entity.PhysicalDataEntity
import com.ugocalc.app.data.local.db.entity.PhysicalDataType
import com.ugocalc.app.data.repository.PhysicalDataRepository
import com.ugocalc.app.domain.inference.model.CalculationChain
import com.ugocalc.app.domain.inference.model.ConfidenceInterval
import com.ugocalc.app.domain.inference.model.EstimationStrategy
import com.ugocalc.app.domain.inference.model.WeightEstimate
import com.ugocalc.app.domain.model.ConfidenceScore
import kotlinx.coroutines.runBlocking
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

    @Test
    fun `custom unit weights override static weights and custom densities preserve isBulkDensity`() {
        val customData = PhysicalData(
            customDensities = listOf(
                PhysicalValue(0.55, "pasta penne", isDefault = false, isBulkDensity = false),
                PhysicalValue(0.49, "pane casereccio", isDefault = false, isBulkDensity = false),
            ),
            customUnitWeights = listOf(
                PhysicalValue(18.0, "biscotto", isDefault = false),
            ),
        )

        val pasta = customData.density("pasta penne")
        assertEquals(0.55, pasta.valore, 1e-9)
        assertFalse(pasta.isDefault)
        assertTrue(pasta.isBulkDensity)

        val pane = customData.density("pane casereccio")
        assertEquals(0.49, pane.valore, 1e-9)
        assertFalse(pane.isBulkDensity)

        val biscotto = customData.unitWeight("biscotto")
        assertEquals(18.0, biscotto.valore, 1e-9)
        assertFalse(biscotto.isDefault)
    }

    @Test
    fun `PhysicalDataRepository learns unit weight and density with EMA and skips low confidence`() = runBlocking {
        val dao = FakePhysicalDataDao()
        val repo = PhysicalDataRepository(dao)

        val countable = WeightEstimate(
            nome = "biscotto",
            strategia = EstimationStrategy.CONTABILE,
            pesoG = 32.0,
            pesoCrudoG = 32.0,
            intervallo = ConfidenceInterval(25.0, 40.0),
            catenaCalcolo = CalculationChain("f", mapOf("conteggio" to 4.0), emptyList(), "s"),
            confidenza = ConfidenceScore(85),
            usoDefaultFisico = false,
        )
        val lowConfidence = countable.copy(nome = "mela", confidenza = ConfidenceScore(40))
        val spread = WeightEstimate(
            nome = "riso",
            strategia = EstimationStrategy.DISTESA_SUL_PIATTO,
            pesoG = 220.0,
            pesoCrudoG = 80.0,
            intervallo = ConfidenceInterval(60.0, 100.0),
            catenaCalcolo = CalculationChain("f", mapOf("volume_cm3" to 250.0, "resa_cottura" to 2.7), emptyList(), "s"),
            confidenza = ConfidenceScore(80),
            usoDefaultFisico = false,
        )

        repo.learnFromGroundTruth(
            estimates = listOf(countable, lowConfidence, spread),
            groundTruthPesiG = mapOf("biscotto" to 60.0, "mela" to 200.0, "riso" to 90.0),
        )

        // Prima pesata: 60 / 4 = 15g
        val unitsAfterFirst = repo.getCustomUnitWeights()
        assertEquals(1, unitsAfterFirst.size)
        assertEquals(15.0, unitsAfterFirst.single().value, 1e-9)

        // Seconda pesata su "Biscotto" (case-insensitive): 80 / 4 = 20g -> EMA = 0.5*15 + 0.5*20 = 17.5g senza duplicati
        repo.learnFromGroundTruth(listOf(countable.copy(nome = "Biscotto")), mapOf("Biscotto" to 80.0))
        val unitsAfterSecond = repo.getCustomUnitWeights()
        assertEquals(1, unitsAfterSecond.size)
        assertEquals(17.5, unitsAfterSecond.single().value, 1e-9)

        // Densità riso appresa con clamp + EMA (base=0.82, obsDens=(90*2.7)/250=0.972, blended=0.5*0.82+0.5*0.972=0.896)
        val densities = repo.getCustomDensities()
        assertEquals(1, densities.size)
        assertEquals(0.896, densities.single().value, 1e-6)
    }

    private class FakePhysicalDataDao : PhysicalDataDao {
        private var nextId = 1L
        private val items = mutableListOf<PhysicalDataEntity>()

        override suspend fun getAll(): List<PhysicalDataEntity> = items.toList()

        override suspend fun getByType(type: PhysicalDataType): List<PhysicalDataEntity> =
            items.filter { it.type == type }

        override suspend fun insert(entity: PhysicalDataEntity): Long {
            val id = if (entity.id == 0L) nextId++ else entity.id
            items.removeAll { it.id == id }
            items.add(entity.copy(id = id))
            return id
        }

        override suspend fun deleteById(id: Long) {
            items.removeAll { it.id == id }
        }
    }
}
