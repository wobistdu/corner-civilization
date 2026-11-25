package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.*
import org.junit.Test

class PrimitiveBakedShadeTest {
    @Test fun uprightLeavesBrighterThanUndersidesWithoutRealtimeLights() {
        assertTrue(PrimitiveBakedShade.factor(0f,1f,0f)>PrimitiveBakedShade.factor(0f,-1f,0f))
        assertEquals(.53f,PrimitiveBakedShade.factor(0f,-1f,0f),.00001f)
    }
    @Test fun shadeBoundedForAllFiniteNormalsAndMissingDirection() {
        for (x in -2..2) for(y in -2..2) for(z in -2..2) {
            assertTrue(PrimitiveBakedShade.factor(x.toFloat(),y.toFloat(),z.toFloat()) in .53f..1f)
        }
        assertEquals(.78f,PrimitiveBakedShade.factor(0f,0f,0f),0f)
    }
    @Test fun normalLengthDoesNotChangeDirectionShade() {
        assertEquals(PrimitiveBakedShade.factor(-1f,2f,1f),PrimitiveBakedShade.factor(-2f,4f,2f),.000001f)
    }
}
