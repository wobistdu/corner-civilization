package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*

/** No IO, Android objects or clock reads. Caller splits slices at midnight and gate changes. */
class WorldEngine(config: RuleConfig = RuleConfig()) {
    private val growth = GrowthUseCase()
    private val battle = BattleUseCase(config)
    private val city = CityUseCase(config)

    fun dispatch(state: WorldState, command: WorldCommand): Transition = when (command) {
        is WorldCommand.Open -> growth.open(state, command.today)
        is WorldCommand.Tick -> tick(state, command.slice)
        is WorldCommand.TargetHit -> battle.targetHit(state, command)
        is WorldCommand.Intervene -> city.intervene(state, command)
        is WorldCommand.Reset -> if (command.today < state.day.date) rejected(state, "DATE_BEFORE_STATE")
            else WorldFactory.reset(state, command.today, command.reason)
    }

    private fun tick(state: WorldState, slice: TimeSlice): Transition {
        if (slice.elapsedMillis < 0) return rejected(state, "NEGATIVE_ELAPSED")
        if (slice.battleActive && !slice.growthActive) return rejected(state, "INVALID_ACTIVITY_GATES")
        if (slice.date < state.day.date) return rejected(state, "DATE_BEFORE_STATE")
        if (state.invasion?.remainingMillis?.let { it <= 0 } == true)
            return rejected(state, "INVALID_BATTLE_TIME")
        val result = Accumulator(state)
        result.apply(growth.rollDay(result.state, slice.date))
        result.apply(battle.start(result.state, slice.date))
        var remaining = slice.elapsedMillis
        while (remaining > 0) {
            val current = result.state
            val invasionAtStart = current.invasion
            var step = remaining
            if (slice.growthActive) {
                if (current.qualification == null && current.era.next() != null &&
                    current.day.growthMillis <= RuleConfig.GROWTH_THRESHOLD_MILLIS) {
                    step = minOf(step, RuleConfig.GROWTH_THRESHOLD_MILLIS + 1 - current.day.growthMillis)
                }
                if (current.era == Era.SPACE && invasionAtStart == null &&
                    current.lastInvasionHandledDate != slice.date &&
                    current.day.spaceUseMillis < RuleConfig.INVASION_THRESHOLD_MILLIS) {
                    step = minOf(step, RuleConfig.INVASION_THRESHOLD_MILLIS - current.day.spaceUseMillis)
                }
                if (slice.battleActive && invasionAtStart != null)
                    step = minOf(step, invasionAtStart.remainingMillis)
            }
            val part = slice.copy(elapsedMillis = step)
            result.apply(growth.advance(result.state, part))
            result.apply(city.advance(result.state, part))
            if (invasionAtStart != null) result.apply(battle.advance(result.state, part))
            remaining -= step
            result.apply(battle.start(result.state, slice.date))
        }
        return result.finish()
    }

    private class Accumulator(initial: WorldState) {
        var state = initial
            private set
        private val events = mutableListOf<WorldEvent>()
        private var persistence = Persistence.NONE
        fun apply(transition: Transition) {
            state = transition.state
            events += transition.events
            if (transition.persistence.ordinal > persistence.ordinal) persistence = transition.persistence
        }
        fun finish() = Transition(state, events.map { it.copy(revision = state.revision) }, persistence)
    }
}
