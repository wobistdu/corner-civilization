package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.*
import org.junit.Test

class PrimitiveFarmWalkTest {
    private val ordinary = PrimitiveResidentPose(-.01f,.062f,0f,PrimitiveActivity.IDLE,0f)
    private fun walk() = PrimitiveFarmWalk(PrimitiveGroundPoint(-.061f,.298f),PrimitiveGroundPoint(-.049f,.298f))
    @Test fun beforeAgricultureDoesNotChangeOrdinaryLife() {
        assertEquals(ordinary,walk().sample(ordinary,false,false,.02f))
    }
    @Test fun fieldArrivalIsContinuousAndActuallyWorksBesideTheField() {
        val walk=walk(); var prior=ordinary; var gathered=false; var reachedBoth=false
        val places=mutableSetOf<Int>()
        repeat(20000) {
            val pose=walk.sample(ordinary,true,false,.02f)
            assertTrue(PrimitiveGroundPoint(prior.x,prior.z).distanceTo(PrimitiveGroundPoint(pose.x,pose.z))<=.000151f)
            if (pose.activity==PrimitiveActivity.GATHER) {
                assertEquals(.298f,pose.z,.000001f)
                gathered=true; places += (pose.x*1000).toInt()
            }
            prior=pose
        }
        reachedBoth=places.size>=2
        assertTrue(gathered);assertTrue(reachedBoth)
    }
    @Test fun rainAndPauseCannotTeleportOrAdvanceRoutine() {
        val walk=walk();var prior=ordinary
        repeat(6000) { prior=walk.sample(ordinary,true,false,.02f) }
        val pause=walk.sample(ordinary,true,false,0f)
        assertEquals(prior.x,pause.x,0f);assertEquals(prior.z,pause.z,0f)
        repeat(200) {
            val rain=walk.sample(ordinary,true,true,.02f)
            assertEquals(prior.x,rain.x,0f);assertEquals(prior.z,rain.z,0f)
            assertEquals(PrimitiveActivity.IDLE,rain.activity)
        }
        val resumed=walk.sample(ordinary,true,false,.02f)
        assertTrue(PrimitiveGroundPoint(prior.x,prior.z).distanceTo(PrimitiveGroundPoint(resumed.x,resumed.z))<=.000151f)
    }
}
