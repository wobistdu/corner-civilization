package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimitiveResidentMotionTest {
    private fun route(phase: Float = 0f) = PrimitiveResidentRoute(
        stops = listOf(
            PrimitiveRouteStop(PrimitiveGroundPoint(0f, 0f), 2f, PrimitiveActivity.GATHER),
            PrimitiveRouteStop(PrimitiveGroundPoint(.1f, 0f), 1f, PrimitiveActivity.IDLE, PrimitiveActivity.CARRY),
        ),
        speedMetresPerSecond = .02f,
        phaseSeconds = phase,
    )

    @Test fun gatheringHoldsPositionWithoutRootBobbing() {
        val first = route().sample(.5f)
        val second = route().sample(1.5f)
        assertEquals(first, second)
        assertEquals(PrimitiveActivity.GATHER, first.activity)
        assertEquals(0f, first.metresPerSecond, .000001f)
    }

    @Test fun walkingUsesConstantSpeedAndHeadingFollowsVelocity() {
        val first = route().sample(3f)
        val second = route().sample(4f)
        assertEquals(.02f, second.x - first.x, .000001f)
        assertEquals(0f, second.z - first.z, .000001f)
        assertEquals(90f, first.yawDegrees, .0001f)
        assertEquals(PrimitiveActivity.WALK, first.activity)
        assertEquals(.02f, first.metresPerSecond, .000001f)
    }

    @Test fun carryingReturnsTowardCampWithOppositeHeading() {
        val pose = route().sample(9f)
        assertEquals(.08f, pose.x, .000001f)
        assertEquals(-90f, pose.yawDegrees, .0001f)
        assertEquals(PrimitiveActivity.CARRY, pose.activity)
    }

    @Test fun closedRouteLoopsWithoutTeleportAtCycleBoundary() {
        val path = route()
        val before = path.sample(path.durationSeconds - .001f)
        val after = path.sample(path.durationSeconds + .001f)
        assertTrue(PrimitiveGroundPoint(before.x, before.z).distanceTo(PrimitiveGroundPoint(after.x, after.z)) < .0001f)
        assertEquals(path.sample(.5f), path.sample(path.durationSeconds + .5f))
    }

    @Test fun headingTurnsAcrossWrapByShortestAngle() {
        val heading = PrimitiveResidentHeading(175f)
        val yaw = heading.advance(-175f, .01f)
        val positiveProgress = (yaw - 175f + 360f) % 360f
        assertTrue(positiveProgress > 0f && positiveProgress < 10f)
        assertEquals(-175f, heading.advance(-175f, .35f), .001f)
    }

    @Test fun instancePhaseOffsetsProduceDifferentInitialActions() {
        assertEquals(PrimitiveActivity.GATHER, route().sample(0f).activity)
        assertEquals(PrimitiveActivity.WALK, route(3f).sample(0f).activity)
    }
}
