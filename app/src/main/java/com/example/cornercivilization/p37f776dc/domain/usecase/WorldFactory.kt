package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import java.time.LocalDate

object WorldFactory {
    fun create(today: LocalDate, seed: Long = 1L, lineageId: String = "local"): WorldState {
        require(lineageId.isNotBlank() && lineageId.length <= 128)
        return WorldState(
            civilizationId = "$lineageId:0",
            era = Era.PRIMITIVE,
            day = DayProgress(today),
            city = newCity(seed),
            lineageId = lineageId,
        )
    }

    internal fun reset(state: WorldState, today: LocalDate, reason: ResetReason): Transition {
        check(state.civilizationGeneration < Long.MAX_VALUE)
        val generation = state.civilizationGeneration + 1
        val next = WorldState(
            civilizationId = "${state.lineageId}:$generation",
            era = Era.PRIMITIVE,
            day = DayProgress(today),
            city = newCity(state.city.seed + 1),
            revision = state.revision,
            lineageId = state.lineageId,
            civilizationGeneration = generation,
        )
        return updated(state, next, Persistence.CRITICAL, listOf(
            WorldEvent(WorldEventType.CIVILIZATION_RESET, next.civilizationId, 0, reason = reason.name),
        ))
    }

    private fun newCity(seed: Long) = CityState(
        seed = seed,
        resources = 10,
        plots = List(9) { PlotState("plot-$it") },
        residents = List(6) {
            ResidentState("resident-$it", ResidentAction.GATHER, seed + it)
        },
    )
}

internal fun updated(
    previous: WorldState,
    next: WorldState,
    persistence: Persistence = Persistence.CHECKPOINT,
    events: List<WorldEvent> = emptyList(),
): Transition {
    if (previous == next) return Transition(previous, events)
    check(previous.revision < Long.MAX_VALUE)
    val revised = next.copy(revision = previous.revision + 1)
    return Transition(revised, events.map { it.copy(revision = revised.revision) }, persistence)
}

internal fun rejected(state: WorldState, reason: String) = Transition(state, listOf(
    WorldEvent(WorldEventType.COMMAND_REJECTED, state.civilizationId, state.revision, reason = reason),
))

internal fun saturatedAdd(a: Long, b: Long): Long = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
internal fun saturatedMultiply(a: Long, b: Long): Long =
    if (a == 0L || b == 0L) 0 else if (a > Long.MAX_VALUE / b) Long.MAX_VALUE else a * b
