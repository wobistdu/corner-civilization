package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class SceneEffectsTest {
    @Test fun noSelectionOrHistoryDoesNotInventFiresOrOtherEffects() {
        InterventionKind.entries.forEach { assertFalse(plotEffect(PlotState("plot-0"), it, null).visible) }
    }
    @Test fun selectionShowsOnlyCorrespondingGhostAtEveryValidPlot() {
        InterventionKind.entries.forEach { selected ->
            InterventionKind.entries.forEach { kind ->
                val effect = plotEffect(PlotState("plot-0"), kind, selected)
                assertEquals(kind == selected, effect.visible)
                assertEquals(kind == selected, effect.ghost)
            }
        }
    }
    @Test fun cancellingSelectionRetainsSnapshotConsequencesAndWaterCanRemoveFire() {
        val plot = PlotState("plot-0", moisture = 25, fire = 35, resourceUnits = 20)
        InterventionKind.entries.forEach {
            val effect = plotEffect(plot, it, null)
            assertTrue(effect.visible)
            assertFalse(effect.ghost)
            assertTrue(effect.scale > 1f)
        }
        assertFalse(plotEffect(plot.copy(fire = 0), InterventionKind.FIRE, null).visible)
        assertTrue(plotEffect(plot.copy(fire = 0), InterventionKind.WATER, null).visible)
    }
    @Test fun resourceSurplusNeverExceedsMeasuredMaximumOwnerScale() {
        val effect = plotEffect(PlotState("plot-0", resourceUnits = Long.MAX_VALUE), InterventionKind.GRANT_RESOURCE, null)
        assertEquals(1.5f, effect.scale, 0f)
    }
}
