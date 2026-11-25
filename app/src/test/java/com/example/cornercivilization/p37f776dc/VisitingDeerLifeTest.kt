package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import org.junit.Assert.*
import org.junit.Test

class VisitingDeerLifeTest {
    private fun world()=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
    private fun tick(w:LocalInterventionWorld,l:VisitingDeerLife,n:Int) {
        repeat(n){w.advance(.05f);l.advance(.05f)}
    }
    @Test fun drinkingRequiresTravelAndContinuousContact() {
        val w=world();val bank=Point(.105f,.105f)
        val l=VisitingDeerLife(w,listOf(bank))
        val s=l.add("deer",Point(.035f,.105f),.005f,0)!!
        tick(w,l,40)
        assertEquals(0,s.drinks)
        assertTrue(s.thirst>=.64f)
        assertTrue(s.body.point.distance(bank)>.006f)
        tick(w,l,180)
        assertEquals(1,s.drinks)
        assertTrue(s.thirst<.2f)
        assertTrue(s.body.point.distance(bank)<.006f)
    }
    @Test fun blockedBankDoesNotGrantRemoteDrinkAndReopens() {
        val w=LocalInterventionWorld(LocalNavigation({x,z->
            if(kotlin.math.abs(x)<.021f&&kotlin.math.abs(z-.14f)>.014f)null else .034f
        }))
        val bank=Point(.105f,.14f)
        val l=VisitingDeerLife(w,listOf(bank))
        val s=l.add("deer",Point(-.105f,.14f),.005f,0)!!
        w.beginRain(Point(0f,.14f),.034f)
        tick(w,l,600)
        assertEquals(0,s.drinks)
        assertTrue(s.thirst>.64f)
        w.hazards.clear()
        tick(w,l,1200)
        assertTrue("after the ford reopens, real water remains reachable",s.drinks>=1)
    }
    @Test fun finiteGrassIsConsumedOnlyAtThePatch() {
        val w=world();val target=Point(.07f,.105f)
        val patch=LocalInterventionWorld.WetSpot(target,0f,.4f);w.wetSpots+=patch
        val l=VisitingDeerLife(w,emptyList())
        val s=l.add("deer",Point(0f,.105f),.005f,0)!!
        s.thirst=.1f;s.hunger=.8f
        // Advance just the visitor to isolate its finite consumption from rain/drying.
        repeat(40){l.advance(.05f)}
        assertEquals(0,s.grazes);assertEquals(.4f,patch.growth,0f)
        repeat(180){l.advance(.05f)}
        assertEquals(1,s.grazes);assertEquals(.2f,patch.growth,.000001f)
        assertTrue(s.hunger<.5f)
        assertTrue(s.body.point.distance(target)<.006f)
    }
    @Test fun pauseDoesNotAdvanceNeedsWorkOrPosition() {
        val w=world();val bank=Point(.105f,.105f)
        val l=VisitingDeerLife(w,listOf(bank));val s=l.add("deer",bank,.005f,0)!!
        tick(w,l,20)
        assertTrue(s.worked>0f&&s.worked<2f)
        val work=s.worked;val hunger=s.hunger;val thirst=s.thirst;val point=s.body.point
        repeat(100){l.advance(0f)}
        assertEquals(work,s.worked,0f);assertEquals(hunger,s.hunger,0f)
        assertEquals(thirst,s.thirst,0f);assertEquals(point,s.body.point);assertEquals(0,s.drinks)
    }
    @Test fun nearbyFireInterruptsDrinkingWithoutReward() {
        val w=world();val bank=Point(.105f,.105f)
        val l=VisitingDeerLife(w,listOf(bank));val s=l.add("deer",bank,.005f,0)!!
        tick(w,l,20)
        assertTrue(s.worked>0f)
        w.cast(LocalInterventionWorld.Kind.FIRE,bank,.034f)
        tick(w,l,60)
        assertEquals(0,s.drinks);assertEquals(0f,s.worked,0f)
        assertTrue(s.escapes>=1);assertFalse(w.dangerous(s.body.point))
        assertTrue(s.body.point.distance(bank)>.06f)
    }
    @Test fun offGridWalkerCanReachEscapeRouteBeforeRainStops() {
        for(direction in listOf(-1f,1f)) {
            val w=world()
            val l=VisitingDeerLife(w,emptyList())
            val s=l.add("deer",Point(0f,.105f),.005f,0)!!
            // An actual walking position between grid nodes, not a spawn-only case.
            s.body.point=Point(direction*.002f,.105f)
            val rain=Point(-direction*.035f,.105f)
            assertNotNull(w.beginRain(rain,.034f))
            var escaped=false
            repeat(160) {
                tick(w,l,1)
                assertTrue(w.navigation.open(s.body.point))
                if(!w.weatherDangerous(s.body.point))escaped=true
            }
            assertTrue("walking direction=$direction must leave active rain",escaped)
            assertTrue(s.escapes>=1)
            assertTrue(w.hazards.single().held)
            assertEquals(0,s.drinks)
        }
    }
    @Test fun gentleVisitorBlocksBodiesWithoutFrighteningResidents() {
        val w=world();val at=Point(.105f,.105f)
        val l=VisitingDeerLife(w,emptyList());val s=l.add("deer",at,.005f,0)!!
        val p=w.wildlife.add("person",WildlifeWorld.Species.RESIDENT,Point(0f,.105f),.004f)
        assertFalse(w.dangerous(at))
        assertTrue(w.wildlife.escapeSafe(p.point,Point(.08f,.105f)))
        assertFalse(w.residentRouteAllowed("person")(s.body.point))
        assertTrue(w.residentRouteAllowed("person")(Point(.084f,.105f)))
        val moved=w.moveResident("person",Point(.21f,.105f))
        assertTrue(moved.x<s.body.point.x)
        assertTrue(moved.distance(s.body.point)>=.0096f-.000001f)
        l.close();assertTrue(w.wildlife.bodies.containsKey("person"));assertFalse(w.wildlife.bodies.containsKey("deer"))
    }
    @Test fun visitorDoesNotEnterFiveAnimalOrLifeSaveAndAvoidsSpawnOverlap() {
        val w=world();val v=LivingVillage(w);w.village=v;v.life.enable()
        val names=listOf("MammothAdult_0","MammothAdult_1","MammothCalf","Sabertooth_0","Sabertooth_1")
        names.forEachIndexed { i,id->w.wildlife.add(id,when(i){0,1->WildlifeWorld.Species.ADULT;2->WildlifeWorld.Species.CALF;else->WildlifeWorld.Species.TIGER},
            Point(-.28f+i*.14f,-.14f),.01f) }
        val preferred=Point(.105f,.105f)
        val resident=w.wildlife.add("PrimitiveResident_0",WildlifeWorld.Species.RESIDENT,preferred,.005f)
        val l=VisitingDeerLife(w,listOf(preferred));val s=l.add("deer",preferred,.005f,0)!!
        assertTrue(s.body.point.distance(resident.point)>=.0106f)
        w.wildlife.decide(.1f,w);v.animalLife.decide(.1f);v.animalLife.settle(.1f)
        assertEquals(names.toSet(),w.wildlife.save().map{it.id}.toSet())
        assertFalse(v.animalLife.states.containsKey("deer"))
        v.life.validate(v.life.snapshot())
        val copy=WildlifeWorld();copy.restore(w.wildlife.save(),w.navigation::open)
        assertTrue(s.body.alive);assertEquals(100f,s.body.health,0f)
    }
    @Test fun deerWalksAroundStationaryHumanWithoutOverlap() {
        val w=world();val bank=Point(.14f,.105f)
        val person=w.wildlife.add("person",WildlifeWorld.Species.RESIDENT,Point(.07f,.105f),.012f)
        val l=VisitingDeerLife(w,listOf(bank));val s=l.add("deer",Point(0f,.105f),.005f,0)!!
        var detour=false
        repeat(400) {
            tick(w,l,1)
            assertTrue(s.body.point.distance(person.point)>=.0176f-.000001f)
            if(kotlin.math.abs(s.body.point.z-.105f)>.017f)detour=true
        }
        assertTrue(detour);assertEquals(1,s.drinks)
    }
}
