package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PrimitiveMemoryPresentationTest {
    private fun complete()=FireSliceState(phase=FirePhase.BURNING,strength=100,knowsFire=true,
        shelter=ShelterPhase.COMPLETE,sourceWood=0,spentWood=8,buildMillis=12_000)
    @Test fun pendingAndStoredPilesFollowOwnershipNotFakeDelivery() {
        val initial=complete()
        assertEquals(1,PrimitiveMemoryPresentation.from(initial).pendingPiles)
        val carried=initial.copy(resourcePhase=ResourcePhase.CARRYING,pendingWood=0,haulingWood=4)
        assertEquals(0,PrimitiveMemoryPresentation.from(carried).pendingPiles)
        assertEquals(0,PrimitiveMemoryPresentation.from(carried).storedPiles)
        val delivered=initial.copy(pendingWood=0,storedWood=4,deliveries=1)
        assertEquals(1,PrimitiveMemoryPresentation.from(delivered).storedPiles)
        val full=initial.copy(pendingWood=0,storedWood=24,suppliedWood=24)
        assertEquals(6,PrimitiveMemoryPresentation.from(full).storedPiles)
    }
    @Test fun fieldVisibilityFollowsCompletedWorkRegardlessOfTime() {
        val old=complete().copy(activeMillis=9_000_000)
        assertFalse(PrimitiveMemoryPresentation.from(old).fieldVisible)
        val half=old.copy(evolutionMillis=300_000)
        assertFalse(PrimitiveMemoryPresentation.from(half).fieldVisible)
        val farm=old.copy(evolutionMillis=600_000)
        assertFalse(PrimitiveMemoryPresentation.from(farm).fieldVisible)
        assertTrue(PrimitiveMemoryPresentation.from(farm.copy(fieldEstablished=true)).fieldVisible)
        assertTrue(PrimitiveMemoryPresentation.from(old.copy(fieldEstablished=true)).fieldVisible)
    }
    @Test fun initialShelterMaterialsAreNotDuplicatedAsPendingPiles() {
        val initial=PrimitiveMemoryPresentation.from(FireSliceState())
        assertEquals(0,initial.pendingPiles)
        assertEquals(0,initial.storedPiles)
    }
}
