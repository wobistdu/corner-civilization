package com.example.cornercivilization.p37f776dc.domain.model

import java.time.LocalDate

sealed class WorldCommand {
    data class Open(val today: LocalDate, val sessionId: String = "") : WorldCommand()
    data class Tick(val slice: TimeSlice) : WorldCommand()
    data class TargetHit(
        val eventId: String,
        val targetId: String,
        val inputSequence: Long,
        val growthActive: Boolean,
        val battleActive: Boolean,
    ) : WorldCommand()
    data class Intervene(
        val kind: InterventionKind,
        val plotId: String,
        val inputSequence: Long,
    ) : WorldCommand()
    data class Reset(val today: LocalDate, val reason: ResetReason) : WorldCommand()
}

enum class WorldEventType {
    DAY_ROLLED, GROWTH_QUALIFIED, ERA_ADVANCED, INVASION_STARTED, BATTLE_STARTED,
    HIT_ACCEPTED, INVASION_DEFEATED, CIVILIZATION_RESET, INTERVENTION_APPLIED, COMMAND_REJECTED,
}

data class WorldEvent(
    val type: WorldEventType,
    val civilizationId: String,
    val revision: Long,
    val eventId: String? = null,
    val plotId: String? = null,
    val fromEra: Era? = null,
    val toEra: Era? = null,
    val hits: Int? = null,
    val intervention: InterventionKind? = null,
    val reason: String? = null,
)

data class Transition(
    val state: WorldState,
    val events: List<WorldEvent> = emptyList(),
    val persistence: Persistence = Persistence.NONE,
)
