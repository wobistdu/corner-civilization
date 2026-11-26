package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*

/** Finite, repeatable local story; no dates, population penalty, or offline catch-up. */
class FireSliceUseCase {
    fun advance(state: FireSliceState, millis: Long, participantsReady: Boolean): FireSliceState {
        require(millis >= 0)
        var next = state.copy(activeMillis = Math.addExact(state.activeMillis, millis))
        var remaining = millis
        while (remaining > 0) {
            val duration = when (next.phase) {
                FirePhase.QUIET -> 20_000L
                FirePhase.APPROACH -> if (participantsReady) 1L else Long.MAX_VALUE
                FirePhase.KINDLING -> if (next.knowsFire) 10_000L else 20_000L
                FirePhase.WET -> 20_000L
                FirePhase.BURNING -> Long.MAX_VALUE
            }
            // Working cannot finish before the actors reach the actual hearth.
            if (next.phase == FirePhase.KINDLING && !participantsReady) return next
            if (duration == Long.MAX_VALUE) return next
            val step = minOf(remaining, (duration - next.phaseMillis).coerceAtLeast(0))
            next = next.copy(phaseMillis = next.phaseMillis + step)
            remaining -= step
            if (next.phaseMillis < duration) return next
            next = when (next.phase) {
                FirePhase.QUIET, FirePhase.WET -> next.copy(phase = FirePhase.APPROACH, phaseMillis = 0)
                FirePhase.APPROACH -> next.copy(phase = FirePhase.KINDLING, phaseMillis = 0)
                FirePhase.KINDLING -> ignite(next)
                FirePhase.BURNING -> next
            }
            // A new approach requires a fresh physical readiness observation.
            if (next.phase == FirePhase.APPROACH) return next
        }
        return next
    }

    fun intervene(state: FireSliceState, tool: FireTool, shelterOperational: Boolean = true): FireSliceState = when (tool) {
        FireTool.OBSERVE -> state
        FireTool.WATER -> {
            if (state.activeMillis - state.lastWaterAt < 500) state
            else {
                val protected = state.shelter == ShelterPhase.COMPLETE && shelterOperational
                val strength = (state.strength - if (protected) 15 else 55).coerceAtLeast(0)
                state.copy(phase = if (strength > 0) FirePhase.BURNING else FirePhase.WET,
                    phaseMillis = 0, strength = strength, waterSequence = state.waterSequence + 1,
                    lastWaterAt = state.activeMillis,
                    shelter = if (state.shelter == ShelterPhase.NONE && state.knowsFire) ShelterPhase.NEEDED else state.shelter,
                    protectedDoses = state.protectedDoses + if (protected && state.strength > 0) 1 else 0)
            }
        }
        FireTool.FIRE -> if (state.phase == FirePhase.WET || state.phase == FirePhase.BURNING) state else ignite(state)
        FireTool.WOOD -> grantWood(state)
        FireTool.DRUM, FireTool.SEED -> state
    }

    private fun ignite(state: FireSliceState) = state.copy(
        phase = FirePhase.BURNING, phaseMillis = 0, strength = 100,
        // Knowledge requires an actual resident at the hearth; gifted fire alone is not knowledge.
        knowsFire = state.knowsFire || state.phase == FirePhase.KINDLING,
        ignitions = state.ignitions + 1,
    )

    fun observeFire(state: FireSliceState, participantsReady: Boolean): FireSliceState =
        if (state.phase == FirePhase.BURNING && participantsReady && !state.knowsFire) state.copy(knowsFire = true) else state

    /** Readiness is sampled from the real worker positions for this exact task, never an animation callback. */
    fun advanceShelter(state: FireSliceState, millis: Long, workersReady: Boolean): FireSliceState {
        require(millis >= 0)
        if (state.phase != FirePhase.BURNING || !state.knowsFire) return state
        return when (state.shelter) {
            ShelterPhase.NEEDED -> state.copy(shelter = ShelterPhase.FETCHING)
            ShelterPhase.FETCHING -> if (workersReady) state.copy(shelter = ShelterPhase.CARRYING, sourceWood = 0, carriedWood = 8) else state
            ShelterPhase.CARRYING -> if (workersReady) state.copy(shelter = ShelterPhase.BUILDING, carriedWood = 0, spentWood = 8) else state
            ShelterPhase.BUILDING -> if (workersReady) {
                val work = (state.buildMillis + millis.coerceAtMost(12_000)).coerceAtMost(12_000)
                state.copy(buildMillis = work, shelter = if (work == 12_000L) ShelterPhase.COMPLETE else state.shelter,
                    strength = if (work == 12_000L) 100 else state.strength)
            } else state
            else -> state
        }
    }

    /** Active time only. Periodic work is bounded and never replays missed weather bursts. */
    fun evolve(state: FireSliceState, millis: Long, shelterOperational:Boolean=true): FireSliceState {
        require(millis >= 0)
        var next = state.copy(evolutionMillis = Math.addExact(state.evolutionMillis, millis))
        val weather = next.evolutionMillis / 120_000
        if (weather > next.weatherCycle) {
            next = next.copy(weatherCycle = weather)
            if (next.shelter == ShelterPhase.COMPLETE && next.phase == FirePhase.BURNING) {
                next = intervene(next, FireTool.WATER, shelterOperational)
            }
        }
        val forage = next.evolutionMillis / 60_000
        if (forage > next.forageCycle) {
            next = next.copy(forageCycle = forage)
            if (next.shelter == ShelterPhase.COMPLETE && next.pendingWood <= 8 && next.resourceRoom >= 4) {
                next = addWood(next)
            }
        }
        val fuel = next.evolutionMillis / 30_000
        if (fuel > next.fuelCycle) {
            val spend = minOf((fuel - next.fuelCycle).coerceAtMost(24).toInt(), next.storedWood)
            next = next.copy(fuelCycle = fuel, storedWood = next.storedWood - spend, usedWood = next.usedWood + spend)
        }
        return next
    }

    private fun addWood(state: FireSliceState) = state.copy(
        pendingWood = state.pendingWood + 4, suppliedWood = Math.addExact(state.suppliedWood, 4),
        resourcePhase = if (state.resourcePhase == ResourcePhase.IDLE) ResourcePhase.WAITING else state.resourcePhase)

    private fun grantWood(state: FireSliceState): FireSliceState =
        if (state.activeMillis - state.lastGiftAt < 1_000 || state.pendingWood > 8 || state.resourceRoom < 4) state
        else addWood(state).copy(lastGiftAt = state.activeMillis)

    /** No magic delivery: ownership only transfers on fresh physical arrival. */
    fun advanceResources(state: FireSliceState, workersReady: Boolean): FireSliceState {
        if (state.shelter != ShelterPhase.COMPLETE || state.phase != FirePhase.BURNING) return state
        return when (state.resourcePhase) {
            ResourcePhase.WAITING -> if (state.pendingWood > 0) state.copy(resourcePhase = ResourcePhase.FETCHING) else state.copy(resourcePhase = ResourcePhase.IDLE)
            ResourcePhase.FETCHING -> if (workersReady && state.pendingWood >= 4) state.copy(
                resourcePhase = ResourcePhase.CARRYING, pendingWood = state.pendingWood - 4, haulingWood = 4) else state
            ResourcePhase.CARRYING -> if (workersReady) state.copy(resourcePhase = ResourcePhase.IDLE,
                haulingWood = 0, storedWood = state.storedWood + state.haulingWood, deliveries = Math.addExact(state.deliveries, 1)) else state
            ResourcePhase.IDLE -> if (state.pendingWood > 0) state.copy(resourcePhase = ResourcePhase.WAITING) else state
        }
    }

    /** One local demonstration shower after discovery; a player's earlier shower replaces it. */
    fun demonstrateRain(state: FireSliceState, shelterOperational: Boolean = true): FireSliceState {
        if (state.rainDemonstrated || !state.knowsFire || state.phase != FirePhase.BURNING || state.activeMillis < 70_000) return state
        return if (state.waterSequence > 0) state.copy(rainDemonstrated = true)
        else intervene(state, FireTool.WATER, shelterOperational).copy(rainDemonstrated = true)
    }
}
