package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class FireSliceUseCaseTest {
    private val rules = FireSliceUseCase()
    private fun lit(): FireSliceState {
        val approach = rules.advance(FireSliceState(), 20_000, false)
        assertEquals(FirePhase.APPROACH, approach.phase)
        val working = rules.advance(approach, 1, true)
        return rules.advance(working, 20_000, true)
    }
    @Test fun discoveryWaitsForActualResidentsAndSurvivesLongFrames() {
        val approach = rules.advance(FireSliceState(), 90_000, false)
        assertEquals(FirePhase.APPROACH, approach.phase)
        assertEquals(0, approach.ignitions)
        assertEquals(FirePhase.APPROACH, rules.advance(approach, 100_000, false).phase)
        val fire = rules.advance(approach, 20_001, true)
        assertEquals(FirePhase.BURNING, fire.phase)
        assertTrue(fire.knowsFire)
        assertEquals(1, fire.ignitions)
    }
    @Test fun twoWaterDosesExtinguishThenAutonomousReignitionPreservesKnowledge() {
        var state = lit()
        state = rules.intervene(state, FireTool.WATER)
        assertEquals(45, state.strength)
        assertEquals(state, rules.intervene(state, FireTool.WATER))
        state = rules.advance(state, 500, true)
        state = rules.intervene(state, FireTool.WATER)
        assertEquals(FirePhase.WET, state.phase)
        assertEquals(0, state.strength)
        assertTrue(state.knowsFire)
        assertEquals(state, rules.intervene(state, FireTool.FIRE))
        state = rules.advance(state, 20_000, false)
        state = rules.advance(state, 10_001, true)
        assertEquals(FirePhase.BURNING, state.phase)
        assertEquals(2, state.ignitions)
    }
    @Test fun repeatWaterRestartsWetIntervalWithoutErasingLearning() {
        var state = rules.intervene(lit(), FireTool.WATER)
        state = rules.intervene(rules.advance(state, 500, true), FireTool.WATER)
        state = rules.advance(state, 19_000, false)
        state = rules.intervene(state, FireTool.WATER)
        assertEquals(0L, state.phaseMillis)
        assertTrue(state.knowsFire)
        assertEquals(FirePhase.WET, rules.advance(state, 19_999, false).phase)
    }
    @Test fun giftedFireRequiresActualObservationAndObservationToolIsInert() {
        val fire = rules.intervene(FireSliceState(), FireTool.FIRE)
        assertFalse(fire.knowsFire)
        assertEquals(fire, rules.intervene(fire, FireTool.OBSERVE))
        assertFalse(rules.observeFire(fire, false).knowsFire)
        assertTrue(rules.observeFire(fire, true).knowsFire)
        assertEquals(1, rules.intervene(fire, FireTool.FIRE).ignitions)
    }
}
