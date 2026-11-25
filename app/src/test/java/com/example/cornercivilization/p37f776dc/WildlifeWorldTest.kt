package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Assert.*
import org.junit.Test

class WildlifeWorldTest {
    private fun world()=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
    private fun animal(w:LocalInterventionWorld,id:String,s:WildlifeWorld.Species,p:Point,r:Float=.012f)=w.wildlife.add(id,s,p,r)
    @Test fun onlyFarBankContactTakesAResidentAndReturnsThemFromAnEdgeAfterTwentySeconds() {
        val w=world()
        val v=LivingVillage(w);w.village=v
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.22f,.10f))
        val p=animal(w,"PrimitiveResident_0",WildlifeWorld.Species.RESIDENT,Point(.238f,.10f),.004f)
        w.wildlife.decide(.02f,w);w.wildlife.settle(.02f,w)
        assertTrue(p.alive)
        w.farBank={it.x>.15f}
        p.point=Point(.28f,.10f)
        w.wildlife.decide(.02f,w);w.wildlife.settle(.02f,w)
        assertTrue(p.alive)
        p.point=Point(.238f,.10f)
        w.wildlife.decide(.02f,w);w.wildlife.settle(.02f,w)
        assertFalse(p.alive);assertFalse(p.id in v.residents)
        assertTrue(w.wildlife.open(t.id,p.point))
        w.advance(19.9f);assertFalse(p.alive)
        w.advance(.11f);assertTrue(p.alive);assertFalse(w.farBank(p.point))
        assertTrue(kotlin.math.abs(p.point.x)>.40f || kotlin.math.abs(p.point.z)>.292f)
        assertTrue(p.id in v.residents)
        assertEquals(2,w.chronicle.snapshot().size)
    }

    @Test fun aNewCalfOnlyEntersAfterRemainsAndASafeEntry() {
        val w=world();val v=LivingVillage(w);w.village=v;v.life.enabled=true
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.3f,.2f))
        animal(w,"MammothAdult_0",WildlifeWorld.Species.ADULT,Point(.32f,.22f))
        val tiger=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(-.2f,-.1f))
        tiger.kills=1;c.alive=false;c.health=0f
        v.animalLife.states[tiger.id]=VillageWildlifeLife.State(carcassReturned=true)
        v.animalLife.decide(0f);assertFalse(c.alive)
        v.life.clock=29.99f;v.animalLife.decide(0f);assertFalse(c.alive)
        v.life.clock=30f;v.animalLife.decide(0f);assertFalse(c.alive)
        v.animalLife.calfEntrySite={_,_->Point(.38f,.22f)}
        v.life.clock=31f;v.animalLife.decide(0f)
        assertTrue(c.alive);assertFalse(c.returnedToSoil);assertEquals(1,tiger.kills)
    }

    @Test fun fastCrossingStopsAtBodyAndCanGoAroundWithoutInterpenetration() {
        val w=world()
        animal(w,"person",WildlifeWorld.Species.RESIDENT,Point(-.08f,.1f),.004f)
        val tiger=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(0f,.1f))
        val at=w.moveResident("person",Point(.08f,.1f))
        assertTrue(at.x<0f);assertTrue(at.distance(tiger.point)>=.0165f)
        w.moveResident("person",Point(at.x,.15f))
        val around=w.moveResident("person",Point(.08f,.15f))
        assertEquals(.08f,around.x,.000001f)
        val before=tiger.point
        w.wildlife.move(tiger.id,Point(.15f,.15f))
        assertTrue(tiger.point.distance(w.wildlife.bodies.getValue("person").point)>=.0165f)
        assertTrue(tiger.point.distance(before)>0f)
    }
    @Test fun wholeMovementCannotJumpOverWaterOrBuildings() {
        val w=LocalInterventionWorld(LocalNavigation({x,_->if(x in -.01f.. .01f)null else .034f}))
        animal(w,"person",WildlifeWorld.Species.RESIDENT,Point(-.08f,.1f),.004f)
        assertTrue(w.moveResident("person",Point(.08f,.1f)).x<-.01f)
    }
    @Test fun initiallyOverlappingBodiesMayOnlySeparate() {
        val w=world()
        animal(w,"person",WildlifeWorld.Species.RESIDENT,Point(.007f,.1f),.004f)
        animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(0f,.1f))
        assertEquals(.007f,w.moveResident("person",Point(-.04f,.1f)).x,.000001f)
        assertEquals(.06f,w.moveResident("person",Point(.06f,.1f)).x,.000001f)
    }
    @Test fun everyAnimalInterruptsActualFoodCarriersAndFleeingPreservesCargo() {
        for(species in listOf(WildlifeWorld.Species.TIGER,WildlifeWorld.Species.ADULT,WildlifeWorld.Species.CALF)) {
            val w=world();val v=LivingVillage(w);w.village=v
            val id="PrimitiveResident_0";var p=Point(-.1f,.2f)
            animal(w,id,WildlifeWorld.Species.RESIDENT,p,.004f)
            val bundle=LivingVillage.FoodBundle(p,id);v.foodBundles+=bundle
            v.jobs[id]=LivingVillage.Job("foodDelivery",null,mutableListOf(w.depot),w.depot,carrying=true,food=bundle)
            val a=animal(w,"animal",species,Point(p.x+.023f,p.z))
            repeat(60){w.advance(.1f);w.respond(id,p,.1f,false)?.let{m->if(it==0)assertTrue(m.fleeing);p=m.point
                assertTrue(p.distance(a.point)>=.0165f)}}
            assertFalse(w.dangerous(p));assertEquals(0,v.ecology.food)
            assertEquals(1,v.foodBundles.size);assertNull(bundle.carrier)
            assertEquals(Point(-.1f,.2f),bundle.point)
        }
    }
    @Test fun woodCarrierDropsAtActualPositionAndResumesAfterTigerLeaves() {
        val w=world();val id="hauler";var p=Point(-.1f,.2f)
        animal(w,id,WildlifeWorld.Species.RESIDENT,p,.004f)
        assertTrue(w.dropWood(p))
        repeat(20){if(w.woods.single().phase!=WoodPhase.CARRIED){w.advance(.1f);w.respond(id,p,.1f,true)?.let{p=it.point}}}
        assertEquals(WoodPhase.CARRIED,w.woods.single().phase)
        val at=p
        val tiger=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(p.x+.026f,p.z))
        w.respond(id,p,.1f,true)?.let{p=it.point}
        assertEquals(WoodPhase.WAITING,w.woods.single().phase);assertEquals(at,w.woods.single().point)
        assertNull(w.woods.single().carrier)
        tiger.point=Point(.35f,-.25f)
        var arrived=false
        repeat(250){w.advance(.1f);w.respond(id,p,.1f,true)?.let{p=it.point};if(w.woods.any{it.phase==WoodPhase.STACKED})arrived=true}
        assertTrue(arrived)
    }
    @Test fun hungryTigerChasesLiveCalfButCannotDamageItRemotely() {
        val w=world()
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.1f,.1f));t.hunger=.9f
        w.wildlife.decide(.1f,w)
        assertNotNull(w.wildlife.order(t.id)?.point);assertTrue(w.wildlife.order(t.id)!!.flee)
        assertTrue(w.wildlife.order(c.id)!!.flee)
        repeat(60){w.wildlife.decide(.1f,w);w.wildlife.settle(.1f,w)}
        assertEquals(100f,c.health,0f);assertEquals(0,t.kills)
    }
    @Test fun adultProtectsCalfAndTigerRetreatsInsteadOfAttacking() {
        val w=world()
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.174f,.1f));t.hunger=1f
        val a=animal(w,"MammothAdult_0",WildlifeWorld.Species.ADULT,Point(.24f,.1f),.02f)
        repeat(30){w.wildlife.decide(.1f,w);w.wildlife.settle(.1f,w)}
        assertEquals(100f,c.health,0f);assertEquals(0,t.kills)
        assertTrue(w.wildlife.order(t.id)!!.flee)
        assertNotNull(w.wildlife.order(a.id)?.point)
        assertTrue(w.wildlife.protections>0)
    }
    @Test fun actualContactPredationSettlesOnceThenFeedingAndRestPersist() {
        val w=world()
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.174f,.1f));t.hunger=1f
        val other=animal(w,"Sabertooth_1",WildlifeWorld.Species.TIGER,Point(.226f,.1f));other.hunger=1f
        repeat(30){w.wildlife.decide(.1f,w);w.wildlife.settle(.1f,w)}
        assertFalse(c.alive);assertEquals(0f,c.health,0f);assertEquals(1,t.kills+other.kills)
        assertEquals(Point(.2f,.1f),c.point)
        val hunter=listOf(t,other).single{it.kills==1}
        assertTrue(hunter.feeding>0f)
        val saved=w.wildlife.save()
        val restored=world();saved.forEach{a->animal(restored,a.id,w.wildlife.bodies.getValue(a.id).species,a.point)}
        restored.wildlife.restore(saved){restored.navigation.open(it)}
        assertFalse(restored.wildlife.bodies.getValue(c.id).alive)
        repeat(150){restored.wildlife.decide(.1f,restored);restored.wildlife.settle(.1f,restored)}
        assertEquals(1,restored.wildlife.bodies.values.sumOf{it.kills})
        assertTrue(restored.wildlife.bodies.getValue(hunter.id).rest>0f)
    }
    @Test fun intermittentContactCannotAccumulateRemoteAttackDamage() {
        val w=world()
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.174f,.1f));t.hunger=1f
        repeat(6) {
            t.point=Point(.174f,.1f);w.wildlife.decide(.3f,w);w.wildlife.settle(.3f,w)
            t.point=Point(.1f,.1f);w.wildlife.decide(.3f,w);w.wildlife.settle(.3f,w)
        }
        assertEquals(100f,c.health,0f)
    }
    @Test fun fireAtContactProtectsCalf() {
        val w=world()
        val c=animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.174f,.1f));t.hunger=1f
        w.cast(Kind.FIRE,t.point,.034f)
        repeat(30){w.wildlife.decide(.1f,w);w.wildlife.settle(.1f,w)}
        assertEquals(100f,c.health,0f)
    }
    @Test fun animalRestoreRejectsInvalidBatchWithoutPartiallyChangingExistingBodies() {
        val w=world()
        animal(w,"MammothCalf",WildlifeWorld.Species.CALF,Point(.2f,.1f))
        val saved=w.wildlife.save().single()
        try {w.wildlife.restore(listOf(saved.copy(alive=false,health=0f),saved.copy(id="wrong"))){true};fail("invalid batch")}
        catch(_:IllegalArgumentException){}
        assertTrue(w.wildlife.bodies.getValue(saved.id).alive)
        val next=WildlifeWorld()
        next.restore(listOf(saved.copy(alive=false,health=0f))){true}
        assertFalse(next.add(saved.id,WildlifeWorld.Species.CALF,Point(0f,0f),.01f).alive)
    }
    @Test fun mammothContactDamagesHouseAndPhysicalHaulingRebuildsIt() {
        val w=world();val v=LivingVillage(w);w.village=v
        v.addHouse("Shelter_0",Point(.08f,.1f),.02f,Point(.045f,.1f));v.sources+=Point(-.06f,.13f)
        val h=v.houses.getValue("Shelter_0")
        assertFalse(v.impact(h.id,Point(-.2f,.1f),.02f));assertEquals(100f,h.health,0f)
        assertTrue(v.impact(h.id,Point(.043f,.1f),.02f))
        assertEquals("animal",h.damageCause);assertEquals(40f,h.health,0f)
        assertTrue(v.impact(h.id,Point(.043f,.1f),.02f));assertTrue(h.ruined)
        val positions=(0..8).associate{"PrimitiveResident_$it" to Point(-.03f+it*.008f,.15f)}.toMutableMap()
        repeat(2400){w.advance(.1f);positions.keys.forEach{id->w.respond(id,positions.getValue(id),.1f,id.last() in listOf('2','5','8'))?.let{positions[id]=it.point}}}
        assertEquals(100f,h.health,.001f);assertEquals(1,h.rebuilds);assertEquals(3,v.delivered)
        assertEquals("none",h.damageCause);assertEquals(2,h.animalHits)
    }
    @Test fun externallyCommandedFaunaUsesBodyCheckAndWeatherCanOverrideHunt() {
        val w=world();val home=Point(.1f,.1f)
        val t=animal(w,"Sabertooth_0",WildlifeWorld.Species.TIGER,home,.01f)
        val person=animal(w,"person",WildlifeWorld.Species.RESIDENT,Point(.14f,.1f),.004f)
        val mind=PrimitiveFaunaMind(w.navigation,home,.01f,.02f,.04f,3)
        val order=WildlifeWorld.Order(Point(.2f,.1f))
        repeat(40){mind.command(order,w.hazards);mind.advance(.1f,w.hazards,move={w.wildlife.move(t.id,it){p->w.navigation.open(p)}})}
        assertTrue(mind.point.x<person.point.x)
        assertTrue(mind.point.distance(person.point)>=.0145f)
        mind.bindBody{w.wildlife.open(t.id,it)}
        w.cast(Kind.RAIN,mind.point,.034f)
        mind.command(order,w.hazards);mind.advance(.1f,w.hazards,move={w.wildlife.move(t.id,it){p->w.navigation.open(p)}})
        assertEquals(PrimitiveFaunaMind.Gait.FLEE,mind.gait)
    }
    @Test fun sharedDepotHandsOverAtReachWithoutCrossingWaitingResident() {
        val w=world();val id="PrimitiveResident_2";var p=Point(-.14f,.227f)
        animal(w,id,WildlifeWorld.Species.RESIDENT,p,.004f)
        val waiting=animal(w,"PrimitiveResident_6",WildlifeWorld.Species.RESIDENT,w.depot,.004f)
        assertTrue(w.dropWood(p))
        var delivered=false
        repeat(200) {
            w.advance(.1f);w.respond(id,p,.1f,true)?.let{p=it.point}
            assertTrue(p.distance(waiting.point)>=.0085f)
            if(!delivered && w.woods.any{it.phase==WoodPhase.STACKED}) {
                delivered=true;assertTrue(p.distance(w.depot)<.012f)
            }
        }
        assertTrue("handover completes without standing in another body",delivered)
    }
    @Test fun blockedSupplyStillVisitsDepotBeforeTakingMaterialToHouse() {
        val w=world();val v=LivingVillage(w);w.village=v
        val id="PrimitiveResident_2";var p=Point(-.14f,.227f)
        animal(w,id,WildlifeWorld.Species.RESIDENT,p,.004f)
        animal(w,"PrimitiveResident_6",WildlifeWorld.Species.RESIDENT,w.depot,.004f)
        v.addHouse("Shelter_0",Point(-.24f,.23f),.015f,Point(-.22f,.25f))
        val h=v.houses.getValue("Shelter_0");h.health=40f;h.damageCause="animal";h.safeSeconds=4f
        assertTrue(w.dropWood(w.depot));w.woods.single().phase=WoodPhase.STACKED
        var pickedUp=false;var arrived=false
        repeat(250) {
            w.advance(.1f);w.respond(id,p,.1f,true)?.let{p=it.point}
            if(w.woods.firstOrNull()?.phase==WoodPhase.CARRIED && !pickedUp) {
                pickedUp=true;assertTrue("no remote material pickup",p.distance(w.depot)<.012f)
            }
            if(!arrived && v.delivered>0) {
                arrived=true;assertTrue("material is delivered at the actual worksite",p.distance(h.workPoint)<.025f)
            }
        }
        assertTrue(pickedUp);assertEquals(1,h.materials);assertEquals(1,v.delivered)
        assertTrue(arrived)
    }
}
