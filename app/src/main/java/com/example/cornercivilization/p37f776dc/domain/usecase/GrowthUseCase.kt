package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import java.time.LocalDate

class GrowthUseCase {
    fun rollDay(state: WorldState, today: LocalDate): Transition {
        if (today < state.day.date) return rejected(state, "DATE_BEFORE_STATE")
        if (today == state.day.date) return Transition(state)
        return updated(state, state.copy(day = DayProgress(today)), Persistence.CRITICAL, listOf(
            WorldEvent(WorldEventType.DAY_ROLLED, state.civilizationId, 0),
        ))
    }

    fun open(state: WorldState, today: LocalDate): Transition {
        val rolled = rollDay(state, today)
        if (today < state.day.date) return rolled
        var next = rolled.state
        if (next.lastOpenDate == today) return rolled
        next = next.copy(lastOpenDate = today)
        val events = rolled.events.toMutableList()
        val qualification = next.qualification
        if (qualification != null) {
            val valid = qualification.civilizationId == next.civilizationId &&
                qualification.sourceEra == next.era && next.era.next() == qualification.targetEra
            if (!valid) {
                events += WorldEvent(WorldEventType.COMMAND_REJECTED, next.civilizationId, 0,
                    reason = "INVALID_QUALIFICATION")
            } else if (today > qualification.earnedOnDate && next.lastUpgradeDate != today) {
                events += WorldEvent(WorldEventType.ERA_ADVANCED, next.civilizationId, 0,
                    fromEra = next.era, toEra = qualification.targetEra)
                next = next.copy(
                    era = qualification.targetEra,
                    qualification = null,
                    lastUpgradeDate = today,
                    day = next.day.copy(spaceUseMillis = 0),
                )
            }
        }
        return updated(state, next, Persistence.CRITICAL, events)
    }

    /** WorldEngine supplies a same-day slice; this method never advances battle time. */
    fun advance(state: WorldState, slice: TimeSlice): Transition {
        if (slice.elapsedMillis < 0) return rejected(state, "NEGATIVE_ELAPSED")
        if (slice.date != state.day.date) return rejected(state, "UNSPLIT_DAY")
        if (!slice.growthActive || slice.elapsedMillis == 0L) return Transition(state)
        var next = state.copy(day = state.day.copy(
            growthMillis = saturatedAdd(state.day.growthMillis, slice.elapsedMillis),
            spaceUseMillis = if (state.era == Era.SPACE)
                saturatedAdd(state.day.spaceUseMillis, slice.elapsedMillis) else state.day.spaceUseMillis,
        ))
        var persistence = Persistence.CHECKPOINT
        val events = mutableListOf<WorldEvent>()
        val target = next.era.next()
        if (next.day.growthMillis > RuleConfig.GROWTH_THRESHOLD_MILLIS &&
            next.qualification == null && target != null) {
            next = next.copy(qualification = GrowthQualification(
                id = "${next.civilizationId}:${next.era.name}:${next.day.date.toEpochDay()}",
                civilizationId = next.civilizationId,
                earnedOnDate = next.day.date,
                sourceEra = next.era,
                targetEra = target,
            ))
            events += WorldEvent(WorldEventType.GROWTH_QUALIFIED, next.civilizationId, 0)
            persistence = Persistence.CRITICAL
        }
        return updated(state, next, persistence, events)
    }
}
