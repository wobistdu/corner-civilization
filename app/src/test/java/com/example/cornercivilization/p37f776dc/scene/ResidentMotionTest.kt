package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.Era
import com.example.cornercivilization.p37f776dc.domain.model.ResidentAction
import org.junit.Assert.*
import org.junit.Test

class ResidentMotionTest {
    @Test fun everyEraActionAndResidentStaysInsideTheMeasuredEnvelope() {
        for (era in Era.entries) for (action in ResidentAction.entries) for (index in 0..5) {
            for (sample in 0..6000) {
                val pose = residentMotion(era, action, sample * .02f, index)
                assertTrue("$era/$action/$index $pose", pose.offsetX in -.004f.. .004f)
                assertTrue(pose.offsetY in 0f.. .0043f)
                assertTrue(pose.offsetZ in -.008f.. .0045f)
                assertTrue(pose.yawDegrees.isFinite())
            }
        }
    }

    @Test fun stoppedExternalClockHoldsEveryEraPoseExactly() {
        for (era in Era.entries) for (action in ResidentAction.entries) {
            val held = residentMotion(era, action, 37.25f, 3)
            repeat(20) { assertEquals(held, residentMotion(era, action, 37.25f, 3)) }
        }
    }

    @Test fun erasDifferInTrajectoryAndActionsDifferInPurpose() {
        val signatures = Era.entries.map { era ->
            listOf(.1f, .7f, 1.6f, 3.2f).map { residentMotion(era, ResidentAction.GATHER, it, 1) }
        }
        assertEquals(Era.entries.size, signatures.toSet().size)
        for (era in Era.entries) {
            val actions = ResidentAction.entries.map { residentMotion(era, it, .73f, 2) }
            assertEquals(ResidentAction.entries.size, actions.toSet().size)
            assertTrue(residentMotion(era, ResidentAction.EVADE, .73f, 2).offsetZ < 0)
        }
    }

    @Test fun invalidTimeDoesNotProduceAnInvisibleOrInvalidResident() {
        for (time in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertEquals(residentMotion(Era.SPACE, ResidentAction.GATHER, 0f, 0), residentMotion(Era.SPACE, ResidentAction.GATHER, time, 0))
        }
    }
}
