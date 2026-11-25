package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ShelterPrototypeTest {
    private val rules = FireSliceUseCase()
    private fun lit() = FireSliceState(phase = FirePhase.BURNING, strength = 100, knowsFire = true)
    private fun delivered(): FireSliceState {
        var state = rules.intervene(lit(), FireTool.WATER)
        state = rules.advanceShelter(state, 100, false)
        state = rules.advanceShelter(state, 100, true)
        return rules.advanceShelter(state, 100, true)
    }
    @Test fun materialOwnershipCommitsOnlyAtArrival() {
        val fetch = rules.advanceShelter(rules.intervene(lit(), FireTool.WATER), 100, false)
        assertEquals(ShelterPhase.FETCHING, fetch.shelter)
        assertEquals(fetch, rules.advanceShelter(fetch, 90_000, false))
        val carry = rules.advanceShelter(fetch, 100, true)
        assertEquals(0, carry.sourceWood); assertEquals(8, carry.carriedWood)
        assertEquals(carry, rules.advanceShelter(carry, 90_000, false))
        val building = rules.advanceShelter(carry, 100, true)
        assertEquals(8, building.spentWood); assertEquals(0, building.carriedWood)
        assertEquals(0L, building.buildMillis)
    }
    @Test fun reloadAndRepeatedPulsesNeverDuplicateMaterialsOrConstruction() {
        val building = delivered()
        val partial = rules.advanceShelter(building, 5_000, true).copy()
        assertEquals(partial, rules.advanceShelter(partial, 90_000, false))
        val done = rules.advanceShelter(partial, 7_000, true)
        assertEquals(ShelterPhase.COMPLETE, done.shelter)
        assertEquals(done, rules.advanceShelter(done.copy(), Long.MAX_VALUE, true))
        assertEquals(8, done.spentWood)
    }
    @Test fun waterInterruptsWorkAndCompleteShelterProtectsButIsNotImmune() {
        var building = rules.advanceShelter(delivered(), 4_000, true)
        building = rules.intervene(building.copy(activeMillis = 1_000), FireTool.WATER)
        assertEquals(FirePhase.WET, building.phase)
        assertEquals(building, rules.advanceShelter(building, 10_000, true))
        var done = rules.advanceShelter(delivered(), 12_000, true).copy(strength = 100)
        done = rules.intervene(done.copy(activeMillis = 1_000), FireTool.WATER)
        assertEquals(85, done.strength)
        assertEquals(1, done.protectedDoses)
        repeat(6) { done = rules.intervene(done.copy(activeMillis = done.activeMillis + 500), FireTool.WATER) }
        assertEquals(FirePhase.WET, done.phase)
        assertEquals(ShelterPhase.COMPLETE, done.shelter)
        assertEquals(8, done.spentWood)
    }
    @Test fun demonstrationRainRunsOnceAndRespectsPriorPlayerRain() {
        val initial = lit().copy(activeMillis = 70_000)
        val rain = rules.demonstrateRain(initial)
        assertEquals(45, rain.strength)
        assertEquals(1L, rain.waterSequence)
        assertEquals(rain, rules.demonstrateRain(rain.copy()))
        val userRain = rules.intervene(initial, FireTool.WATER)
        assertEquals(userRain.waterSequence, rules.demonstrateRain(userRain).waterSequence)
    }
}
