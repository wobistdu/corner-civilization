package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class TransportMotionTest {
    @Test fun taxisStayInLaneAndFaceTheirMotionRatherThanSlidingSideways() {
        for (index in 0..7) for (sample in 0..1000) {
            val seconds = sample * .05f
            val pose = airTrafficMotion(seconds, index)
            assertTrue(abs(pose.x) <= .020f)
            assertTrue(abs(pose.y) <= .0035f)
            assertTrue(abs(pose.z) <= .012f)
            val next = airTrafficMotion(seconds + .001f, index)
            val angle = pose.yaw * PI.toFloat() / 180f
            val forwardDot = sin(angle) * (next.x - pose.x) + cos(angle) * (next.z - pose.z)
            assertTrue(forwardDot > 0f)
        }
    }
    @Test fun groundShuttlesKeepAuthoredSupportHeightAndReverseAtLaneEnds() {
        for (phase in listOf(0f, 1f, 2f, 3f, 4f, 5f, 6f)) {
            val pose = groundTransportMotion(.075f, phase)
            assertTrue(abs(pose.x) <= .075f)
            assertEquals(0f, pose.y, 0f)
            assertEquals(0f, pose.z, 0f)
            assertEquals(if (cos(phase) >= 0) 0f else 180f, pose.yaw, 0f)
            assertEquals(pose, groundTransportMotion(.075f, phase))
        }
    }
}
