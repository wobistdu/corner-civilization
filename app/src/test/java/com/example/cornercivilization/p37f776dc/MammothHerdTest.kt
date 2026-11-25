package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.*
import org.junit.Assert.*
import org.junit.Test

class MammothHerdTest {
    private fun fixture():Pair<LocalInterventionWorld,LivingVillage> {
        val w=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
        val v=LivingVillage(w);w.village=v;v.sources+=Point(.15f,.12f);v.life.enable()
        return w to v
    }
    @Test fun herdFollowsAndOnlyAnOccasionalSafeOutingOverridesFollowing() {
        val(w,v)=fixture()
        val lead=w.wildlife.add("MammothAdult_0",Species.ADULT,Point(.22f,.18f),.018f)
        val mate=w.wildlife.add("MammothAdult_1",Species.ADULT,Point(.32f,.18f),.018f)
        val calf=w.wildlife.add("MammothCalf",Species.CALF,Point(.25f,.13f),.012f)
        var outings=0
        val pasture=Point(.25f,-.02f)
        v.animalLife.excursionSite={_,_->outings++;pasture}
        w.wildlife.decide(0f,w)
        val follow=requireNotNull(w.wildlife.order(mate.id))
        assertTrue(follow.brisk)
        assertTrue(requireNotNull(follow.point).distance(lead.point)<mate.point.distance(lead.point))
        v.life.clock=69f;w.wildlife.decide(0f,w);assertEquals(0,outings)
        v.life.clock=71f;w.wildlife.decide(0f,w)
        assertEquals(pasture,w.wildlife.order(calf.id)?.point)
        val tiger=w.wildlife.add("Sabertooth_0",Species.TIGER,Point(.25f,.055f),.009f)
        tiger.hunger=.90f
        w.wildlife.decide(.1f,w)
        assertTrue(w.wildlife.order(calf.id)?.flee==true)
        assertNotEquals(pasture,w.wildlife.order(calf.id)?.point)
        tiger.point=Point(-.30f,-.20f)
        v.life.clock=112f;w.wildlife.decide(0f,w)
        assertEquals(1,outings);assertNotEquals(pasture,w.wildlife.order(calf.id)?.point)
    }

}
