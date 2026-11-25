package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.*
import org.junit.Test

class PrimitiveVegetationPatchTest {
    @Test fun islandOffsetsStayInsideAuthoredRadius() {
        for (i in 0..100) assertTrue(PrimitiveVegetationPatch.offset(i / 100f, .025f) in 0f.. .025f)
        assertEquals(.025f, PrimitiveVegetationPatch.offset(1f, .025f), .000001f)
    }
    @Test fun shortGrassAndBankLeavesHaveBoundedDifferentHeights() {
        for (i in 0..100) {
            assertTrue(PrimitiveVegetationPatch.bladeHeight(i / 100f, false) in .0022f.. .0045f)
            assertTrue(PrimitiveVegetationPatch.bladeHeight(i / 100f, true) in .0045f.. .0065001f)
        }
        assertTrue(PrimitiveVegetationPatch.bladeHeight(.5f, true) > PrimitiveVegetationPatch.bladeHeight(.5f, false))
    }
    @Test fun grassGeometryHasExplicitUpperBound() {
        assertEquals(115_200, PrimitiveVegetationPatch.MAX_GRASS_TRIANGLES)
        assertTrue(515_939 - 19_000 + PrimitiveVegetationPatch.MAX_GRASS_TRIANGLES < 800_000)
    }
}
