package com.example.cornercivilization.p37f776dc
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
class PrimitiveFaunaMindTest {
    @Test fun contactCandidatesShareOneDynamicSearchAndReachOpenTargets() {
        for(blocked in listOf(false,true)) {
            val nav=LocalNavigation({_,_->.03f})
            val home=Point(-.1f,.1f)
            val target=Point(.1f,.1f)
            val mind=PrimitiveFaunaMind(nav,home,.02f,.03f,.04f,0)
            var queries=0
            mind.bindBody { queries++;!blocked||abs(it.x)>.02f }
            val order=WildlifeWorld.Order(target,"graze")
            mind.command(order,emptyList())
            assertTrue("contact candidates must share their terrain scan: $queries",
                queries<=nav.points.size+2500)
            repeat(120) {
                mind.advance(.1f,emptyList())
                mind.command(order,emptyList())
            }
            if(blocked) {
                assertTrue("unreachable food should leave the animal roaming on its own bank",mind.point.distance(home)>.006f)
                assertTrue(mind.point.x<-.02f)
            }
            else assertTrue("an open target must actually be reached",mind.point.distance(target)<.005f)
        }
    }
    @Test fun disconnectedBanksRejectBeforeScanningDynamicBodiesAcrossTheBank() {
        val nav=LocalNavigation({x,_->if(abs(x)<.035f)null else .03f})
        var queries=0
        assertNull(nav.path(Point(-.1f,.1f),Point(.1f,.1f)){queries++;true})
        assertTrue("an unreachable bank must not scan thousands of dynamic body positions: $queries",queries<=100)
        assertNotNull("connected routes remain available",nav.path(Point(-.1f,.1f),Point(-.2f,.1f)))
    }
    @Test fun unreachableFoodDoesNotSearchAgainEveryFrameAndStillRetries() {
        val nav=LocalNavigation({x,_->if(abs(x)<.035f)null else .03f})
        val home=Point(-.1f,.1f)
        val mind=PrimitiveFaunaMind(nav,home,.02f,.006f,.025f,0)
        var queries=0
        mind.bindBody { queries++;true }
        val order=WildlifeWorld.Order(Point(.1f,.1f),"graze")
        mind.command(order,emptyList())
        val first=queries
        assertTrue("the unreachable target was actually searched",first>0)
        repeat(10) {
            mind.command(order,emptyList())
            mind.advance(.01f,emptyList())
        }
        assertEquals("the same failed route must not be recalculated each frame",first,queries)
        assertEquals(home,mind.point)
        mind.advance(2.1f,emptyList())
        mind.command(order,emptyList())
        assertTrue("failed paths must remain retryable",queries>first)
    }
    @Test fun repeatedGrazingOrderCannotRestartEscapeAtTheSnappedStartEveryFrame() {
        val nav=LocalNavigation({_,_->.034f})
        val p=Point(.25746742f,-.03346748f)
        val mind=PrimitiveFaunaMind(nav,p,.0222f,.004f,.0174f,0)
        val rain=Hazard(1,Kind.RAIN,p,.034f,.06f,held=true)
        repeat(800) {
            mind.command(WildlifeWorld.Order(Point(.20f,.23f),"graze"),listOf(rain))
            mind.advance(1f/90f,listOf(rain))
        }
        assertFalse("safe landing must be reached while other systems still order grazing",mind.danger(mind.point,listOf(rain)))
    }

    @Test fun nearbyRainAndFireCauseEscapeWhileRemoteAnimalsRemainUnfrightened() {
        for(kind in Kind.entries) {
            val nav=LocalNavigation({_,_->.03f});val p=Point(.2f,-.12f)
            val mind=PrimitiveFaunaMind(nav,p,.022f,.004f,.018f,0)
            val sim=LocalInterventionWorld(nav);assertTrue(sim.cast(kind,p,.03f,radius=.06f))
            mind.advance(.1f,sim.hazards);assertEquals(PrimitiveFaunaMind.Gait.FLEE,mind.gait)
            repeat(100){mind.advance(.1f,sim.hazards)}
            assertFalse(mind.danger(mind.point,sim.hazards));assertTrue(mind.point.distance(p)>.09f)
            val remote=PrimitiveFaunaMind(nav,Point(-.3f,.2f),.01f,.005f,.02f,1)
            remote.advance(.1f,sim.hazards);assertNotEquals(PrimitiveFaunaMind.Gait.FLEE,remote.gait)
            repeat(500){mind.advance(.1f,emptyList())};assertTrue(mind.point.distance(p)<.05f)
        }
    }
    @Test fun escapeCannotCrossWaterOrBuildingAndMovingRainReplans() {
        val nav=LocalNavigation({x,_->if(abs(x)<.035f)null else .03f},{x,z->x in .12f.. .18f && z in -.04f.. .04f})
        val mind=PrimitiveFaunaMind(nav,Point(.1f,.09f),.02f,.006f,.025f,0)
        val h=Hazard(1,Kind.RAIN,mind.point,.03f,.06f,held=true)
        repeat(160){i->if(i==50)h.point=mind.point;mind.advance(.1f,listOf(h));assertTrue(nav.open(mind.point));assertTrue(mind.point.x>=.035f)}
        assertFalse(mind.danger(mind.point,listOf(h)))
    }
    @Test fun thirdFireRemainsIndependentFourthRejectedAndSlotReusableOnlyAfterFade() {
        val w=LocalInterventionWorld(LocalNavigation({_,_->.03f}))
        repeat(3){assertTrue(w.cast(Kind.FIRE,Point(.1f*it,.1f),.03f))}
        val ids=w.hazards.map{it.id};assertFalse(w.cast(Kind.FIRE,Point(-.2f,.1f),.03f));assertEquals(ids,w.hazards.map{it.id})
        w.advance(15f);assertTrue(w.hazards.all{it.strength==1f});w.advance(1.5f);assertTrue(w.hazards.all{abs(it.strength-.5f)<.001f})
        assertFalse(w.cast(Kind.FIRE,Point(-.2f,.1f),.03f));w.advance(1.5f);assertTrue(w.hazards.isEmpty());assertTrue(w.cast(Kind.FIRE,Point(-.2f,.1f),.03f))
    }
}
