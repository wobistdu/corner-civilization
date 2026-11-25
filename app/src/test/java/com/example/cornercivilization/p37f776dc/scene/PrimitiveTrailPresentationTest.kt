package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PrimitiveTrailPresentationTest {
    @Test fun elapsedTimeOrWoodGiftsDoNotFabricateTravelWear() {
        val state=FireSliceState(activeMillis=99_000_000,evolutionMillis=300_000,pendingWood=12,suppliedWood=12)
        assertEquals(0,PrimitiveTrailPresentation.from(state).woodWear)
        assertFalse(PrimitiveTrailPresentation.from(state).fieldVisible)
    }
    @Test fun savedCompletedDeliveriesCreateOnlyOneBoundedStage() {
        for ((deliveries,wear) in listOf(0 to 0,1 to 1,2 to 1,3 to 2,5 to 2,6 to 3,Int.MAX_VALUE to 3)) {
            val state=FireSliceState(deliveries=deliveries)
            assertEquals(wear,PrimitiveTrailPresentation.from(state).woodWear)
            assertEquals(PrimitiveTrailPresentation.from(state),PrimitiveTrailPresentation.from(state.copy()))
        }
    }
    @Test fun fieldPathFollowsPersistentWorkResult() {
        assertFalse(PrimitiveTrailPresentation.from(FireSliceState(activeMillis=600_000)).fieldVisible)
        assertFalse(PrimitiveTrailPresentation.from(FireSliceState(evolutionMillis=599_999)).fieldVisible)
        assertFalse(PrimitiveTrailPresentation.from(FireSliceState(evolutionMillis=600_000)).fieldVisible)
        assertTrue(PrimitiveTrailPresentation.from(FireSliceState(fieldEstablished=true)).fieldVisible)
    }
}
