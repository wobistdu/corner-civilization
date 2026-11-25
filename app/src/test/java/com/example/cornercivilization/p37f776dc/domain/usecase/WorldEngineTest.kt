package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorldEngineTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val engine = WorldEngine()
    private fun fresh() = WorldFactory.create(today, seed = 42)
    private fun tick(state: WorldState, millis: Long, growth: Boolean = true, battle: Boolean = true,
        date: LocalDate = state.day.date) = engine.dispatch(state,
        WorldCommand.Tick(TimeSlice(date, millis, growth, battle)))
    private fun open(state: WorldState, date: LocalDate) = engine.dispatch(state, WorldCommand.Open(date)).state
    private fun space() = fresh().copy(era = Era.SPACE)
    private fun fighting(): WorldState = tick(space(), 305_000).state
    private fun hit(state: WorldState, sequence: Long): Transition {
        val invasion = requireNotNull(state.invasion)
        return engine.dispatch(state, WorldCommand.TargetHit(invasion.eventId, invasion.targetId,
            sequence, growthActive = true, battleActive = true))
    }

    @Test fun `599 and exactly 600 seconds fail but 601 qualifies`() {
        for (millis in listOf(599_000L, 600_000L, 601_000L)) {
            val state = tick(fresh(), millis).state
            assertEquals(millis > 600_000, state.qualification != null)
            assertEquals(Era.PRIMITIVE, state.era)
        }
        assertNotNull(tick(fresh(), 600_001).state.qualification)
    }

    @Test fun `qualification is consumed only next day and same-day reopen is idempotent`() {
        val qualified = tick(open(fresh(), today), 601_000).state
        assertEquals(Era.PRIMITIVE, open(qualified, today).era)
        val upgraded = open(qualified, today.plusDays(1))
        assertEquals(Era.AGRICULTURAL, upgraded.era)
        assertNull(upgraded.qualification)
        assertEquals(upgraded, open(upgraded, today.plusDays(1)))
        val againQualified = tick(upgraded, 601_000).state
        assertEquals(Era.AGRICULTURAL, open(againQualified, today.plusDays(1)).era)
        assertEquals(Era.INDUSTRIAL, open(againQualified, today.plusDays(2)).era)
    }

    @Test fun `fastest path reaches space on fifth date not fourth`() {
        var state = fresh()
        val expected = Era.entries
        for (day in 0..4) {
            state = open(state, today.plusDays(day.toLong()))
            assertEquals(expected[day], state.era)
            // No need to run the fifth day's invasion in this growth test.
            if (day < 4) state = tick(state, 600_001).state
        }
        assertNull(state.qualification)
    }

    @Test fun `absence preserves qualification and never pays multiple upgrades`() {
        val qualified = tick(fresh(), 601_000).state
        val upgraded = open(qualified, today.plusDays(7))
        assertEquals(Era.AGRICULTURAL, upgraded.era)
        assertEquals(0L, upgraded.day.growthMillis)
        assertEquals(upgraded, open(upgraded, today.plusDays(7)))
    }

    @Test fun `unqualified time is not carried across midnight`() {
        val before = tick(fresh(), 599_000).state
        val after = tick(before, 2_000, date = today.plusDays(1)).state
        assertEquals(2_000L, after.day.growthMillis)
        assertNull(after.qualification)
    }

    @Test fun `continuous midnight does not upgrade and does not overwrite old eligibility`() {
        val qualified = tick(open(fresh(), today), 600_001).state
        val next = tick(qualified, 600_001, date = today.plusDays(1)).state
        assertEquals(Era.PRIMITIVE, next.era)
        assertEquals(today, next.qualification?.earnedOnDate)
        assertEquals(Era.AGRICULTURAL, open(next, today.plusDays(1)).era)
    }

    @Test fun `wrong civilization wrong era and nonadjacent qualification cannot upgrade`() {
        val state = tick(fresh(), 601_000).state
        val qualification = requireNotNull(state.qualification)
        val bad = listOf(
            qualification.copy(civilizationId = "old"),
            qualification.copy(sourceEra = Era.INDUSTRIAL),
            qualification.copy(targetEra = Era.SPACE),
        )
        bad.forEach {
            val result = open(state.copy(qualification = it), today.plusDays(1))
            assertEquals(Era.PRIMITIVE, result.era)
        }
    }

    @Test fun `growth continues outside view and stops while minimized`() {
        val seenElsewhere = tick(fresh(), 600_001, growth = true, battle = false).state
        assertNotNull(seenElsewhere.qualification)
        val minimized = tick(seenElsewhere, 90_000, growth = false, battle = false).state
        assertEquals(seenElsewhere, minimized)
    }

    @Test fun `negative delta invalid gates and backward date are rejected without mutation`() {
        val initial = fresh()
        for (result in listOf(tick(initial, -1), tick(initial, 1_000, false, true),
            tick(initial, 1_000, date = today.minusDays(1)))) {
            assertEquals(initial, result.state)
            assertEquals(Persistence.NONE, result.persistence)
            assertEquals(WorldEventType.COMMAND_REJECTED, result.events.single().type)
        }
    }

    @Test fun `invasion starts at 300 not 299 seconds even outside view`() {
        val before = tick(space(), 299_000, battle = false).state
        assertNull(before.invasion)
        val result = tick(before, 1_000, battle = false)
        assertEquals(InvasionPhase.WARNING, result.state.invasion?.phase)
        assertEquals(5_000L, result.state.invasion?.remainingMillis)
        assertEquals(Persistence.CRITICAL, result.persistence)
    }

    @Test fun `warning and battle pause outside view while growth continues`() {
        val warning = tick(space(), 300_000, battle = false).state
        val paused = tick(warning, 10_000, battle = false).state
        assertEquals(5_000L, paused.invasion?.remainingMillis)
        assertEquals(310_000L, paused.day.growthMillis)
        val almost = tick(paused, 4_999).state
        assertEquals(InvasionPhase.WARNING, almost.invasion?.phase)
        val fight = tick(almost, 1).state
        assertEquals(InvasionPhase.FIGHTING, fight.invasion?.phase)
        assertEquals(20_000L, fight.invasion?.remainingMillis)
        assertEquals(fight.invasion, tick(fight, 60_000, battle = false).state.invasion)
    }

    @Test fun `only current target fighting phase and distinct active hits count`() {
        val warning = tick(space(), 300_000).state
        assertEquals(warning, hit(warning, 0).state)
        val fight = tick(warning, 5_000).state
        val invasion = requireNotNull(fight.invasion)
        val wrong = engine.dispatch(fight, WorldCommand.TargetHit(invasion.eventId, "other", 0, true, true))
        assertEquals(fight, wrong.state)
        assertEquals(fight, engine.dispatch(fight,
            WorldCommand.TargetHit(invasion.eventId, invasion.targetId, 0, true, false)).state)
        val first = hit(fight, 0).state
        assertEquals(1, first.invasion?.hits)
        assertEquals(first, hit(first, 0).state)
        assertEquals(first, engine.dispatch(first,
            WorldCommand.TargetHit("previous-event", invasion.targetId, 1, true, true)).state)
    }

    @Test fun `nine hits remain fighting and tenth defeats once`() {
        var state = fighting()
        repeat(9) { state = hit(state, it.toLong()).state }
        assertEquals(9, state.invasion?.hits)
        val tenth = hit(state, 9)
        assertNull(tenth.state.invasion)
        assertEquals(today, tenth.state.lastInvasionHandledDate)
        assertEquals(1, tenth.events.count { it.type == WorldEventType.INVASION_DEFEATED })
        assertNull(tick(tenth.state, 1_000_000).state.invasion)
    }

    @Test fun `timeout is exact and an earlier tenth hit wins`() {
        var state = fighting()
        repeat(9) { state = hit(state, it.toLong()).state }
        val beforeDeadline = tick(state, 19_999).state
        assertNotNull(beforeDeadline.invasion)
        assertEquals(Era.SPACE, hit(beforeDeadline, 9).state.era)
        val atDeadline = tick(state, 20_000).state
        assertEquals(Era.PRIMITIVE, atDeadline.era)
        assertNull(atDeadline.invasion)
        // A target callback queued at exactly the deadline arrives after its elapsed slice.
        val old = requireNotNull(state.invasion)
        assertEquals(atDeadline, engine.dispatch(atDeadline,
            WorldCommand.TargetHit(old.eventId, old.targetId, 9, true, true)).state)
    }

    @Test fun `large delta respects trigger warning battle and new-world remainder`() {
        val result = tick(space(), 325_001)
        assertEquals(Era.PRIMITIVE, result.state.era)
        assertEquals(1L, result.state.day.growthMillis)
        assertEquals(0L, result.state.day.spaceUseMillis)
        assertEquals(1L, result.state.civilizationGeneration)
        assertNull(result.state.qualification)
        assertEquals(listOf(WorldEventType.INVASION_STARTED, WorldEventType.BATTLE_STARTED,
            WorldEventType.CIVILIZATION_RESET), result.events.map { it.type })
    }

    @Test fun `failure clears city and progress and new same-day time still waits for tomorrow`() {
        var state = engine.dispatch(fighting(),
            WorldCommand.Intervene(InterventionKind.GRANT_RESOURCE, "plot-0", 0)).state
        val oldId = state.civilizationId
        state = tick(state, 20_000).state
        assertNotEquals(oldId, state.civilizationId)
        assertEquals(Era.PRIMITIVE, state.era)
        assertEquals(10L, state.city.resources)
        assertEquals(0L, state.city.constructionProgress)
        assertEquals(0L, state.day.growthMillis)
        assertNull(state.qualification)
        assertTrue(state.city.plots.all { it.resourceUnits == 0L && it.fire == 0 })
        state = tick(state, 600_000).state
        assertNull(state.qualification)
        state = tick(state, 1).state
        assertNotNull(state.qualification)
        assertEquals(Era.PRIMITIVE, open(state, today).era)
        assertEquals(Era.AGRICULTURAL, open(state, today.plusDays(1)).era)
    }

    @Test fun `paused old battle resumes after multiple dates and counts completion day handled`() {
        var state = fighting()
        repeat(9) { state = hit(state, it.toLong()).state }
        state = tick(state, 13_000).state
        val invasion = state.invasion
        state = open(state, today.plusDays(3))
        assertEquals(invasion, state.invasion)
        state = tick(state, 800_000, growth = false, battle = false).state
        assertEquals(invasion, state.invasion)
        state = hit(state, 9).state
        assertEquals(today.plusDays(3), state.lastInvasionHandledDate)
        assertNull(tick(state, 500_000).state.invasion)
        val next = tick(state, 300_000, battle = false, date = today.plusDays(4)).state
        assertEquals(InvasionPhase.WARNING, next.invasion?.phase)
    }

    @Test fun `fire water resources and autonomous building are persistent nonlethal state`() {
        val initial = fresh()
        val fire = engine.dispatch(initial, WorldCommand.Intervene(InterventionKind.FIRE, "plot-0", 0)).state
        assertEquals(35, fire.city.plots.first().fire)
        assertTrue(fire.city.residents.all { it.action in listOf(ResidentAction.EVADE, ResidentAction.RESCUE) })
        val water = engine.dispatch(fire, WorldCommand.Intervene(InterventionKind.WATER, "plot-0", 1)).state
        assertEquals(0, water.city.plots.first().fire)
        assertEquals(25, water.city.plots.first().moisture)
        val resources = engine.dispatch(water,
            WorldCommand.Intervene(InterventionKind.GRANT_RESOURCE, "plot-0", 2)).state
        assertEquals(water.city.resources + 20, resources.city.resources)
        assertEquals(resources, engine.dispatch(resources,
            WorldCommand.Intervene(InterventionKind.GRANT_RESOURCE, "plot-0", 2)).state)
        val built = tick(resources, 10_000).state
        assertTrue(built.city.constructionProgress > resources.city.constructionProgress)
        assertEquals(initial.city.residents.map { it.id }, built.city.residents.map { it.id })
        assertEquals(6, built.city.residents.size)
    }

    @Test fun `simulation checkpoints are invariant to normal tick partition including fire ending`() {
        val start = engine.dispatch(fresh(), WorldCommand.Intervene(InterventionKind.FIRE, "plot-0", 0)).state
        val large = tick(start, 200_000).state
        var small = start
        repeat(40) { small = tick(small, 5_000).state }
        assertEquals(large.city, small.city)
        assertEquals(large.day, small.day)
    }
}
