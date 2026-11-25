package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import java.time.LocalDate

class BattleUseCase(private val config: RuleConfig = RuleConfig()) {
    fun start(state: WorldState, today: LocalDate): Transition {
        if (today != state.day.date || state.era != Era.SPACE || state.invasion != null ||
            state.day.spaceUseMillis < RuleConfig.INVASION_THRESHOLD_MILLIS ||
            state.lastInvasionHandledDate == today) return Transition(state)
        val eventId = "${state.civilizationId}:invasion:${today.toEpochDay()}"
        val invasion = InvasionState(eventId, state.civilizationId, today, "$eventId:ship",
            InvasionPhase.WARNING, config.warningMillis)
        return updated(state, state.copy(invasion = invasion), Persistence.CRITICAL, listOf(
            WorldEvent(WorldEventType.INVASION_STARTED, state.civilizationId, 0, eventId = eventId),
        ))
    }

    /** Consumes at most the current phase. WorldEngine forwards remaining delta to the next phase. */
    fun advance(state: WorldState, slice: TimeSlice): Transition {
        val invasion = state.invasion ?: return Transition(state)
        if (slice.elapsedMillis < 0) return rejected(state, "NEGATIVE_ELAPSED")
        if (!slice.growthActive || !slice.battleActive || slice.elapsedMillis == 0L) return Transition(state)
        val remaining = (invasion.remainingMillis - slice.elapsedMillis.coerceAtMost(invasion.remainingMillis))
        if (remaining > 0) return updated(state,
            state.copy(invasion = invasion.copy(remainingMillis = remaining)))
        if (invasion.phase == InvasionPhase.FIGHTING) {
            return WorldFactory.reset(state, slice.date, ResetReason.INVASION_LOST)
        }
        return updated(state, state.copy(invasion = invasion.copy(
            phase = InvasionPhase.FIGHTING, remainingMillis = config.battleMillis,
        )), Persistence.CRITICAL, listOf(
            WorldEvent(WorldEventType.BATTLE_STARTED, state.civilizationId, 0, eventId = invasion.eventId),
        ))
    }

    fun targetHit(state: WorldState, command: WorldCommand.TargetHit): Transition {
        val invasion = state.invasion ?: return rejected(state, "NO_ACTIVE_INVASION")
        if (!command.growthActive || !command.battleActive) return rejected(state, "BATTLE_INACTIVE")
        if (invasion.phase != InvasionPhase.FIGHTING) return rejected(state, "NOT_FIGHTING")
        if (invasion.civilizationId != state.civilizationId || command.eventId != invasion.eventId ||
            command.targetId != invasion.targetId) return rejected(state, "WRONG_TARGET")
        if (command.inputSequence < 0 || command.inputSequence <= invasion.lastAcceptedInputSequence)
            return rejected(state, "DUPLICATE_INPUT")
        val hits = invasion.hits + 1
        val hitEvent = WorldEvent(WorldEventType.HIT_ACCEPTED, state.civilizationId, 0,
            eventId = invasion.eventId, hits = hits)
        if (hits >= RuleConfig.REQUIRED_HITS) {
            return updated(state, state.copy(invasion = null,
                lastInvasionHandledDate = state.day.date), Persistence.CRITICAL, listOf(hitEvent,
                WorldEvent(WorldEventType.INVASION_DEFEATED, state.civilizationId, 0,
                    eventId = invasion.eventId, hits = hits)))
        }
        return updated(state, state.copy(invasion = invasion.copy(
            hits = hits, lastAcceptedInputSequence = command.inputSequence,
        )), Persistence.CRITICAL, listOf(hitEvent))
    }
}
