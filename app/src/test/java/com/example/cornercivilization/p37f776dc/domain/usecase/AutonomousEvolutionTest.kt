package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class AutonomousEvolutionTest {
    private val rules = FireSliceUseCase()
    private fun complete() = FireSliceState(phase = FirePhase.BURNING, strength = 100, knowsFire = true,
        shelter = ShelterPhase.COMPLETE, sourceWood = 0, spentWood = 8, buildMillis = 12_000)

    @Test fun processClockNeverEstablishesAField() {
        val old = complete().copy(activeMillis = 9_000_000)
        assertFalse(old.agricultural)
        val justBefore = rules.evolve(old, 599_999)
        assertFalse(justBefore.agricultural)
        assertFalse(rules.evolve(justBefore, 1).agricultural)
        assertEquals(600_000L, rules.evolve(justBefore, 1).evolutionMillis)
        assertFalse(rules.evolve(old, 9_000_000).agricultural)
        assertTrue(rules.evolve(old.copy(fieldEstablished=true), 9_000_000).agricultural)
    }
    @Test fun giftsAreBoundedAndNeverGoStraightToInventory() {
        var state = complete()
        repeat(10) { state = rules.intervene(state.copy(activeMillis = it * 1_000L), FireTool.WOOD) }
        assertEquals(12, state.pendingWood)
        assertEquals(0, state.storedWood)
        assertEquals(12, state.suppliedWood)
        assertEquals(state, rules.intervene(state, FireTool.WOOD))
    }
    @Test fun ownershipChangesOnlyAfterArrivalAndNoDuplicateDelivery() {
        val fetch = rules.advanceResources(complete(), false)
        assertEquals(ResourcePhase.FETCHING, fetch.resourcePhase)
        assertEquals(fetch, rules.advanceResources(fetch, false))
        val carry = rules.advanceResources(fetch, true)
        assertEquals(4, carry.haulingWood); assertEquals(0, carry.pendingWood)
        assertEquals(carry, rules.advanceResources(carry, false))
        val delivered = rules.advanceResources(carry, true)
        assertEquals(4, delivered.storedWood); assertEquals(1, delivered.deliveries)
        assertEquals(delivered, rules.advanceResources(delivered.copy(), true))
    }
    @Test fun stormsInterruptWithoutLosingHeldWood() {
        val carry = rules.advanceResources(rules.advanceResources(complete(), false), true)
        val wet = carry.copy(phase = FirePhase.WET, strength = 0)
        assertEquals(wet, rules.advanceResources(wet, true))
        assertEquals(4, wet.haulingWood)
        val resumed = rules.advanceResources(wet.copy(phase = FirePhase.BURNING, strength = 100), true)
        assertEquals(4, resumed.storedWood)
    }
    @Test fun weatherAndForagingAreIdempotentAndConserveMaterials() {
        val rain = rules.evolve(complete().copy(activeMillis = 120_000), 120_000)
        assertEquals(85, rain.strength)
        assertEquals(1L, rain.weatherCycle)
        assertEquals(8, rain.pendingWood)
        assertEquals(rain, rules.evolve(rain, 0))
        var state = rain
        repeat(10_000) {
            state = rules.evolve(state.copy(activeMillis = state.activeMillis + 100), 100)
            state = rules.advanceResources(state, true)
            assertEquals(state.suppliedWood, state.pendingWood + state.haulingWood + state.storedWood + state.usedWood)
            assertTrue(state.resourceRoom >= 0)
            assertTrue(state.pendingWood <= 12)
        }
    }
    @Test fun noProgressWithoutBuildingAndNoNegativeDeltas() {
        val initial = FireSliceState()
        assertEquals(initial, rules.advanceResources(initial, true))
        assertEquals(initial, rules.evolve(initial, 0))
        assertThrows(IllegalArgumentException::class.java) { rules.evolve(initial, -1) }
    }
}
