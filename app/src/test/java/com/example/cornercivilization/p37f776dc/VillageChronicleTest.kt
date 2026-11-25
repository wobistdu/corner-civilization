package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import org.junit.Assert.*
import org.junit.Test

class VillageChronicleTest {
    @Test fun allRulesHaveReadableCopyAndTheBookIsBounded() {
        val book = VillageChronicle()
        for (rule in VillageEvent.entries) {
            book.villageEvent(rule, "PrimitiveResident_1", 7f)
            val line = book.snapshot().last()
            assertTrue(line.text.startsWith("石生"))
            assertFalse(line.text.contains(rule.name))
            assertFalse(line.player)
        }
        repeat(96) { book.append(it.toFloat(), "第${it}件", true) }
        assertEquals(96,book.snapshot().size)
        assertEquals((0..95).toList(),book.snapshot().map { it.time })
        assertTrue(book.snapshot().all { it.player })
    }

    @Test fun fiveDistinctNewsMustInterveneEvenAcrossPagesAndPlayerActions() {
        val book = VillageChronicle()
        fun branch(who: String) = book.villageEvent(VillageEvent.COLLECT_BRANCH, who, 0f)
        branch("Resident_0")
        repeat(4) { book.append(0f, "村闻$it") }
        repeat(6) { book.append(0f, "你放下木材。", player = true) }
        val before = book.revision
        branch("Resident_1")
        assertEquals(before, book.revision)
        book.append(0f, "第五件")
        branch("Resident_1")
        assertTrue(book.snapshot().last().text.startsWith("石生"))
        assertEquals(6,book.snapshot().count { it.player })
    }

    @Test fun movingRainDoesNotFloodPagesAndEscapeIsPerAnimalPerCloud() {
        val world = LocalInterventionWorld(LocalNavigation({ _, _ -> .034f }))
        val p = LocalInterventionWorld.Point(.23f, -.12f)
        val id = requireNotNull(world.beginRain(p, .034f))
        repeat(60) {
            assertTrue(world.moveRain(id, p, .034f))
            world.reportEscape("tiger", "剑齿虎", p)
        }
        world.endRain(id)
        world.endRain(id)
        world.advance(15f)
        val lines = world.chronicle.snapshot()
        assertEquals(listOf(false,true,false), lines.map { it.player })
        assertTrue(lines[1].text.contains("右侧后方"))
        assertTrue(lines.last().text.contains("雨歇"))
    }

    @Test fun repeatedRuleStillCountsWhileScrollSuppressesIt() {
        val world = LocalInterventionWorld(LocalNavigation({ _, _ -> .034f }))
        val village = LivingVillage(world)
        val life = village.life
        repeat(2) { life.emit(VillageEvent.COLLECT_BRANCH, "PrimitiveResident_0", "test") }
        life.clock += 4
        life.emit(VillageEvent.COLLECT_BRANCH, "PrimitiveResident_0", "test")
        assertEquals(2, life.journal.counts[VillageEvent.COLLECT_BRANCH])
        assertEquals(listOf(0), world.chronicle.snapshot().map { it.time })
    }
}
