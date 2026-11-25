package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class VillageJournalTest {
    @Test fun journalDebouncesSubjectsAndBoundsRecentHistory() {
        val j=VillageJournal();val e=VillageEvent.FILL_JAR
        j.record(e,"first",1f,"water");j.record(e,"first",2f,"water")
        j.record(e,"second",2f,"water")
        assertEquals(2,j.counts[e]);assertEquals(2,j.recent.size)
        repeat(110){j.record(VillageEvent.EAT_STOCK,"resident",10f+it*4f,"meal")}
        assertEquals(110,j.counts[VillageEvent.EAT_STOCK]);assertEquals(96,j.recent.size)
        assertEquals(66f,j.recent.first().at,0f);assertEquals(446f,j.recent.last().at,0f)
    }
    @Test fun restoredJournalDoesNotRepeatRecentConsequences() {
        val j=VillageJournal()
        j.restore(mapOf(VillageEvent.DRINK_JAR to 7),
            listOf(VillageJournal.Entry(VillageEvent.DRINK_JAR,"resident",50f,"water")))
        j.record(VillageEvent.DRINK_JAR,"resident",52f,"water")
        assertEquals(7,j.counts[VillageEvent.DRINK_JAR]);assertEquals(1,j.recent.size)
        j.record(VillageEvent.DRINK_JAR,"resident",53f,"water")
        assertEquals(8,j.counts[VillageEvent.DRINK_JAR]);assertEquals(2,j.recent.size)
    }
}
