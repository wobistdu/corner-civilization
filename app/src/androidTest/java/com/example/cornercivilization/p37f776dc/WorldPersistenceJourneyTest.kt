package com.example.cornercivilization.p37f776dc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.data.repository.*
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldEngine
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Exercises the production codec and atomic replacements on Android storage.
 * Dates and active durations are injected; device settings and player saves stay untouched.
 */
@RunWith(AndroidJUnit4::class)
class WorldPersistenceJourneyTest {
    @Test fun fiveDatesThenNineHitBattleReloadAndTenthHitCommit() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "acceptance-${UUID.randomUUID()}")
        assertTrue(directory.mkdirs())
        try {
            val engine = WorldEngine()
            val firstDate = LocalDate.of(2026, 11, 10)
            var state = WorldFactory.create(firstDate, seed = 47, lineageId = "android-acceptance")
            fun saveAndReopen() {
                assertTrue(AtomicFileWorldRepository(directory)
                    .save(WorldSnapshot(state, "Asia/Shanghai")) is SaveResult.Saved)
                // A new repository has no in-memory knowledge of the previous instance.
                val loaded = AtomicFileWorldRepository(directory).load()
                assertTrue(loaded is LoadResult.Loaded)
                assertEquals(state, (loaded as LoadResult.Loaded).snapshot.state)
                state = loaded.snapshot.state
            }
            fun dispatch(command: WorldCommand) {
                state = engine.dispatch(state, command).state
                saveAndReopen()
            }
            saveAndReopen()
            val expected = listOf(Era.PRIMITIVE, Era.AGRICULTURAL, Era.INDUSTRIAL, Era.MODERN, Era.SPACE)
            expected.forEachIndexed { index, era ->
                val date = firstDate.plusDays(index.toLong())
                dispatch(WorldCommand.Open(date, "android-day-$index"))
                assertEquals(era, state.era)
                if (era != Era.SPACE) {
                    dispatch(WorldCommand.Tick(TimeSlice(date, 601_000, true, false)))
                    assertEquals(era, state.era)
                    assertNotNull(state.qualification)
                }
            }
            dispatch(WorldCommand.Tick(TimeSlice(state.day.date, 300_000, true, false)))
            assertEquals(InvasionPhase.WARNING, state.invasion?.phase)
            dispatch(WorldCommand.Tick(TimeSlice(state.day.date, 5_000, true, true)))
            assertEquals(InvasionPhase.FIGHTING, state.invasion?.phase)
            repeat(9) { sequence ->
                val invasion = requireNotNull(state.invasion)
                dispatch(WorldCommand.TargetHit(invasion.eventId, invasion.targetId,
                    sequence.toLong(), true, true))
            }
            dispatch(WorldCommand.Tick(TimeSlice(state.day.date, 13_000, true, true)))
            val savedBattle = requireNotNull(state.invasion)
            assertEquals(9, savedBattle.hits)
            assertEquals(7_000L, savedBattle.remainingMillis)
            // Opening days later must resume the existing event without offline damage.
            dispatch(WorldCommand.Open(firstDate.plusDays(8), "android-after-absence"))
            assertEquals(savedBattle, state.invasion)
            dispatch(WorldCommand.TargetHit(savedBattle.eventId, savedBattle.targetId, 9, true, true))
            assertNull(state.invasion)
            assertEquals(Era.SPACE, state.era)
            dispatch(WorldCommand.Tick(TimeSlice(state.day.date, 301_000, true, true)))
            assertNull("The resumed battle already handled this date", state.invasion)
        } finally {
            directory.deleteRecursively()
        }
    }
}
