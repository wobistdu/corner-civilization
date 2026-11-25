package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.*
import org.junit.Assert.*
import org.junit.Test

class VillageWildlifeLifeTest {
    private fun fixture():Pair<LocalInterventionWorld,LivingVillage> {
        val w=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
        val v=LivingVillage(w);w.village=v;v.sources+=Point(.15f,.12f);v.life.enable()
        return w to v
    }
    private fun step(w:LocalInterventionWorld,v:LivingVillage,dt:Float=.1f) {
        v.life.clock+=dt
        w.wildlife.decide(dt,w)
        w.wildlife.bodies.values.toList().forEach{b->
            w.wildlife.order(b.id)?.point?.let{goal->
                // 平坦无障碍夹具中直接沿命令方向行进，仍使用整段身体碰撞。
                val p=goal
                val d=p.distance(b.point);val move=minOf(.021f*dt,d)
                if(d>0f)w.wildlife.move(b.id,Point(b.point.x+(p.x-b.point.x)*move/d,b.point.z+(p.z-b.point.z)*move/d)){w.navigation.open(it)}
            }
        }
        v.animalLife.settle(dt)
    }
    @Test fun returnedCalfRetainsHistoryAndReleasesItsFootprintAfterRestore() {
        val (w,v)=fixture()
        val p=Point(.14f,.11f)
        val calf=w.wildlife.add("MammothCalf",Species.CALF,p,.008f)
        val tiger=w.wildlife.add("Sabertooth_0",Species.TIGER,Point(.20f,.11f),.006f)
        calf.alive=false;calf.health=0f;tiger.kills=1
        assertFalse(w.wildlife.open(tiger.id,p))
        v.animalLife.states[tiger.id]=VillageWildlifeLife.State(carcassReturned=true)
        w.wildlife.decide(.1f,w)
        assertTrue(calf.returnedToSoil)
        assertTrue(w.wildlife.open(tiger.id,p))
        assertEquals(p,w.wildlife.move(tiger.id,p))
        assertFalse(w.wildlife.save().single{it.id==calf.id}.alive)
        assertEquals(1,tiger.kills)
    }

    @Test fun nearbyFireInterruptsAccumulatedRestAndWaterContact() {
        for(e in listOf(VillageEvent.ELEPHANT_SHADE,VillageEvent.TIGER_DRINK)) {
            val(w,v)=fixture();val tiger=e==VillageEvent.TIGER_DRINK
            val b=w.wildlife.add(if(tiger)"Sabertooth_0"else"MammothAdult_0",if(tiger)Species.TIGER else Species.ADULT,Point(.15f,.12f),.008f)
            val s=VillageWildlifeLife.State(thirst=.9f,fatigue=.9f,intent=e,goal=b.point,elapsed=if(tiger).95f else 4.95f)
            v.animalLife.states[b.id]=s
            w.cast(Kind.FIRE,b.point,.034f)
            step(w,v)
            assertEquals(0,v.life.journal.counts[e]?:0)
            assertEquals(0,s.drinks)
            assertTrue(s.thirst>=.9f);assertTrue(s.fatigue>=.9f)
            assertTrue(s.elapsed<=.95f||!tiger&&s.elapsed<=4.95f)
        }
    }
    @Test fun thirstyTigerTravelsToWaterBeforeRecovering() {
        val(w,v)=fixture();val b=w.wildlife.add("Sabertooth_0",Species.TIGER,Point(.15f,.12f),.008f)
        val s=VillageWildlifeLife.State(thirst=.9f);v.animalLife.states[b.id]=s
        step(w,v);assertEquals(0,s.drinks);assertTrue(b.point.distance(v.ecology.bank)>.05f)
        repeat(1800){if(s.drinks==0)step(w,v)}
        assertEquals(1,s.drinks);assertEquals(0f,s.thirst,0f)
        assertTrue(b.point.distance(v.ecology.bank)<.025f)
        assertEquals(1,v.life.journal.counts[VillageEvent.TIGER_DRINK])
    }
    @Test fun absentParentCancelsProtectedRest() {
        val(w,v)=fixture();val b=w.wildlife.add("MammothCalf",Species.CALF,Point(.15f,.12f),.004f)
        val s=VillageWildlifeLife.State(fatigue=.8f,intent=VillageEvent.CALF_REST_PROTECTED,goal=b.point,elapsed=4.95f)
        v.animalLife.states[b.id]=s;step(w,v)
        assertNull(s.intent);assertEquals(0,v.life.journal.counts[VillageEvent.CALF_REST_PROTECTED]?:0)
        assertTrue(s.fatigue>=.8f)
    }
    @Test fun mudBathAndShadeRequireContinuousContact() {
        for(mud in listOf(false,true)) {
            val(w,v)=fixture();val p=v.sources.single()
            val b=w.wildlife.add("MammothAdult_0",Species.ADULT,p,.008f)
            val s=VillageWildlifeLife.State(fatigue=.9f);v.animalLife.states[b.id]=s
            if(mud)w.wetSpots+=WetSpot(p,.8f)
            repeat(49){step(w,v)}
            val e=if(mud)VillageEvent.ELEPHANT_MUD else VillageEvent.ELEPHANT_SHADE
            assertEquals(0,v.life.journal.counts[e]?:0);assertTrue(s.fatigue>.9f)
            repeat(3){step(w,v)}
            assertEquals(1,v.life.journal.counts[e]);assertTrue(s.fatigue<.6f)
            assertEquals(if(mud).9f else 0f,s.coat,.001f)
        }
    }
    @Test fun realHoovesAndRecoveredMeadowChangeTheNextWorkDemand() {
        val(w,v)=fixture();v.agriculture=true;v.ecology.crop=.8f
        val b=w.wildlife.add("MammothAdult_0",Species.ADULT,v.ecology.field,.008f)
        v.animalLife.settle(.1f)
        assertEquals(.72f,v.ecology.crop,.0001f);assertEquals(.3f,v.life.trampled,.0001f)
        assertEquals(1,v.life.journal.counts[VillageEvent.CROP_TRAMPLED])
        v.animalLife.settle(.1f);assertEquals(.72f,v.ecology.crop,.0001f)
        val p=WetSpot(Point(.2f,.1f),.8f,.2f);w.wetSpots+=p
        v.animalLife.settle(.1f);p.growth=.7f;v.animalLife.settle(.1f)
        assertEquals(p.point,b.goal);assertEquals(1,v.life.journal.counts[VillageEvent.MEADOW_RECOVER])
    }
}
