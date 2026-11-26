package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.scene.VillageLifeCodec
import org.junit.Assert.*
import org.junit.Test

class VillageInfluenceTest {
    private val world=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
    private val village=LivingVillage(world).also{world.village=it;it.life.enable()}
    private val tools=village.influence
    private val first="PrimitiveResident_0"
    private val second="PrimitiveResident_1"
    @Test fun hearingIsLocalAndCarrierKeepsTheBlank(){
        val spot=Point(.02f,.2f)
        village.residents[first]=spot;village.residents[second]=Point(.04f,.2f)
        village.residents["PrimitiveResident_2"]=Point(-.4f,-.2f)
        val carry=LivingVillage.Job("life_MOULD_CLAY",null,mutableListOf(),world.depot,carrying=true,event=VillageEvent.MOULD_CLAY)
        village.jobs[second]=carry
        assertTrue(tools.drum(spot))
        assertEquals("drumGather",village.jobs[first]?.kind);assertSame(carry,village.jobs[second])
        assertEquals(spot,tools.looking(second));assertNull(tools.looking("PrimitiveResident_2"))
        assertFalse(tools.drum(spot));world.advance(2.2f);assertNull(tools.looking(first))
    }
    @Test fun alarmPropagatesToListenersWhoWalkToCover(){
        val spot=Point(.04f,.20f)
        village.addHouse("Shelter_0",Point(-.055f,.20f),.01f,Point(-.04f,.20f))
        village.residents[first]=spot;village.residents[second]=Point(-.012f,.20f)
        world.wildlife.add("Sabertooth_0",WildlifeWorld.Species.TIGER,Point(.14f,.20f),.008f)
        assertTrue(tools.drum(spot));assertTrue(tools.beat!!.alarm)
        assertFalse(village.life.person(second).warned)
        repeat(12){world.advance(.1f)}
        assertTrue(village.life.person(second).warned)
        assertEquals("drumShelter",village.jobs[second]?.kind)
    }
    @Test fun farMeadowRipensLocallyAndAnAdultMustReachIt(){
        world.farBank={it.x>.12f}
        val near=Point(-.10f,.18f);val far=Point(.22f,.16f)
        assertEquals(2,tools.plant(listOf(near,far,far)))
        world.wetSpots+=LocalInterventionWorld.WetSpot(far,1f)
        repeat(10){tools.advance(1f)}
        assertEquals(0f,tools.plots.first().growth,0f)
        assertEquals(far,tools.meadow(far))
        val adult=world.wildlife.add("MammothAdult_0",WildlifeWorld.Species.ADULT,Point(.28f,.16f),.015f)
        village.animalLife.decide(.1f)
        assertEquals(far,world.wildlife.order(adult.id)?.point)
        tools.advance(.1f);assertEquals(2,tools.plots.size)
        adult.point=far;adult.hunger=.5f;tools.advance(.1f)
        assertEquals(2,tools.plots.size);repeat(20){tools.advance(.1f)}
        assertEquals(.32f,adult.hunger,.00001f)
        assertEquals(listOf(near),tools.plots.map{it.point})
    }
    @Test fun restoreKeepsGrowthAndOldSaveStartsWithoutPlantings(){
        tools.plant(listOf(Point(-.1f,.18f)));tools.plots[0].growth=.55f
        val saved=VillageLifeCodec.encode(village.life.snapshot())
        tools.plots.clear();village.life.restore(VillageLifeCodec.decode(saved))
        assertEquals(.55f,tools.plots.single().growth,.0001f)
        saved.remove("plantings");village.life.restore(VillageLifeCodec.decode(saved))
        assertTrue(tools.plots.isEmpty())
    }
    @Test fun changingResidentDropsThePreviousDiaryAndTracks(){
        village.residents[first]=world.depot;village.residents[second]=Point(.1f,.1f)
        assertTrue(tools.follow(first));val old=tools.diary
        tools.witnessed(second,VillageEvent.FIRE_CISTERN);assertEquals(1,old.snapshot().size)
        tools.witnessed(first,VillageEvent.MOULD_CLAY);assertEquals(2,old.snapshot().size)
        assertTrue(tools.follow(second));assertNotSame(old,tools.diary)
        assertEquals(listOf(village.residents[second]),tools.footsteps.toList())
        assertTrue(tools.follow(second));assertNull(tools.followed);assertTrue(tools.footsteps.isEmpty())
    }
}
