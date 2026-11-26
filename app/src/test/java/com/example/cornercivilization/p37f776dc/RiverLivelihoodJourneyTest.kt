package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Assert.*
import org.junit.Test

class RiverLivelihoodJourneyTest {
    private val world = LocalInterventionWorld(LocalNavigation({ _, _ -> .034f }))
    private val village = LivingVillage(world).also { world.village = it; it.life.enable() }
    private val life = village.life
    private val id = "PrimitiveResident_1"
    private val bank = village.ecology.bank

    private fun until(label: String, condition: () -> Boolean) {
        repeat(2000) {
            if (condition()) return
            world.advance(.1f)
            val current = village.residents.getValue(id)
            world.respond(id, current, .1f, true)?.let { village.residents[id] = it.point }
        }
        fail("$label: job=${village.jobs[id]} state=${life.river.state} news=${world.chronicle.snapshot().takeLast(5)}")
    }
    @Test fun rainfallClayDeliveryTimberAndDrinkingWater() {
        village.residents[id] = world.depot
        village.ecology.food = 5
        life.water = 1f
        assertTrue(world.cast(Kind.RAIN,bank,.034f))
        until("worker carried wet clay home") { life.river.state.blank }
        assertFalse(life.river.state.cistern)
        assertTrue(world.dropWood(world.depot))
        until("a hauled bundle fired the pot") { life.river.state.cistern }
        assertTrue(world.woods.isEmpty())
        val old = life.water
        assertTrue(world.cast(Kind.RAIN,world.depot,.034f))
        repeat(60) { world.advance(.1f) }
        assertTrue(life.water>old+.25f)
    }
    @Test fun wetTrapFillsSoonerAndFinalCatchIsAHauledMeal() {
        val almostWorn = RiverLivelihood.State(trapUses=1)
        life.river.restore(almostWorn)
        life.river.advance(10f)
        val dry = life.river.state.catchProgress
        life.river.restore(almostWorn)
        world.wetSpots += WetSpot(bank,1f)
        life.river.advance(10f)
        assertTrue(life.river.state.catchProgress > dry*2f)
        village.residents[id] = bank
        life.water = 1f
        until("last catch lifted") { life.river.state.trapUses==0 }
        assertEquals("riverFish", village.foodBundles.single().origin)
        assertEquals(0,village.ecology.food)
        until("catch delivered") { village.ecology.food==1 }
        assertTrue(village.foodBundles.isEmpty())
        assertEquals(1,life.foodProduced)
    }
    @Test fun abandonedClayCannotTurnIntoWaterOrAnUnattendedPot() {
        village.residents[id] = bank
        life.river.restore(RiverLivelihood.State(softBank=90f))
        life.water=1f
        until("worker picked up the blank") { village.jobs[id]?.let { it.event==VillageEvent.MOULD_CLAY && it.carrying }==true }
        val at=village.residents.getValue(id)
        village.interrupt(id,at)
        assertFalse(life.river.state.blank)
        assertFalse(life.river.state.cistern)
        assertNull(life.journal.counts[VillageEvent.SPILL_WATER])
        assertTrue(world.wetSpots.isEmpty())
    }
}
