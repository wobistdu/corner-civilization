package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VillageLifeCodecTest {
    private fun life():VillageLife {
        val w=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
        val v=LivingVillage(w);w.village=v
        v.addHouse("Shelter_0",Point(.05f,.1f),.02f,Point(.026f,.1f))
        v.sources+=Point(.1f,.15f)
        return v.life.also{it.enable()}
    }
    @Test fun fullStateSurvivesTextEncodingWithoutSharingObjects() {
        val l=life();val id="PrimitiveResident_0";val peer="PrimitiveResident_1"
        l.clock=100f;l.person(id).apply{hunger=.7f;lastMeal=32f;hostedBy="Shelter_0";warned=true}
        l.person(peer).apply{farmSkill=.6f;interrupted=true}
        l.water=.7f;l.seeds=3;l.bankReeds=2f;l.foodProduced=5
        l.debts["$peer|$id"]=4;l.bonds["$peer|$id"]=.8f
        l.cargoMemory[peer]=Point(.13f,.17f);l.blockedWork[id]=VillageEvent.CUT_RIPE_CROP.name
        l.cutGrass=Point(.2f,.2f);l.grassBait=.7f;l.safeTrail=Point(.05f,.15f)
        l.homes["Shelter_0"]=VillageLife.Home(leak=.4f,brace=.8f,salvage=1,damp=.2f)
        l.patches.single().apply{berries=.5f;resting=true;branches=0;regrowth=.4f}
        l.journal.record(VillageEvent.FORAGE_BERRIES,id,40f,"bundle")
        val original=l.snapshot()
        val decoded=VillageLifeCodec.decode(JSONObject(VillageLifeCodec.encode(original).toString()))
        assertEquals(original,decoded)
        l.restore(decoded);assertEquals(original,l.snapshot())
        l.person(id).hunger=0f;assertEquals(.7f,decoded.people.getValue(id).hunger,0f)
    }
    @Test fun unknownSchemaMissingFieldsAndUnknownEventsAreRejected() {
        val l=life();val saved=l.snapshot()
        for(kind in listOf("schema","field","event")){
            val j=VillageLifeCodec.encode(saved)
            when(kind){
                "schema"->j.put("schema",2)
                "field"->j.remove("people")
                else->j.getJSONObject("counts").put("UNKNOWN_EVENT",1)
            }
            var rejected=false
            try{VillageLifeCodec.decode(j)}catch(_:Exception){rejected=true}
            assertTrue(kind,rejected);assertEquals(saved,l.snapshot())
        }
    }
    @Test fun invalidDecodedNumbersCannotPartiallyRestore() {
        val l=life();l.water=.6f;val before=l.snapshot()
        val json=VillageLifeCodec.encode(before)
        json.getJSONObject("floats").put("water",.9f).put("soilHard",-1f)
        try{l.restore(VillageLifeCodec.decode(json));fail("invalid soil accepted")}
        catch(_:IllegalArgumentException){}
        assertEquals(before,l.snapshot())
    }
}
