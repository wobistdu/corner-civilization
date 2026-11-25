package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SceneFeedbackTest {
    private val date = LocalDate.of(2026, 11, 10)
    private fun world() = WorldFactory.create(date)

    @Test fun snapshotRestoreDoesNotReplayTransientEffects() {
        val feedback = SceneFeedback()
        feedback.accept(null, world().copy(era = Era.SPACE))
        assertNull(feedback.eraChange)
        assertNull(feedback.victorySeconds)
    }

    @Test fun civilizationChangeUsesSafeResetAndRetainsOldEraUntilPresentationEnds() {
        val feedback = SceneFeedback()
        val previous = world().copy(era = Era.SPACE)
        val next = previous.copy(civilizationId = "new-city", era = Era.PRIMITIVE)
        feedback.accept(previous, next)
        assertEquals(SceneFeedback.Kind.SAFE_RESET, feedback.eraChange!!.kind)
        assertEquals(Era.SPACE, feedback.eraChange!!.from)
        assertEquals(Era.PRIMITIVE, feedback.eraChange!!.to)
        feedback.advance(.4f)
        assertNotNull(feedback.eraChange)
        feedback.advance(2f)
        assertNull(feedback.eraChange)
        assertEquals(Era.PRIMITIVE, next.era)
    }

    @Test fun eraChangeUsesEvolutionAndNeverChangesTheDomainSnapshot() {
        val feedback = SceneFeedback()
        val previous = world()
        val next = previous.copy(era = Era.AGRICULTURAL)
        feedback.accept(previous, next)
        assertEquals(SceneFeedback.Kind.EVOLVE, feedback.eraChange!!.kind)
        feedback.advance(2f)
        assertNull(feedback.eraChange)
        assertEquals(Era.AGRICULTURAL, next.era)
        assertEquals(previous.day.growthMillis, next.day.growthMillis)
    }

    @Test fun resolvedInvasionCelebratesOnceAndCivilizationResetDoesNotCelebrate() {
        val previous = world().copy(era = Era.SPACE).let {
            it.copy(invasion = InvasionState("event", it.civilizationId, date, "ship", InvasionPhase.FIGHTING, 10000, hits = 9))
        }
        val resolved = previous.copy(invasion = null)
        val feedback = SceneFeedback()
        feedback.accept(previous, resolved)
        assertEquals(0f, feedback.victorySeconds!!, 0f)
        feedback.advance(1f)
        feedback.accept(resolved, resolved.copy(revision = resolved.revision + 1))
        assertNull(feedback.victorySeconds)
        feedback.accept(previous, resolved.copy(civilizationId = "new-city", era = Era.PRIMITIVE))
        assertNull(feedback.victorySeconds)
        assertEquals(SceneFeedback.Kind.SAFE_RESET, feedback.eraChange!!.kind)
    }
    @Test fun coldLoadExplicitEvolutionWaitsForActiveMotionAndAcknowledgesOnce() {
        val feedback = SceneFeedback()
        val next = world().copy(era = Era.AGRICULTURAL)
        val event = SceneEvolution("committed-42", Era.PRIMITIVE, Era.AGRICULTURAL)
        feedback.accept(null, next, event)
        assertEquals(Era.PRIMITIVE, feedback.eraChange!!.from)
        assertNull(feedback.advance(60f, motionActive = false))
        assertEquals(0f, feedback.eraChange!!.seconds, 0f)
        assertNull(feedback.advance(.6f))
        feedback.accept(next, next.copy(revision = next.revision + 1), event)
        assertEquals(.6f, feedback.eraChange!!.seconds, 0f)
        assertEquals(event.id, feedback.advance(.4f))
        assertNull(feedback.eraChange)
        feedback.accept(next, next, event)
        assertNull(feedback.eraChange)
        assertNull(feedback.advance(2f))
    }

    @Test fun explicitEventAdoptsSameProcessEvolutionWithoutRestartingIt() {
        val feedback = SceneFeedback()
        val previous = world()
        val next = previous.copy(era = Era.AGRICULTURAL)
        feedback.accept(previous, next)
        feedback.advance(.7f)
        val event = SceneEvolution("live-42", previous.era, next.era)
        feedback.accept(next, next, event)
        assertEquals(.7f, feedback.eraChange!!.seconds, 0f)
        assertEquals(event.id, feedback.advance(.31f))
        assertNull(feedback.advance(1f))
    }

    @Test fun delayedExplicitEventAcknowledgesAlreadyCompletedAnimationWithoutReplay() {
        val feedback = SceneFeedback()
        val previous = world()
        val next = previous.copy(era = Era.AGRICULTURAL)
        feedback.accept(previous, next)
        feedback.advance(1.1f)
        val event = SceneEvolution("late-42", previous.era, next.era)
        feedback.accept(next, next, event)
        assertNull(feedback.eraChange)
        assertNull(feedback.advance(0f, motionActive = false))
        assertEquals(event.id, feedback.advance(0f))
        assertNull(feedback.advance(0f))
    }
}
