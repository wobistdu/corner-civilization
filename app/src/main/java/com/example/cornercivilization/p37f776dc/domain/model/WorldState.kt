package com.example.cornercivilization.p37f776dc.domain.model

import java.time.LocalDate

enum class Era { PRIMITIVE, AGRICULTURAL, INDUSTRIAL, MODERN, SPACE;
    fun next(): Era? = entries.getOrNull(ordinal + 1)
}

enum class InvasionPhase { WARNING, FIGHTING }
enum class InterventionKind { WATER, FIRE, GRANT_RESOURCE }
enum class ResidentAction { GATHER, BUILD, REST, EVADE, RESCUE }
enum class ResetReason { INVASION_LOST, RECOVERY_NEW_GAME }
enum class Persistence { NONE, CHECKPOINT, CRITICAL }

data class DayProgress(
    val date: LocalDate,
    val growthMillis: Long = 0,
    val spaceUseMillis: Long = 0,
)

data class GrowthQualification(
    val id: String,
    val civilizationId: String,
    val earnedOnDate: LocalDate,
    val sourceEra: Era,
    val targetEra: Era,
)

data class InvasionState(
    val eventId: String,
    val civilizationId: String,
    val triggerDate: LocalDate,
    val targetId: String,
    val phase: InvasionPhase,
    val remainingMillis: Long,
    val hits: Int = 0,
    val lastAcceptedInputSequence: Long = -1,
)

data class PlotState(
    val id: String,
    val moisture: Int = 0,
    val fire: Int = 0,
    val resourceUnits: Long = 0,
)

data class ResidentState(
    val id: String,
    val action: ResidentAction,
    val actionSeed: Long,
)

data class CityState(
    val seed: Long,
    val resources: Long,
    val plots: List<PlotState>,
    val residents: List<ResidentState>,
    val lastInterventionSequence: Long = -1,
    val autonomyMillis: Long = 0,
    val constructionProgress: Long = 0,
)

data class WorldState(
    val civilizationId: String,
    val era: Era,
    val day: DayProgress,
    val city: CityState,
    val qualification: GrowthQualification? = null,
    val lastUpgradeDate: LocalDate? = null,
    val lastOpenDate: LocalDate? = null,
    val invasion: InvasionState? = null,
    val lastInvasionHandledDate: LocalDate? = null,
    val revision: Long = 0,
    val lineageId: String = "local",
    val civilizationGeneration: Long = 0,
)

data class TimeSlice(
    val date: LocalDate,
    val elapsedMillis: Long,
    val growthActive: Boolean,
    val battleActive: Boolean,
)

/** Versioned first-playable defaults, not new product requirements. */
data class RuleConfig(
    val version: Int = 1,
    val warningMillis: Long = 5_000,
    val battleMillis: Long = 20_000,
    val waterMoisture: Int = 25,
    val waterFireReduction: Int = 40,
    val fireIncrease: Int = 35,
    val resourceGrant: Long = 20,
    val autonomyStepMillis: Long = 5_000,
    val buildResourceCost: Long = 5,
) {
    init {
        require(version > 0 && warningMillis > 0 && battleMillis > 0)
        require(waterMoisture in 1..100 && waterFireReduction in 1..100 && fireIncrease in 1..100)
        require(resourceGrant > 0 && autonomyStepMillis > 0 && buildResourceCost > 0)
    }

    companion object {
        const val GROWTH_THRESHOLD_MILLIS = 600_000L
        const val INVASION_THRESHOLD_MILLIS = 300_000L
        const val REQUIRED_HITS = 10
    }
}
