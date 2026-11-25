package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PrimitiveResourceWalkTest {
    private fun complete()=FireSliceState(phase=FirePhase.BURNING,strength=100,knowsFire=true,
        shelter=ShelterPhase.COMPLETE,sourceWood=0,spentWood=8,buildMillis=12_000)
    private val ordinary=PrimitiveResidentPose(-.18f,.1861f,0f,PrimitiveActivity.IDLE,0f)
    @Test fun arrivalRequiresTravelAndDwellWithoutTeleport() {
        val walk=PrimitiveFireWalk(0, resourceMotionEnabled = true)
        val fetch=complete().copy(resourcePhase=ResourcePhase.FETCHING)
        var prior=ordinary
        repeat(1500) {
            val p=walk.sample(ordinary,fetch,.02f)
            assertTrue(PrimitiveGroundPoint(prior.x,prior.z).distanceTo(PrimitiveGroundPoint(p.x,p.z)) <= .000151f)
            prior=p
        }
        assertTrue(walk.resourceReady)
        assertEquals(-.088f,prior.x,.0001f)
        val carry=fetch.copy(resourcePhase=ResourcePhase.CARRYING,pendingWood=0,haulingWood=4)
        val p=walk.sample(ordinary,carry,.02f)
        assertFalse(walk.resourceReady)
        assertEquals(PrimitiveActivity.CARRY_WOOD,p.activity)
        repeat(800) { walk.sample(ordinary,carry,.02f) }
        assertTrue(walk.resourceReady)
    }
    @Test fun bothLanesHaveSeparateSupportsAndRecoverAfterRain() {
        val a=PrimitiveFireWalk(0, resourceMotionEnabled = true); val b=PrimitiveFireWalk(1, resourceMotionEnabled = true)
        val fetch=complete().copy(resourcePhase=ResourcePhase.FETCHING)
        var pa=ordinary; var pb=ordinary
        repeat(1800) { pa=a.sample(ordinary,fetch,.02f); pb=b.sample(ordinary,fetch,.02f) }
        assertTrue(kotlin.math.abs(pa.z-pb.z) >= .008f)
        val wet=fetch.copy(phase=FirePhase.WET,strength=0)
        a.sample(ordinary,wet,.02f)
        assertFalse(a.resourceReady)
        repeat(1800) { a.sample(ordinary,fetch,.02f) }
        assertTrue(a.resourceReady)
    }
    @Test fun completedBuildersKeepLivingWithoutDeliveringInvisibleWood() {
        for (phase in listOf(ResourcePhase.WAITING, ResourcePhase.IDLE)) {
            val walk=PrimitiveFireWalk(0, resourceMotionEnabled = true)
            val state=complete().copy(resourcePhase=phase)
            var prior=ordinary
            val poses=mutableListOf<PrimitiveResidentPose>()
            repeat(3000) {
                val p=walk.sample(ordinary,state,.02f)
                assertTrue(PrimitiveGroundPoint(prior.x,prior.z).distanceTo(PrimitiveGroundPoint(p.x,p.z)) <= .000151f)
                assertFalse(walk.resourceReady)
                assertFalse(walk.workReady)
                assertFalse(walk.ready)
                poses += p; prior=p
            }
            assertTrue(poses.any { it.activity == PrimitiveActivity.WALK })
            assertTrue(poses.any { it.activity == PrimitiveActivity.TEND_FIRE })
            assertTrue(poses.any { it.activity == PrimitiveActivity.IDLE })
            assertTrue(poses.maxOf { it.x } - poses.minOf { it.x } >= .049f)
            assertTrue(poses.all { it.activity !in listOf(PrimitiveActivity.GATHER,PrimitiveActivity.CARRY_WOOD) })
        }
    }
    @Test fun idleCampLanesRemainSeparatedAndPauseDoesNotAdvance() {
        val a=PrimitiveFireWalk(0,true);val b=PrimitiveFireWalk(1,true)
        val state=complete()
        val other=ordinary.copy(z=.2239f)
        var pa=ordinary;var pb=other
        repeat(3000) {
            pa=a.sample(ordinary,state,.02f);pb=b.sample(other,state,.02f)
            assertTrue(kotlin.math.abs(pa.z-pb.z) >= .037f)
        }
        val paused=a.sample(ordinary,state,0f)
        assertEquals(pa.x,paused.x,.000001f);assertEquals(pa.z,paused.z,.000001f)
        val wet=state.copy(phase=FirePhase.WET,strength=0)
        val reaction=a.sample(ordinary,wet,.02f)
        assertEquals(PrimitiveActivity.CATCH_RAIN,reaction.activity)
        assertFalse(a.resourceReady)
        val resumed=a.sample(ordinary,state,.02f)
        assertTrue(PrimitiveGroundPoint(reaction.x,reaction.z).distanceTo(PrimitiveGroundPoint(resumed.x,resumed.z)) <= .000151f)
    }
}
