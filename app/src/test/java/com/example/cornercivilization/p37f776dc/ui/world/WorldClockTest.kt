package com.example.cornercivilization.p37f776dc.ui.world

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorldClockTest {
    @Test fun midnightIsSplitWithoutLosingMonotonicMilliseconds() {
        val clock = WorldClock { ZoneId.of("UTC") }
        val start = Instant.parse("2026-11-10T23:59:59.500Z")
        clock.rebase(100, start)
        val slices = clock.settle(2100, start.plusMillis(2000), true, false)
        assertEquals(listOf(500L, 1500L), slices.map { it.elapsedMillis })
        assertEquals(listOf(LocalDate.of(2026,11,10), LocalDate.of(2026,11,11)), slices.map { it.date })
        assertTrue(slices.all { it.growthActive && !it.battleActive })
    }
    @Test fun wallClockJumpNeverAddsOfflineTimeAndResumeRebases() {
        val clock = WorldClock { ZoneId.of("UTC") }
        val start = Instant.parse("2026-11-10T12:00:00Z")
        clock.rebase(0, start)
        assertEquals(1000L, clock.settle(1000, start.plusSeconds(86400), true, true).sumOf { it.elapsedMillis })
        clock.rebase(500000, start.plusSeconds(172800))
        assertEquals(1L, clock.settle(500001, start.plusSeconds(172800), true, true).sumOf { it.elapsedMillis })
    }
}
