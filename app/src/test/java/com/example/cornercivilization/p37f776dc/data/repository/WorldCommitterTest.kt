package com.example.cornercivilization.p37f776dc.data.repository

import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldEngine
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WorldCommitterTest {
    private val today = LocalDate.of(2026, 11, 10)
    private val engine = WorldEngine()
    private fun fresh() = WorldFactory.create(today)
    private fun tick(state: WorldState, elapsed: Long) = engine.dispatch(state,
        WorldCommand.Tick(TimeSlice(state.day.date, elapsed, true, true)))
    private val failingRepository = object : WorldRepository {
        override fun load(): LoadResult = LoadResult.Empty
        override fun save(snapshot: WorldSnapshot): SaveResult = SaveResult.Failed("DISK_FULL")
    }
    private val committer = WorldCommitter(failingRepository, "UTC")

    @Test fun `failed qualification and upgrade commit publish no partial state or success events`() {
        val beforeQualification = tick(fresh(), 600_000).state
        assertFailedUnchanged(beforeQualification, tick(beforeQualification, 1))
        val qualified = tick(beforeQualification, 1).state
        val upgrade = engine.dispatch(qualified, WorldCommand.Open(today.plusDays(1)))
        assertEquals(Era.AGRICULTURAL, upgrade.state.era)
        assertFailedUnchanged(qualified, upgrade)
        assertEquals(Era.PRIMITIVE, qualified.era)
        assertNotNull(qualified.qualification)
    }

    @Test fun `failed success commit keeps nine-hit battle and failed reset keeps complete old city`() {
        var battle = tick(fresh().copy(era = Era.SPACE), 305_000).state
        repeat(9) {
            val invasion = requireNotNull(battle.invasion)
            battle = engine.dispatch(battle, WorldCommand.TargetHit(invasion.eventId, invasion.targetId,
                it.toLong(), true, true)).state
        }
        val invasion = requireNotNull(battle.invasion)
        val success = engine.dispatch(battle, WorldCommand.TargetHit(invasion.eventId, invasion.targetId,
            9, true, true))
        assertNull(success.state.invasion)
        assertFailedUnchanged(battle, success)
        assertFailedUnchanged(battle, tick(battle, 20_000))
        assertEquals(9, battle.invasion?.hits)
        assertEquals(Era.SPACE, battle.era)
    }

    @Test fun `failed intervention does not grant resources`() {
        val before = fresh()
        val grant = engine.dispatch(before, WorldCommand.Intervene(InterventionKind.GRANT_RESOURCE, "plot-0", 0))
        assertFailedUnchanged(before, grant)
        assertEquals(10L, before.city.resources)
    }

    @Test fun `repository exception and wrong saved revision fail closed`() {
        val before = fresh()
        val transition = tick(before, 600_001)
        val repositories = listOf(
            object : WorldRepository {
                override fun load(): LoadResult = LoadResult.Empty
                override fun save(snapshot: WorldSnapshot): SaveResult = throw IllegalStateException("injected")
            },
            object : WorldRepository {
                override fun load(): LoadResult = LoadResult.Empty
                override fun save(snapshot: WorldSnapshot): SaveResult = SaveResult.Saved(-1)
            },
        )
        repositories.forEach {
            val result = WorldCommitter(it, "UTC").commit(before, transition)
            assertTrue(result is CommitResult.Failed)
            assertEquals(before, (result as CommitResult.Failed).previousState)
        }
    }

    @Test fun `successful critical commit exposes exactly saved complete transition`() {
        var durable: WorldSnapshot? = null
        val repository = object : WorldRepository {
            override fun load(): LoadResult = durable?.let { LoadResult.Loaded(it) } ?: LoadResult.Empty
            override fun save(snapshot: WorldSnapshot): SaveResult {
                durable = snapshot
                return SaveResult.Saved(snapshot.state.revision)
            }
        }
        val previous = tick(fresh(), 600_001).state
        val next = engine.dispatch(previous, WorldCommand.Open(today.plusDays(1)))
        val result = WorldCommitter(repository, "UTC").commit(previous, next) as CommitResult.Applied
        assertEquals(next, result.transition)
        assertEquals(result.transition.state, durable?.state)
        assertNull(durable?.state?.qualification)
        assertEquals(Era.AGRICULTURAL, durable?.state?.era)
    }

    private fun assertFailedUnchanged(previous: WorldState, transition: Transition) {
        assertEquals(Persistence.CRITICAL, transition.persistence)
        val result = committer.commit(previous, transition)
        assertTrue(result is CommitResult.Failed)
        assertEquals(previous, (result as CommitResult.Failed).previousState)
        // Failed carries no transition/events that UI could accidentally publish.
        assertEquals("DISK_FULL", result.reason)
    }
}
