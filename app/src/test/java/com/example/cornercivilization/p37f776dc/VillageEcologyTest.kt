package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Assert.*
import org.junit.Test

class VillageEcologyTest {
    private fun world()=LocalInterventionWorld(LocalNavigation({ _,_ -> .034f }))

    @Test fun onlyDeliveredWoodCanFinishTheFirstExpansion() {
        val world=world()
        val village=LivingVillage(world)
        world.village=village
        village.enableExpansion()
        village.agriculture=true
        village.ecology.raids=1
        village.ecology.food=1
        world.advance(.1f)
        assertFalse(village.houses.getValue("FieldFence").available)
        repeat(3) { assertTrue(world.dropWood(world.depot));world.woods.last().phase=WoodPhase.STACKED }
        val positions=(2..8).associate { "PrimitiveResident_$it" to world.depot }.toMutableMap()
        repeat(1800) {
            world.advance(.1f)
            for(id in positions.keys) {
                val response=world.respond(id,positions.getValue(id),.1f,id.last() in "258")
                if(response!=null) positions[id]=response.point
            }
        }
        assertEquals(1,village.houses.getValue("FieldFence").rebuilds)
        assertEquals(3,village.delivered)
    }

    @Test fun residentTravelsFromWaterToFireBeforeCoolingIt() {
        val world=world()
        val village=LivingVillage(world)
        world.village=village
        village.ecology.bank=Point(.02f,.12f)
        val source=Point(.095f,.12f)
        world.cast(Kind.FIRE,source,.034f)
        var worker=village.ecology.bank
        var carried=false
        var cooled=false
        repeat(90) {
            world.advance(.1f)
            world.respond("PrimitiveResident_7",worker,.1f,false)?.let { worker=it.point }
            carried=carried || village.jobs["PrimitiveResident_7"]?.kind=="quench"
            cooled=cooled || world.hazards.firstOrNull()?.heat?.let { it<.5f }==true
        }
        assertTrue(carried);assertTrue(cooled)
    }

    @Test fun grazingAndHarvestCompeteForOneCrop() {
        val field=VillageEcology()
        field.graze(6f);field.harvest()
        assertEquals(0,field.food)
        field.grow(22f,1f);field.harvest()
        assertEquals(1,field.food)
        assertTrue(field.crop in .05f..1f)
    }
}
