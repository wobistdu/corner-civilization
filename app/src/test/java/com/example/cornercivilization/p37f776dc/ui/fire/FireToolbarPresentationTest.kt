package com.example.cornercivilization.p37f776dc.ui.fire

import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.ui.fire.components.FireToolbarPresentation
import org.junit.Assert.*
import org.junit.Test

class FireToolbarPresentationTest {
    private fun camp() = FireSliceState(phase=FirePhase.BURNING,strength=100,knowsFire=true,
        shelter=ShelterPhase.COMPLETE,sourceWood=0,spentWood=8,buildMillis=12_000)
    @Test fun allFourIntentButtonsExistOnce() {
        assertEquals(FireTool.entries.toSet(),FireToolbarPresentation.tools.map { it.first }.toSet())
        assertEquals(6,FireToolbarPresentation.tools.size)
        assertEquals(listOf("木头","敲鼓","撒种"),FireToolbarPresentation.tools.takeLast(3).map{it.second})
    }
    @Test fun firstToolHintsAreSingleLinesWithoutCountdowns() {
        val hints=FireTool.entries.map(FireToolbarPresentation::firstHint)
        assertEquals(6,hints.distinct().size)
        assertTrue(hints.all { it.isNotBlank() && '\n' !in it && !Regex("\\d").containsMatchIn(it) })
        for(tool in FireTool.entries.filter { it!=FireTool.OBSERVE })
            assertEquals(FireToolbarPresentation.firstHint(tool),FireToolbarPresentation.hint(camp(),tool,true))
        val full=camp().copy(pendingWood=12,storedWood=12,suppliedWood=24)
        assertEquals(FireToolbarPresentation.firstHint(FireTool.WOOD),FireToolbarPresentation.hint(full,FireTool.WOOD,true))
    }
    @Test fun carryingNeverClaimsDeliveredInventory() {
        val carrying=camp().copy(resourcePhase=ResourcePhase.CARRYING,pendingWood=0,haulingWood=4)
        val hint=FireToolbarPresentation.hint(carrying,FireTool.OBSERVE,true)
        assertTrue(hint.contains("到库才算交付"))
        assertFalse(hint.contains("已搬回"))
    }
    @Test fun observeShowsPersistentResultsAndNoSaveMutation() {
        val initial=camp().copy(pendingWood=0,storedWood=4,deliveries=1)
        assertFalse(Regex("\\d").containsMatchIn(FireToolbarPresentation.hint(initial,FireTool.OBSERVE,true)))
        assertTrue(FireToolbarPresentation.hint(initial.copy(fieldEstablished=true),FireTool.OBSERVE,true).contains("第一块田"))
        assertEquals(0L,initial.evolutionMillis)
        assertEquals("营地正在准备",FireToolbarPresentation.hint(initial,FireTool.WOOD,false))
    }
}
