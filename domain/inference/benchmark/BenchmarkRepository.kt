package com.ugocalc.domain.inference.benchmark

import com.ugocalc.data.dao.BenchmarkMealDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class BenchmarkRepository(private val dao: BenchmarkMealDao) {
    suspend fun recordMeal(meal: BenchmarkMealEntity) = withContext(Dispatchers.IO) {
        dao.insert(meal)
    }

    suspend fun getMealsForSession(sessionId: String) = withContext(Dispatchers.IO) {
        dao.getBySessionId(sessionId)
    }
}
