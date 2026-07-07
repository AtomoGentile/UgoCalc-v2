package com.ugocalc.domain.inference.benchmark

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "benchmark_meals")
data class BenchmarkMealEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val sessionId: String?,
    val mode: String?,
    val photoHash: String?,
    val trueWeightG: Double?,
    val estimatedWeightG: Double?,
    val timestamp: Long = System.currentTimeMillis()
)