package com.example.cornercivilization.p37f776dc.scene

import org.junit.Assert.*
import org.junit.Test

class PrimitiveCampWalkTest {
    private val source = PrimitiveGroundPoint(.07694531f,.08433333f)
    private val camp = PrimitiveGroundPoint(-.209f,.243f)
    private val hearth = PrimitiveGroundPoint(-.18f,.205f)
    @Test fun visitorsMoveContinuouslyToCampAndReturnToSource() {
        val walk=PrimitiveCampWalk(0,source,camp)
        var previous=source
        var visited=false
        var returned=false
        repeat(2600) {
            val pose=walk.sample(.1f,false,hearth)
            val p=PrimitiveGroundPoint(pose.x,pose.z)
            assertTrue(p.distanceTo(previous) <= .000751f)
            if(p.distanceTo(camp)<.0001f && pose.activity==PrimitiveActivity.IDLE) visited=true
            if(visited && p.distanceTo(source)<.0001f && pose.activity==PrimitiveActivity.GATHER) returned=true
            previous=p
        }
        assertTrue(visited);assertTrue(returned)
    }
    @Test fun rainOnlyPausesNearbyVisitorAndDoesNotTeleport() {
        val remote=PrimitiveCampWalk(0,source,camp)
        assertEquals(PrimitiveActivity.GATHER,remote.sample(.1f,true,hearth).activity)
        val near=PrimitiveCampWalk(0,source,camp)
        var pose=near.sample(0f,false,hearth)
        repeat(1000) { pose=near.sample(.1f,false,hearth) }
        assertTrue(PrimitiveGroundPoint(pose.x,pose.z).distanceTo(hearth)<.075f)
        val paused=near.sample(.2f,true,hearth)
        assertEquals(pose.x,paused.x,0f);assertEquals(pose.z,paused.z,0f)
        assertEquals(PrimitiveActivity.IDLE,paused.activity)
        assertEquals(paused,near.sample(0f,true,hearth))
    }
    @Test fun visitorsAreStaggeredAndZeroDeltaDoesNotMove() {
        val a=PrimitiveCampWalk(0,source,camp);val b=PrimitiveCampWalk(2,source,camp)
        var pa=a.sample(0f,false,hearth);var pb=b.sample(0f,false,hearth)
        repeat(220) { pa=a.sample(.1f,false,hearth);pb=b.sample(.1f,false,hearth) }
        assertNotEquals(pa,pb)
        val frozen=a.sample(0f,false,hearth)
        assertEquals(pa.x,frozen.x,0f);assertEquals(pa.z,frozen.z,0f)
    }
}
