package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import org.junit.Assert.*
import org.junit.Test

class VillageLifeTest {
    private val id="PrimitiveResident_0"
    private val peer="PrimitiveResident_1"
    private fun fixture():Pair<LocalInterventionWorld,LivingVillage> {
        val w=LocalInterventionWorld(LocalNavigation({_,_->.034f}))
        val v=LivingVillage(w);w.village=v
        v.addHouse("Shelter_0",Point(.04f,.1f),.02f,Point(.018f,.1f))
        v.addHouse("Shelter_1",Point(.15f,.12f),.02f,Point(.126f,.12f))
        v.sources+=Point(-.06f,.13f);v.life.enable();v.life.reserveHearthBuilders=false
        v.life.water=1f
        v.life.person(id);v.life.person(peer)
        v.residents[id]=w.depot;v.residents[peer]=Point(.1f,.15f)
        return w to v
    }
    private fun assign(w:LocalInterventionWorld,v:LivingVillage,e:VillageEvent,goal:Point,
                       other:String?=null,house:String?=null) {
        val at=v.residents.getValue(id)
        v.jobs[id]=LivingVillage.Job("life_${e.name}",house,
            w.navigation.path(at,goal)!!.toMutableList(),goal,event=e,peer=other)
    }
    private fun tick(v:LivingVillage,n:Int=1) {
        repeat(n){v.life.clock+=.1f
            for(who in listOf(id,peer))v.jobs[who]?.let{v.respond(who,v.residents.getValue(who),.1f)}
        }
    }
    private fun until(v:LivingVillage,e:VillageEvent,n:Int=2000) {
        repeat(n){if((v.life.journal.counts[e]?:0)==0)tick(v)}
        assertEquals(e.name,1,v.life.journal.counts[e])
    }
    private fun encounter():Pair<LocalInterventionWorld,LivingVillage> {
        val(w,v)=fixture()
        v.ecology.food=8;v.life.clock=30f;v.life.water=1f;v.life.reeds=3;v.life.bankReeds=0f
        v.life.advance(0f);v.life.homes.values.forEach{it.inspected=1f}
        v.life.patches.forEach{it.berries=0f;it.resting=true;it.branches=0}
        for(who in listOf(id,peer))v.life.person(who).apply {
            hunger=.1f;thirst=.1f;fatigue=.1f;lonely=.5f;lastSocial=0f
        }
        v.residents[peer]=Point(w.depot.x+.02f,w.depot.z)
        return w to v
    }
    @Test fun urgentNeighbourRemainsFreeToMeetOwnNeeds() {
        for(need in listOf("hunger","thirst")) {
            val(w,v)=encounter()
            if(need=="hunger"){v.life.person(peer).hunger=.9f;v.ecology.food=0}
            else v.life.person(peer).thirst=.9f
            v.respond(id,w.depot,.1f)
            assertNotEquals(need,"life_reply",v.jobs[peer]?.kind)
        }
    }
    @Test fun interruptedConversationDoesNotGrantSocialReward() {
        val(w,v)=encounter()
        v.respond(id,w.depot,.1f)
        assertEquals(VillageEvent.CHAT_TOGETHER,v.jobs[id]?.event)
        assertEquals("life_reply",v.jobs[peer]?.kind)
        v.interrupt(peer,v.residents.getValue(peer))
        v.respond(id,w.depot,4.1f)
        assertNull(v.life.bonds["$id|$peer"])
        assertEquals(.5f,v.life.person(id).lonely,0f)
        assertNull(v.life.journal.counts[VillageEvent.CHAT_TOGETHER])
    }
    @Test fun distantNeighbourCancelsEncounterWithoutReward() {
        val(w,v)=encounter()
        v.respond(id,w.depot,.1f)
        v.residents[peer]=Point(w.depot.x+.08f,w.depot.z)
        v.respond(id,w.depot,4.1f)
        assertNull(v.life.bonds["$id|$peer"])
        assertNull(v.jobs[id])
    }
    @Test fun localRainChangesOnlyTheNearbyTrail() {
        for(local in listOf(false,true)) {
            val(w,v)=fixture();w.wetSpots+=WetSpot(v.ecology.field,1f)
            w.cast(Kind.RAIN,if(local)w.depot else Point(.3f,-.2f),.034f)
            v.life.advance(.1f)
            assertEquals(if(local).35f else 0f,v.life.trailDebris,.00001f)
            assertEquals(if(local)1 else 0,v.life.journal.counts[VillageEvent.FLOOD_TRAIL]?:0)
        }
    }
    @Test fun consumptionAndRationingUseActualStock() {
        for(e in listOf(VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL,VillageEvent.DRINK_JAR,VillageEvent.DRINK_RIVER)) {
            val(w,v)=fixture();v.ecology.food=3
            v.life.person(id).apply{hunger=.9f;thirst=.9f}
            val goal=if(e==VillageEvent.DRINK_RIVER)v.ecology.bank else w.depot
            assign(w,v,e,goal)
            assertEquals(0,v.life.person(id).meals);assertEquals(0,v.life.person(id).drinks)
            until(v,e)
            when(e) {
                VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL->{
                    assertEquals(2,v.ecology.food);assertEquals(1,v.life.foodEaten)
                    assertEquals(if(e==VillageEvent.RATION_MEAL).48f else .22f,v.life.person(id).hunger,.0001f)
                }
                else->{
                    assertEquals(1,v.life.person(id).drinks)
                    assertEquals(.15f,v.life.person(id).thirst,.0001f)
                    assertEquals(if(e==VillageEvent.DRINK_JAR).75f else 1f,v.life.water,.0001f)
                }
            }
        }
    }
    @Test fun hungryGathererKeepsWorkingWhenStoredFoodIsUnsafe() {
        val(w,v)=fixture()
        val source=v.sources.single()
        v.residents[id]=source
        v.ecology.food=1;v.life.person(id).hunger=.95f
        w.wildlife.add("MammothAdult_1",WildlifeWorld.Species.ADULT,w.depot,.025f)
        assertTrue(w.dangerous(w.depot));assertFalse(w.dangerous(source))
        assign(w,v,VillageEvent.FORAGE_BERRIES,source)
        val gathering=v.jobs.getValue(id)
        repeat(10){v.life.advance(.1f)}
        assertSame(gathering,v.jobs[id])
        assertEquals(0,v.life.foodEaten)
    }
    @Test fun safeStoredFoodCanInterruptHungryWorkers() {
        val(w,v)=fixture()
        v.residents[id]=v.sources.single()
        v.ecology.food=1;v.life.person(id).hunger=.95f
        assign(w,v,VillageEvent.FORAGE_BERRIES,v.sources.single())
        v.life.advance(.1f)
        assertNotEquals(VillageEvent.FORAGE_BERRIES,v.jobs[id]?.event)
        assertEquals(1,v.ecology.food)
        assertEquals(0,v.life.foodEaten)
    }
    @Test fun safeWildFoodCanInterruptHungryChoresWithoutStock() {
        val(w,v)=fixture()
        val source=v.sources.single()
        v.residents[id]=source;v.life.person(id).hunger=.95f
        assign(w,v,VillageEvent.COLLECT_BRANCH,source)
        v.life.advance(.1f)
        assertNull(v.jobs[id])
        assertEquals(0,v.life.foodEaten)
        w.advance(2f);v.respond(id,source,.1f)
        assertEquals(VillageEvent.FORAGE_BERRIES,v.jobs[id]?.event)
        tick(v,90)
        assertEquals(1,v.life.foodEaten)
        assertTrue(v.life.person(id).hunger<.3f)
    }
    @Test fun hungryTravellerReconsidersDistantRestWhenWildFoodIsAvailable() {
        val(w,v)=fixture()
        v.residents[id]=v.sources.single()
        v.life.person(id).apply{hunger=.95f;fatigue=.6f}
        assign(w,v,VillageEvent.REST_SHADE,Point(.2f,.2f))
        assertTrue(v.jobs.getValue(id).path.isNotEmpty())
        v.life.advance(.1f)
        assertNull(v.jobs[id])
        assertEquals(0,v.life.foodEaten)
        assertTrue(v.life.person(id).fatigue>.59f)
    }
    @Test fun hungryChoreContinuesIfNoWildFoodIsAvailable() {
        val(w,v)=fixture()
        v.residents[id]=v.sources.single();v.life.person(id).hunger=.95f
        v.life.patches.single().apply{berries=0f;resting=true}
        assign(w,v,VillageEvent.COLLECT_BRANCH,v.sources.single())
        val chore=v.jobs.getValue(id)
        v.life.advance(.1f)
        assertSame(chore,v.jobs[id])
        assertEquals(0,v.life.foodEaten)
    }
    @Test fun hearthWorkersCanDrinkBeforeReturningToDuty() {
        for(number in listOf(3,6)) {
            val(w,v)=fixture();val worker="PrimitiveResident_$number"
            v.life.reserveHearthBuilders=true
            val p=v.life.person(worker)
            p.hunger=.1f;p.thirst=.95f;p.fatigue=.1f
            var at=w.depot
            repeat(80) {
                w.advance(.1f)
                at=v.respond(worker,at,.1f)?.point?:at
            }
            assertEquals(worker,1,p.drinks)
            assertTrue(worker,p.thirst<.25f)
            assertNull(v.respond(worker,at,.1f))
        }
    }
    @Test fun blockedDepotStillAllowsEatingFreshlyGatheredFood() {
        val(w,v)=fixture();val source=v.sources.single()
        v.residents[id]=source;v.life.person(id).hunger=.95f;v.ecology.food=1
        w.wildlife.add("MammothAdult_1",WildlifeWorld.Species.ADULT,w.depot,.025f)
        assign(w,v,VillageEvent.FORAGE_BERRIES,source)
        tick(v,41)
        assertEquals(1,v.life.foodProduced)
        assertEquals(0,v.life.foodEaten)
        assertEquals(1,v.foodBundles.size)
        tick(v,60)
        assertEquals(1,v.life.foodEaten)
        assertEquals(1,v.life.person(id).meals)
        assertTrue(v.life.person(id).hunger<.4f)
        assertTrue(v.foodBundles.isEmpty())
        assertEquals(1,v.ecology.food)
    }
    @Test fun interruptedLocalMealLeavesRealRecoverableFood() {
        val(w,v)=fixture();val source=v.sources.single()
        v.residents[id]=source;v.life.person(id).hunger=.95f
        w.wildlife.add("MammothAdult_1",WildlifeWorld.Species.ADULT,w.depot,.025f)
        assign(w,v,VillageEvent.FORAGE_BERRIES,source)
        tick(v,41)
        v.interrupt(id,source)
        assertEquals(0,v.life.foodEaten)
        assertEquals(1,v.life.foodProduced)
        assertEquals(source,v.foodBundles.single().point)
        assertNull(v.foodBundles.single().carrier)
        assertEquals(0,v.ecology.food)
        w.advance(2f)
        v.respond(id,source,.1f)
        tick(v,80)
        assertEquals(1,v.life.foodEaten)
        assertTrue(v.foodBundles.isEmpty())
    }
    private fun groundMeal():Pair<LocalInterventionWorld,LivingVillage> {
        val(w,v)=fixture()
        val at=Point(-.1794695f,0f)
        v.residents[id]=at
        w.wildlife.add(id,WildlifeWorld.Species.RESIDENT,at,.003f)
        v.life.person(id).hunger=.95f
        v.foodBundles+=LivingVillage.FoodBundle(Point(-.19641826f,-.0069999993f))
        v.life.foodBaseline=1
        v.jobs[id]=requireNotNull(v.life.choose(id,at))
        assertEquals(VillageEvent.EAT_FORAGE,v.jobs.getValue(id).event)
        assertFalse(v.jobs.getValue(id).carrying)
        return w to v
    }
    @Test fun groundMealAtNativeGridEdgeFinishesOnce() {
        val(_,v)=groundMeal()
        tick(v,65)
        assertEquals(1,v.life.person(id).meals)
        assertEquals(1,v.life.foodEaten)
        assertTrue(v.life.person(id).hunger<.3f)
        assertTrue(v.foodBundles.isEmpty())
        tick(v,50)
        assertEquals(1,v.life.foodEaten)
    }
    @Test fun groundFoodIsPickedUpBeforeEatingAndDroppedOnInterruption() {
        val(w,v)=groundMeal()
        val bundle=v.foodBundles.single()
        tick(v,5)
        assertTrue(v.jobs.getValue(id).carrying)
        assertSame(bundle,v.jobs.getValue(id).food)
        assertEquals(v.residents.getValue(id),bundle.point)
        assertEquals(0,v.life.foodEaten)
        tick(v,25)
        assertEquals(0,v.life.person(id).meals)
        val at=v.residents.getValue(id)
        v.interrupt(id,at)
        assertNull(bundle.carrier)
        assertEquals(at,bundle.point)
        assertEquals(at,v.life.cargoMemory[id])
        assertEquals(v.life.foodBaseline,v.ecology.food+v.foodBundles.size+v.life.foodEaten)
        w.advance(2f)
        v.respond(id,at,.1f)
        tick(v,50)
        assertEquals(1,v.life.foodEaten)
        assertTrue(v.foodBundles.isEmpty())
    }
    @Test fun missingReservedFoodCancelsBeforeEating() {
        val(_,v)=groundMeal()
        v.foodBundles.clear();v.life.foodLost=1
        tick(v,5)
        assertNull(v.jobs[id])
        assertEquals(0,v.life.person(id).meals)
        assertEquals(0,v.life.foodEaten)
    }
    @Test fun stockMealTakesOnePhysicalPortionOnArrival() {
        for(e in listOf(VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL)) {
            val(w,v)=fixture()
            v.ecology.food=1;v.life.foodBaseline=1
            v.life.person(id).hunger=.9f
            assign(w,v,e,w.depot)
            tick(v)
            assertEquals(0,v.ecology.food)
            val b=v.foodBundles.single()
            assertEquals(id,b.carrier);assertEquals(w.depot,b.point)
            assertSame(b,v.jobs.getValue(id).food)
            assertTrue(v.jobs.getValue(id).carrying)
            tick(v,35)
            assertEquals(0,v.life.foodEaten)
            tick(v,10)
            assertEquals(1,v.life.foodEaten)
            assertEquals(1,v.life.person(id).meals)
            assertTrue(v.foodBundles.isEmpty())
            assertEquals(if(e==VillageEvent.RATION_MEAL).48f else .22f,v.life.person(id).hunger,.0001f)
        }
    }
    @Test fun stockMealDoesNotTakeFoodBeforeArrival() {
        val(w,v)=fixture()
        v.ecology.food=1
        v.residents[id]=Point(w.depot.x+.1f,w.depot.z)
        assign(w,v,VillageEvent.EAT_STOCK,w.depot)
        tick(v,10)
        assertEquals(1,v.ecology.food)
        assertTrue(v.foodBundles.isEmpty())
        assertFalse(v.jobs.getValue(id).carrying)
        assertEquals(0f,v.jobs.getValue(id).elapsed,0f)
    }
    @Test fun lastStockPortionHasOneOwnerAndSurvivesInterruption() {
        val(w,v)=fixture()
        v.ecology.food=1;v.life.foodBaseline=1
        v.residents[peer]=w.depot
        for(who in listOf(id,peer)) {
            v.life.person(who).hunger=.9f
            v.jobs[who]=LivingVillage.Job("life_EAT_STOCK",null,mutableListOf(),w.depot,
                event=VillageEvent.EAT_STOCK)
        }
        tick(v)
        assertEquals(0,v.ecology.food)
        assertEquals(id,v.foodBundles.single().carrier)
        assertNull(v.jobs[peer])
        tick(v,10)
        v.interrupt(id,w.depot)
        assertNull(v.foodBundles.single().carrier)
        assertEquals(0,v.life.foodEaten)
        assertEquals(0,v.life.person(peer).meals)
        assertEquals(1,v.ecology.food+v.foodBundles.size+v.life.foodEaten)
    }
    private fun rainCollection():Pair<LocalInterventionWorld,LivingVillage> {
        val(w,v)=fixture()
        v.life.water=0f
        val at=Point(w.depot.x+.07f,w.depot.z)
        v.residents[id]=at
        assertTrue(w.cast(Kind.RAIN,Point(at.x+.075f,at.z),.034f))
        assertFalse(w.dangerous(at))
        assign(w,v,VillageEvent.CATCH_RAIN,at)
        return w to v
    }
    @Test fun collectedRainMustReturnBeforeStockChanges() {
        val(w,v)=rainCollection()
        tick(v,41)
        assertEquals(0f,v.life.water,0f)
        assertTrue(v.jobs.getValue(id).carrying)
        assertTrue(v.residents.getValue(id).distance(w.depot)>.04f)
        w.hazards.clear()
        until(v,VillageEvent.CATCH_RAIN)
        assertTrue(v.residents.getValue(id).distance(w.depot)<.004f)
        assertEquals(.5f,v.life.water,.00001f)
        assertNull(v.jobs[id])
        tick(v,100)
        assertEquals(.5f,v.life.water,.00001f)
        assertEquals(1,v.life.journal.counts[VillageEvent.CATCH_RAIN])
    }
    @Test fun interruptedRainCargoSpillsLocallyWithoutFillingStock() {
        val(w,v)=rainCollection()
        tick(v,41)
        val at=v.residents.getValue(id)
        v.interrupt(id,at)
        assertEquals(0f,v.life.water,0f)
        assertEquals(1,v.life.journal.counts[VillageEvent.SPILL_WATER])
        assertTrue(w.wetSpots.any{it.point.distance(at)<.001f&&it.wet>0f})
        assertNull(v.life.journal.counts[VillageEvent.CATCH_RAIN])
    }
    @Test fun missingRainOrBlockedReturnCannotFillStock() {
        for(blocked in listOf(false,true)) {
            val(w,v)=rainCollection()
            if(blocked)assertTrue(w.cast(Kind.FIRE,w.depot,.034f,radius=.025f))
            else w.hazards.clear()
            tick(v,41)
            assertEquals(0f,v.life.water,0f)
            assertNull(v.jobs[id])
            assertNull(v.life.journal.counts[VillageEvent.CATCH_RAIN])
        }
    }
    @Test fun gatheredFoodMustTravelAndCanBeRecoveredAfterInterruption() {
        val(w,v)=fixture();val patch=v.life.patches.single()
        assign(w,v,VillageEvent.FORAGE_BERRIES,patch.point)
        until(v,VillageEvent.FORAGE_BERRIES)
        assertEquals(0,v.ecology.food);assertEquals(2f,patch.berries,0f)
        assertEquals(1,v.foodBundles.size);assertEquals(1,v.life.foodProduced)
        val at=v.residents.getValue(id);v.interrupt(id,at)
        assertEquals(at,v.foodBundles.single().point);assertNull(v.foodBundles.single().carrier)
        assertEquals(at,v.life.cargoMemory[id])
        v.residents[peer]=Point(.1f,.15f)
        repeat(1800){if(v.ecology.food==0){v.life.clock+=.1f;v.respond(peer,v.residents.getValue(peer),.1f)}}
        assertEquals(1,v.ecology.food);assertTrue(v.foodBundles.isEmpty())
        assertTrue(v.life.cargoMemory.isEmpty())
        assertEquals(1,v.life.journal.counts[VillageEvent.RECOVER_CARGO])
    }
    private fun blockedHarvest(event:VillageEvent):Triple<LocalInterventionWorld,LivingVillage,Point> {
        val(w,v)=fixture()
        val goal=if(event==VillageEvent.CUT_RIPE_CROP)v.ecology.field else v.life.patches.single().point
        if(event==VillageEvent.CUT_RIPE_CROP){v.agriculture=true;v.ecology.crop=.9f}
        if(event==VillageEvent.PICK_SEEDHEADS)w.wetSpots+=WetSpot(goal,0f,1f)
        v.residents[id]=goal
        assertTrue(w.cast(Kind.FIRE,w.depot,.034f,radius=.025f))
        assertTrue(w.dangerous(w.depot));assertFalse(w.dangerous(goal))
        assign(w,v,event,goal)
        return Triple(w,v,goal)
    }
    @Test fun harvestedFoodWaitsAtSafeSourceThenIsDeliveredOnce() {
        for(event in listOf(VillageEvent.FORAGE_BERRIES,VillageEvent.PICK_SEEDHEADS,VillageEvent.CUT_RIPE_CROP)) {
            val(w,v,goal)=blockedHarvest(event)
            tick(v,41)
            assertEquals(event.name,1,v.life.foodProduced)
            assertEquals(0,v.ecology.food)
            assertEquals(1,v.foodBundles.size)
            val bundle=v.foodBundles.single()
            assertEquals(goal,bundle.point);assertNull(bundle.carrier)
            assertNull(v.jobs[id]);assertEquals(1,v.life.journal.counts[event])
            when(event) {
                VillageEvent.FORAGE_BERRIES->assertEquals(2f,v.life.patches.single().berries,0f)
                VillageEvent.PICK_SEEDHEADS->assertEquals(.5f,w.wetSpots.single().growth,.0001f)
                else->assertEquals(.66f,v.ecology.crop,.0001f)
            }
            assertEquals(v.life.foodBaseline+v.life.foodProduced,v.ecology.food+v.foodBundles.size)
            // 仓库安全后沿现有拾取/搬运链处理同一实物，不直接补库存。
            w.hazards.clear()
            val pickup=requireNotNull(v.life.choose(peer,v.residents.getValue(peer)))
            assertEquals("foodPickup",pickup.kind);assertSame(bundle,pickup.food)
            v.jobs[peer]=pickup
            tick(v)
            assertEquals(0,v.ecology.food)
            repeat(1800){if(v.ecology.food==0)tick(v)}
            assertEquals(1,v.ecology.food);assertTrue(v.foodBundles.isEmpty())
            assertEquals(1,v.life.foodProduced);assertEquals(1,v.harvests)
            tick(v,30)
            assertEquals(1,v.ecology.food);assertEquals(1,v.harvests)
        }
    }
    @Test fun harvestAtCapacityLeavesSourcesUnchanged() {
        for(event in listOf(VillageEvent.FORAGE_BERRIES,VillageEvent.PICK_SEEDHEADS,VillageEvent.CUT_RIPE_CROP)) {
            val(w,v,_)=blockedHarvest(event)
            v.ecology.food=v.ecology.foodCapacity;v.life.foodBaseline=v.ecology.food
            tick(v,41)
            assertEquals(0,v.life.foodProduced);assertTrue(v.foodBundles.isEmpty())
            assertNull(v.life.journal.counts[event])
            assertEquals(v.ecology.foodCapacity,v.ecology.food)
            when(event) {
                VillageEvent.FORAGE_BERRIES->assertEquals(3f,v.life.patches.single().berries,0f)
                VillageEvent.PICK_SEEDHEADS->assertEquals(1f,w.wetSpots.single().growth,0f)
                else->assertEquals(.9f,v.ecology.crop,0f)
            }
        }
    }
    @Test fun incompleteHarvestCannotCreateFoodAtABlockedDepot() {
        val(_,v,_)=blockedHarvest(VillageEvent.FORAGE_BERRIES)
        tick(v,35)
        assertEquals(0,v.life.foodProduced);assertTrue(v.foodBundles.isEmpty())
        assertEquals(3f,v.life.patches.single().berries,0f)
        assertNull(v.life.journal.counts[VillageEvent.FORAGE_BERRIES])
    }
    @Test fun deliveryChangesNeedsOnlyAtTheRecipientsPosition() {
        for(e in listOf(VillageEvent.SHARE_MEAL,VillageEvent.REPAY_HELP,VillageEvent.INVITE_MEAL,VillageEvent.SHARE_WATER)) {
            val(w,v)=fixture();v.ecology.food=4
            v.life.person(peer).apply{hunger=.9f;thirst=.9f;lonely=.9f}
            v.life.debts["$id|$peer"]=2
            assign(w,v,e,w.depot,peer)
            tick(v,45)
            assertTrue(v.jobs.getValue(id).carrying)
            assertEquals(.9f,v.life.person(peer).hunger,0f)
            assertEquals(.9f,v.life.person(peer).thirst,0f)
            assertEquals(if(e==VillageEvent.SHARE_WATER)4 else 3,v.ecology.food)
            // 收件人在运送过程中继续移动，送饭者必须重算路线。
            v.residents[peer]=Point(.2f,.2f)
            until(v,e)
            assertTrue(v.residents.getValue(id).distance(v.residents.getValue(peer))<.023f)
            if(e==VillageEvent.SHARE_WATER){
                assertEquals(.2f,v.life.person(peer).thirst,.0001f);assertEquals(.6f,v.life.water,.0001f)
            }else{
                assertEquals(.2f,v.life.person(peer).hunger,.0001f);assertEquals(1,v.life.foodEaten)
                assertTrue(v.foodBundles.isEmpty())
            }
            if(e==VillageEvent.REPAY_HELP)assertEquals(1,v.life.debts["$id|$peer"])
            else assertEquals(1,v.life.debts["$peer|$id"])
        }
    }
    @Test fun guidesWaitForBothPeopleAtTheDestination() {
        for(e in listOf(VillageEvent.ESCORT_AFRAID,VillageEvent.ESCORT_WATER,VillageEvent.TEACH_FARM,
                       VillageEvent.WELCOME_GUEST,VillageEvent.REUNITE_HOME)) {
            val(w,v)=fixture();v.residents[id]=Point(.1f,.15f)
            v.life.person(id).farmSkill=.8f
            v.life.person(peer).apply{fear=.8f;thirst=.8f;farmSkill=.05f;warned=true}
            assign(w,v,e,v.residents.getValue(peer),peer)
            tick(v,45)
            assertEquals("life_guiding",v.jobs.getValue(id).kind)
            assertEquals("life_follow",v.jobs.getValue(peer).kind)
            assertFalse(v.jobs.getValue(id).carrying)
            assertEquals(.8f,v.life.person(peer).fear,0f)
            val goal=v.jobs.getValue(id).goal
            until(v,e)
            assertTrue(v.residents.getValue(id).distance(goal)<.014f)
            assertTrue(v.residents.getValue(peer).distance(goal)<.028f)
            assertFalse(v.jobs.containsKey(peer))
            when(e){
                VillageEvent.TEACH_FARM->assertEquals(.25f,v.life.person(peer).farmSkill,.0001f)
                VillageEvent.WELCOME_GUEST->assertEquals("Shelter_0",v.life.person(peer).hostedBy)
                VillageEvent.REUNITE_HOME->assertFalse(v.life.person(peer).warned)
                VillageEvent.ESCORT_WATER->assertEquals(.3f,v.life.person(peer).thirst,.0001f)
                else->assertEquals(.25f,v.life.person(peer).fear,.0001f)
            }
        }
    }
    @Test fun jarsAndBuildingMaterialsHaveTwoPhysicalTravelStages() {
        for(e in listOf(VillageEvent.FILL_JAR,VillageEvent.WATER_FIELD,VillageEvent.DAMP_ROOF,VillageEvent.BRACE_HOUSE)) {
            val(w,v)=fixture();v.life.fieldWater=0f;v.life.homes["Shelter_0"]=VillageLife.Home()
            w.dropWood(w.depot);w.woods.single().phase=WoodPhase.STACKED
            val goal=if(e in listOf(VillageEvent.FILL_JAR,VillageEvent.WATER_FIELD))v.ecology.bank else w.depot
            assign(w,v,e,goal,house=if(e in listOf(VillageEvent.DAMP_ROOF,VillageEvent.BRACE_HOUSE))"Shelter_0"else null)
            repeat(1000){if(v.jobs[id]?.carrying!=true)tick(v)}
            assertTrue(v.jobs.getValue(id).carrying)
            assertEquals(0f,v.life.fieldWater,0f);assertEquals(0f,v.life.homes.getValue("Shelter_0").damp,0f)
            val waterAtPickup=v.life.water
            until(v,e)
            when(e){
                VillageEvent.FILL_JAR->assertEquals(1.8f,v.life.water,.0001f)
                VillageEvent.WATER_FIELD->assertEquals(.4f,v.life.fieldWater,.0001f)
                VillageEvent.DAMP_ROOF->{assertEquals(.65f,waterAtPickup,.0001f);assertEquals(1f,v.life.homes.getValue("Shelter_0").damp,0f)}
                else->{assertTrue(w.woods.isEmpty());assertEquals(1f,v.life.homes.getValue("Shelter_0").brace,0f)}
            }
        }
    }
    @Test fun exhaustedWorkersReleaseCargoAndRememberIt() {
        val(w,v)=fixture();val b=LivingVillage.FoodBundle(w.depot,id)
        v.foodBundles+=b
        v.jobs[id]=LivingVillage.Job("foodDelivery",null,mutableListOf(),w.depot,carrying=true,food=b)
        v.life.person(id).fatigue=.94f
        v.life.advance(.1f)
        assertFalse(v.jobs.containsKey(id));assertNull(b.carrier);assertTrue(v.life.person(id).interrupted)
        assertEquals(w.depot,v.life.cargoMemory[id])
        assertEquals(1,v.life.journal.counts[VillageEvent.STOP_EXHAUSTED])
    }
    @Test fun depletedResourcesRecoverAndStorageDoesNotCreateFood() {
        val(w,v)=fixture();val p=v.life.patches.single()
        p.berries=0f;p.resting=true
        repeat(410){v.life.advance(1f)}
        assertEquals(2f,p.berries,.0001f);assertFalse(p.resting)
        assertEquals(1,v.life.journal.counts[VillageEvent.BERRIES_REGROW])
        v.ecology.food=2;v.life.dampGrain=.9f
        v.life.advance(.1f)
        assertEquals(1,v.ecology.food);assertEquals(1,v.life.foodLost);assertEquals(1,v.life.spoiled)
        assertEquals(0,v.life.foodProduced)
        v.life.bankReeds=0f;repeat(600){v.life.advance(1f)}
        assertEquals(1.2f,v.life.bankReeds,.0001f)
        assertTrue(w.woods.isEmpty())
    }
    @Test fun snapshotsAreIndependentAndInvalidStateCannotPartiallyApply() {
        val(_,v)=fixture();val life=v.life
        life.clock=100f;life.water=.7f;life.seeds=2
        life.debts["$id|$peer"]=3;life.cargoMemory[id]=Point(.03f,.12f)
        life.journal.record(VillageEvent.FILL_JAR,id,99f,"water")
        val saved=life.snapshot();life.person(id).hunger=.9f
        assertEquals(.18f,saved.people.getValue(id).hunger,0f)
        val before=life.snapshot()
        try {life.restore(saved.copy(floats=saved.floats+("water" to -1f)));fail("negative water accepted")}
        catch(_:IllegalArgumentException){}
        assertEquals(before,life.snapshot())
        life.restore(saved)
        assertEquals(saved,life.snapshot());assertTrue(v.jobs.isEmpty())
    }
    @Test fun urgentNeedsPrecedeFieldWorkAndNoElapsedTimeUnlocksAgriculture() {
        val(w,v)=fixture();v.life.person(id).apply{thirst=.9f;hunger=.1f}
        assertEquals(VillageEvent.DRINK_JAR,v.life.choose(id,w.depot)?.event)
        v.life.advance(650f)
        assertFalse(v.agriculture);assertEquals(0f,v.ecology.cultivation,0f)
    }
}
