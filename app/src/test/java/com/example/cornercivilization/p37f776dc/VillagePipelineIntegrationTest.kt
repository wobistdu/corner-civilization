package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import org.junit.Assert.*
import org.junit.Test

/** Public resident response bridge, including existing hauling and escape tasks. */
class VillagePipelineIntegrationTest {
    private fun fixture():Pair<LocalInterventionWorld,LivingVillage> {
        val world=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
        val village=LivingVillage(world);world.village=village
        village.life.enable();village.life.reserveHearthBuilders=false
        village.ecology.food=8;village.life.foodBaseline=8;village.life.water=2f
        return world to village
    }
    @Test fun idleDailyHaulerActuallyPicksUpCarriesAndStacksGift() {
        val (world,village)=fixture()
        val id="PrimitiveResident_2"
        var current=world.depot
        world.wildlife.add(id,WildlifeWorld.Species.RESIDENT,current,.003f)
        assertTrue(world.dropWood(Point(-.02f,.15f)))
        var carried=false
        for(step in 0 until 300) {
            world.advance(.1f)
            current=world.respond(id,current,.1f,true)?.point?:current
            if(world.woods.single().phase==LocalInterventionWorld.WoodPhase.CARRIED)carried=true
            if(world.woods.single().phase==LocalInterventionWorld.WoodPhase.STACKED)break
        }
        assertTrue("real carrying occurred",carried)
        assertEquals(LocalInterventionWorld.WoodPhase.STACKED,world.woods.single().phase)
        assertNull(world.woods.single().carrier)
        assertTrue(village.life.enabled)
    }
    @Test fun escapedResidentEatsEvenWhileOriginalWorksiteRemainsDangerous() {
        val (world,village)=fixture()
        val id="PrimitiveResident_0"
        val dangerousOrigin=Point(0f,0f)
        var current=dangerousOrigin
        world.wildlife.add(id,WildlifeWorld.Species.RESIDENT,current,.003f)
        world.wildlife.add("Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.025f,0f),.01f)
        village.life.person(id).hunger=.85f
        repeat(500) {
            world.advance(.1f)
            current=world.respond(id,current,.1f,false)?.point?:current
        }
        assertTrue(world.dangerous(dangerousOrigin))
        assertFalse(world.dangerous(current))
        assertEquals("safety must release the old flee task into daily life",1,village.life.foodEaten)
        assertEquals(7,village.ecology.food)
        assertTrue(village.life.person(id).hunger<.3f)
    }
    @Test fun sharedJarCanBeUsedFromClearEdgeWithoutEnteringAnotherBody() {
        val (world,village)=fixture()
        val id="PrimitiveResident_1"
        var current=Point(world.depot.x+.004f,world.depot.z+.011f)
        world.wildlife.add(id,WildlifeWorld.Species.RESIDENT,current,.003f)
        world.wildlife.add("PrimitiveResident_2",WildlifeWorld.Species.RESIDENT,Point(world.depot.x-.003f,world.depot.z-.003f),.008f)
        village.life.person(id).thirst=.9f
        repeat(80) {
            world.advance(.1f)
            current=world.respond(id,current,.1f,false)?.point?:current
            val other=world.wildlife.bodies.getValue("PrimitiveResident_2")
            assertTrue(current.distance(other.point)>=.011f-.0001f)
        }
        assertEquals(1,village.life.person(id).drinks)
        assertTrue(village.life.person(id).thirst<.3f)
        assertEquals(1.75f,village.life.water,.0001f)
    }
    @Test fun savedAnimalCannotOverlapResidentAfterResidentLocationRestore() {
        val (world,_)=fixture()
        val id="PrimitiveResident_6"
        val start=world.navigation.points.minBy{it.distance(Point(.35f,.23f))}
        world.wildlife.add(id,WildlifeWorld.Species.RESIDENT,start,.005f)
        world.wildlife.add("MammothAdult_1",WildlifeWorld.Species.ADULT,start,.015f)
        val restored=world.wildlife.restoreResidents(emptyMap(),world.navigation.points).getValue(id)
        assertTrue(world.navigation.open(restored))
        assertTrue(restored.distance(start)>=.0206f)
        val preferred=world.navigation.points.minBy{it.distance(Point(.2f,.12f))}
        assertEquals(preferred,world.wildlife.restoreResidents(mapOf(id to preferred),world.navigation.points).getValue(id))
    }
}
