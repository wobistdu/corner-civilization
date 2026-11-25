package com.example.cornercivilization.p37f776dc

import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** 场景只设处境与资源；主角任务由真实choose选择，不直接调用work或emit。 */
@RunWith(Parameterized::class)
internal class VillageEventReachabilityTest(private val event:VillageEvent) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name="{0}")
        fun cases()=VillageEvent.entries.map{arrayOf(it)}
    }
    private val id="PrimitiveResident_0"
    private val peer="PrimitiveResident_4"
    private val third="PrimitiveResident_7"
    private fun scenario():Pair<LocalInterventionWorld,LivingVillage> {
        val nav=if(event==VillageEvent.WAIT_ROUTE)LocalNavigation({_,_->.034f},{x,_->x in -.04f..-.02f})
            else LocalNavigation({_,_->.034f})
        val w=LocalInterventionWorld(nav);val v=LivingVillage(w);w.village=v
        v.addHouse("Shelter_0",Point(.04f,.1f),.018f,Point(.018f,.1f))
        v.addHouse("Shelter_1",Point(.15f,.12f),.018f,Point(.126f,.12f))
        v.addHouse("Shelter_2",Point(.24f,.13f),.018f,Point(.216f,.13f))
        v.sources+=Point(-.06f,.13f);v.enableExpansion();v.life.enable()
        v.life.reserveHearthBuilders=false
        v.life.water=1f;v.life.bankReeds=0f;v.life.reeds=3;v.ecology.food=8
        v.life.safeTrail=w.depot;v.life.hearthFuel=1f
        v.life.patches.forEach{it.berries=0f;it.resting=true;it.branches=0}
        v.life.advance(0f);v.life.homes.values.forEach{it.inspected=1f}
        v.residents[id]=w.depot;v.residents[peer]=Point(.1f,.15f);v.residents[third]=Point(.23f,.16f)
        for(who in listOf(id,peer,third))v.life.person(who).apply{hunger=.1f;thirst=.1f;fatigue=.1f;farmSkill=.1f;buildSkill=.1f}
        val l=v.life;val p=l.person(id);val other=l.person(peer);val h=v.houses.getValue("Shelter_0")
        fun farm(){v.agriculture=true;v.ecology.crop=.5f;l.seeds=2}
        fun timber(){w.dropWood(w.depot);w.woods.last().phase=WoodPhase.STACKED}
        fun damageOther(){v.houses.getValue("Shelter_1").apply{health=0f;ruined=true;safeSeconds=0f}}
        fun animal(name:String,species:Species,at:Point,rad:Float=.008f)=w.wildlife.add(name,species,at,rad)
        fun builder(who:String,house:String) {
            val home=v.houses.getValue(house)
            home.apply{health=50f;safeSeconds=4f;materials=1}
            val at=home.workPoint;v.residents[who]=at
            // 已经在施工的邻居继续走正常施工入口；主角仍由choose选择协作。
            v.jobs[who]=LivingVillage.Job("build",house,mutableListOf(),at)
        }
        when(event) {
            VillageEvent.GREET_NEIGHBOR->{l.clock=30f;p.lonely=.1f;v.residents[peer]=Point(w.depot.x+.04f,w.depot.z)}
            VillageEvent.CHAT_TOGETHER->{l.clock=30f;p.lonely=.5f;v.residents[peer]=Point(w.depot.x+.02f,w.depot.z)}
            VillageEvent.STRETCH_BREAK->{l.clock=30f;p.fatigue=.2f}
            VillageEvent.EAT_STOCK->p.hunger=.8f
            VillageEvent.EAT_FORAGE->{
                p.hunger=.9f;v.ecology.food=0
                v.foodBundles+=LivingVillage.FoodBundle(w.depot)
                l.foodBaseline=1
            }
            VillageEvent.RATION_MEAL->{p.hunger=.8f;l.ration=true;v.ecology.food=2}
            VillageEvent.KEEP_LAST_MEAL->{v.ecology.food=1;other.hunger=.95f}
            VillageEvent.DRINK_RIVER->{p.thirst=.9f;l.water=0f}
            VillageEvent.DRINK_JAR->p.thirst=.9f
            VillageEvent.REST_SHADE->p.fatigue=.65f
            VillageEvent.SLEEP_HOME->p.fatigue=.9f
            VillageEvent.SLEEP_LONGHOUSE->{p.fatigue=.9f;h.health=0f;h.ruined=true;v.houses.getValue("Longhouse").available=true}
            VillageEvent.DRY_CLOTHES->p.wet=.7f
            VillageEvent.WARM_AT_HEARTH->{p.wet=.7f;l.hearthWarm=true}
            VillageEvent.STOP_EXHAUSTED->l.homes.getValue(h.id).inspected=0f
            VillageEvent.RESUME_RECOVERED->p.interrupted=true
            VillageEvent.FORAGE_BERRIES->{v.ecology.food=0;l.patches.single().apply{berries=2f;resting=false}}
            VillageEvent.PICK_SEEDHEADS->{v.ecology.food=0;w.wetSpots+=WetSpot(Point(-.15f,.12f),.5f,.9f)}
            VillageEvent.CUT_RIPE_CROP->{farm();v.ecology.food=0;v.ecology.crop=.9f}
            VillageEvent.DRY_WET_GRAIN->l.dampGrain=.6f
            VillageEvent.PRESERVE_GRAIN->l.hearthWarm=true
            VillageEvent.COMPOST_SPOILED->l.spoiled=1
            VillageEvent.TURN_STOCK->l.dampGrain=.2f
            VillageEvent.RESCUE_STOCK->w.cast(Kind.FIRE,Point(w.depot.x-.09f,w.depot.z),.034f,radius=.04f)
            VillageEvent.STORE_GRANARY->v.houses.getValue("Granary").available=true
            VillageEvent.AGREE_RATIONS->{v.ecology.food=2;other.hunger=.57f;l.person(third).hunger=.57f}
            VillageEvent.COOK_TOGETHER->{l.hearthWarm=true;p.hunger=.45f;other.hunger=.45f;v.residents[peer]=Point(w.depot.x+.009f,w.depot.z)}
            VillageEvent.RESERVE_SEEDS->{farm();l.seeds=0}
            VillageEvent.FILL_JAR->l.water=0f
            VillageEvent.SHARE_WATER->{other.thirst=.9f;other.fatigue=.5f}
            VillageEvent.WATER_FIELD->{farm();l.fieldWater=0f}
            VillageEvent.WASH_MUD->p.mud=.8f
            VillageEvent.CATCH_RAIN->{l.water=.55f;w.cast(Kind.RAIN,Point(w.depot.x+.065f,w.depot.z),.034f)}
            VillageEvent.SPILL_WATER->l.water=0f
            VillageEvent.DAMP_ROOF->w.cast(Kind.FIRE,Point(h.workPoint.x+.09f,h.workPoint.z),.034f,radius=.025f)
            VillageEvent.COOL_ASH->{v.ecology.scorch=Point(.12f,.2f);v.ecology.scarAge=25f}
            VillageEvent.WATER_SEEDLINGS->{farm();v.ecology.food=2;l.cropCycles=1;v.ecology.crop=.2f;l.fieldWater=.3f;l.seeds=0}
            VillageEvent.CLEAR_BANK->l.bankDebris=.8f
            VillageEvent.ALTERNATE_BANK->{p.thirst=.9f;l.water=0f;l.bankDebris=.8f}
            VillageEvent.ESCORT_WATER->{other.fear=.45f;other.thirst=.8f;v.houses.values.forEach{it.available=false}}
            VillageEvent.SOW_SEEDS->{farm();v.ecology.crop=.15f;l.fieldWater=.5f}
            VillageEvent.PULL_WEEDS->{farm();l.weeds=.8f;l.fieldWater=.5f}
            VillageEvent.MULCH_FIELD->{farm();l.fieldWater=.19f;l.water=1f}
            VillageEvent.LOOSEN_SOIL->{farm();l.soilHard=.8f;l.fieldWater=.5f}
            VillageEvent.DRAIN_FIELD->{farm();l.fieldWater=.85f}
            VillageEvent.STAKE_CROP->{farm();l.trampled=.7f;l.fieldWater=.5f}
            VillageEvent.PICK_PESTS->{farm();l.pests=.8f;l.fieldWater=.5f}
            VillageEvent.REPLANT_GAP->{farm();l.trampled=.2f;l.fieldWater=.5f}
            VillageEvent.CHECK_FENCE->{farm();v.houses.getValue("FieldFence").available=true;l.fenceWear=.4f;l.fieldWater=.5f}
            VillageEvent.MEND_FENCE->{farm();v.houses.getValue("FieldFence").available=true;l.fenceWear=.8f;l.fieldWater=.5f;timber()}
            VillageEvent.REST_SOIL->{farm();l.soilFertility=.2f;l.fieldWater=.5f}
            VillageEvent.SAVE_OVERWATERED->{farm();l.fieldWater=.98f;v.ecology.crop=.3f}
            VillageEvent.PATCH_ROOF->l.homes.getValue(h.id).leak=.8f
            VillageEvent.BRACE_HOUSE->{h.animalHits=1;l.homes.getValue(h.id).brace=0f;timber()}
            VillageEvent.CLEAR_TRAIL->l.trailDebris=.8f
            VillageEvent.FEED_HEARTH->{l.hearthWarm=true;l.hearthFuel=0f;timber()}
            VillageEvent.COVER_WOOD->{timber();w.cast(Kind.RAIN,Point(.2f,-.2f),.034f)}
            VillageEvent.DRY_WOOD->{timber();l.woodWet=.8f}
            VillageEvent.SALVAGE_TIMBER->{damageOther();l.homes.getValue("Shelter_1").salvage=1}
            VillageEvent.MAKE_TOOL->timber()
            VillageEvent.MEND_TOOL->{p.tool=.1f;l.spareTools=1}
            VillageEvent.BUILD_TOGETHER->{builder(peer,"Shelter_1");builder(third,"Shelter_1");p.buildSkill=.5f;other.buildSkill=.4f;l.person(third).buildSkill=.4f}
            VillageEvent.INSPECT_HOME->l.homes.getValue(h.id).inspected=0f
            VillageEvent.RETURN_HOST_SPACE->p.hostedBy="Shelter_1"
            VillageEvent.SHARE_MEAL->other.hunger=.9f
            VillageEvent.COMFORT_FEAR->{other.fear=.7f;v.houses.values.forEach{it.available=false}}
            VillageEvent.THANK_HELPER->l.debts["$id|$peer"]=2
            VillageEvent.REPAY_HELP->{l.debts["$id|$peer"]=2;l.bonds["$id|$peer"]=.5f;other.hunger=.45f}
            VillageEvent.TEACH_FARM->{farm();l.fieldWater=.5f;p.farmSkill=.8f}
            VillageEvent.TEACH_BUILD->{builder(peer,"Shelter_1");builder(third,"Shelter_1");p.buildSkill=.8f}
            VillageEvent.RELAY_WARNING->{p.warned=true;animal("Sabertooth_0",Species.TIGER,Point(w.depot.x+.12f,w.depot.z))
                l.person(third).watch=.9f;v.residents[third]=v.ecology.refuge}
            VillageEvent.VISIT_DISPLACED->{other.hostedBy="Shelter_2";other.lonely=.5f}
            VillageEvent.INVITE_MEAL->{p.hunger=.45f;other.lonely=.8f}
            VillageEvent.HARVEST_TOGETHER->{farm();v.ecology.food=0;v.ecology.crop=.9f;v.residents[peer]=v.ecology.field
                v.jobs[peer]=LivingVillage.Job("life_CUT_RIPE_CROP",null,mutableListOf(),v.ecology.field,event=VillageEvent.CUT_RIPE_CROP)}
            VillageEvent.WELCOME_GUEST->damageOther()
            VillageEvent.KEEP_COMPANY->{other.fatigue=.65f;other.lonely=.5f}
            VillageEvent.RECALL_WORKER->{animal("Sabertooth_0",Species.TIGER,Point(.17f,.15f));other.warned=false;v.residents[peer]=Point(.107f,.15f)}
            VillageEvent.SHELTER_FROM_RAIN->{p.wet=.3f;w.cast(Kind.RAIN,Point(.2f,-.2f),.034f)}
            VillageEvent.ESCORT_AFRAID->other.fear=.45f
            VillageEvent.GUIDE_CARRIER->{l.safeTrail=null;v.residents[peer]=Point(w.depot.x+.02f,w.depot.z)
                val b=LivingVillage.FoodBundle(v.residents.getValue(peer),peer);v.foodBundles+=b
                v.jobs[peer]=LivingVillage.Job("foodDelivery",null,mutableListOf(),w.depot,food=b,carrying=true)}
            VillageEvent.WAIT_ROUTE->{v.residents[id]=Point(.25f,-.1f);l.patches.single().apply{berries=2f;resting=false};v.ecology.food=0;h.available=false;v.houses.getValue("Shelter_1").available=false;v.houses.getValue("Shelter_2").available=false}
            VillageEvent.RECOVER_CARGO->{v.ecology.food=0;val at=Point(-.06f,.18f);v.foodBundles+=LivingVillage.FoodBundle(at);l.cargoMemory[peer]=at}
            VillageEvent.MOVE_GRAIN_DRY->{v.ecology.food=0;v.foodBundles+=LivingVillage.FoodBundle(Point(-.06f,.18f),wet=.7f)}
            VillageEvent.WATCH_TIGER_PATH->animal("Sabertooth_0",Species.TIGER,Point(w.depot.x+.12f,w.depot.z))
            VillageEvent.RELIEVE_WATCH->{other.watch=1f;other.fatigue=.7f;v.residents[peer]=v.ecology.refuge}
            VillageEvent.REUNITE_HOME->other.warned=true
            VillageEvent.MARK_SAFE_TRAIL->l.safeTrail=null
            VillageEvent.REOPEN_WORK->{p.warned=true;other.warned=true;l.person(third).warned=true;l.blockedWork[id]=VillageEvent.PULL_WEEDS.name}
            VillageEvent.BAIT_ELEPHANT->{animal("MammothAdult_0",Species.ADULT,Point(v.ecology.field.x+.065f,v.ecology.field.z))
                w.wetSpots+=WetSpot(Point(-.23f,.1f),.5f,.8f);w.wetSpots+=WetSpot(Point(.28f,.05f),.5f,1f)}
            VillageEvent.LEAD_CALF_WATER,VillageEvent.SHARE_ELEPHANT_WATER->{v.ecology.food=0
                animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f),.012f);animal("MammothCalf",Species.CALF,Point(.23f,.1f),.005f)
                v.animalLife.states["MammothCalf"]=VillageWildlifeLife.State(thirst=.9f)}
            VillageEvent.CALF_REJOIN->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.12f,.1f),.012f);animal("MammothCalf",Species.CALF,Point(.27f,.1f),.005f)}
            VillageEvent.ELEPHANT_MUD->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f));w.wetSpots+=WetSpot(Point(.23f,.1f),.9f)
                v.animalLife.states["MammothAdult_0"]=VillageWildlifeLife.State(fatigue=.5f)}
            VillageEvent.ELEPHANT_SHADE->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f))
                v.animalLife.states["MammothAdult_0"]=VillageWildlifeLife.State(fatigue=.8f)}
            VillageEvent.TIGER_DRINK->{v.ecology.food=0;animal("Sabertooth_0",Species.TIGER,Point(.2f,.1f))
                v.animalLife.states["Sabertooth_0"]=VillageWildlifeLife.State(thirst=.9f)}
            VillageEvent.TIGER_AVOID_FENCE->{v.ecology.food=0;v.houses.getValue("FieldFence").available=true
                animal("Sabertooth_0",Species.TIGER,Point(.03f,.27f)).hunger=.8f;animal("MammothCalf",Species.CALF,Point(-.2f,.27f),.004f)}
            VillageEvent.TIGER_YIELD->{v.ecology.food=0;animal("Sabertooth_0",Species.TIGER,Point(.2f,.1f)).rest=10f
                animal("Sabertooth_1",Species.TIGER,Point(.225f,.1f)).hunger=.5f}
            VillageEvent.RETURN_CARCASS_SOIL->{v.ecology.food=0;animal("MammothCalf",Species.CALF,Point(.2f,.1f),.004f).apply{alive=false;health=0f}
                animal("Sabertooth_0",Species.TIGER,Point(.215f,.1f)).apply{kills=1;feeding=.5f}}
            VillageEvent.ELEPHANT_AVOID_GUARD->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f))
                other.warned=true;l.person(third).warned=true;v.residents[peer]=Point(.16f,.15f);v.residents[third]=Point(.24f,.15f)}
            VillageEvent.CALF_REST_PROTECTED->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f),.012f)
                animal("MammothCalf",Species.CALF,Point(.225f,.1f),.005f);v.animalLife.states["MammothCalf"]=VillageWildlifeLife.State(fatigue=.8f)}
            VillageEvent.COLLECT_BRANCH->{h.animalHits=1;h.health=50f;h.safeSeconds=0f;l.patches.single().branches=1}
            VillageEvent.REST_FORAGE_PATCH->{l.patches.single().resting=false}
            VillageEvent.CUT_REEDS->{l.bankReeds=2f;l.reeds=0}
            VillageEvent.BERRIES_REGROW->{l.patches.single().apply{berries=1f;regrowth=.98f}}
            VillageEvent.FLOOD_TRAIL->{w.wetSpots+=WetSpot(v.ecology.field,1f);w.cast(Kind.RAIN,w.depot,.034f)}
            VillageEvent.CLEAN_MUD_TRAIL->l.trailMud=.7f
            VillageEvent.CROP_TRAMPLED->{farm();animal("MammothAdult_0",Species.ADULT,v.ecology.field)}
            VillageEvent.MEADOW_RECOVER->{v.ecology.food=0;animal("MammothAdult_0",Species.ADULT,Point(.2f,.1f))
                w.wetSpots+=WetSpot(Point(.24f,.12f),1f,.2f);w.cast(Kind.RAIN,Point(.24f,.12f),.034f)}
            VillageEvent.COMPOST_SCAR->{l.spoiled=1;v.ecology.scorch=Point(.13f,.2f)}
            VillageEvent.CLEAR_SILT->{farm();l.silt=.7f;l.fieldWater=.5f}
            VillageEvent.BIRDS_EAT_SPILL->{v.foodBundles+=LivingVillage.FoodBundle(Point(.12f,.16f),age=81f)}
            VillageEvent.KEEP_GRASS_REFUGE->{animal("MammothAdult_0",Species.ADULT,Point(v.ecology.field.x+.07f,v.ecology.field.z))
                l.grassBait=1f;w.wetSpots+=WetSpot(Point(-.23f,.1f),.5f,.8f);w.wetSpots+=WetSpot(Point(.28f,.05f),.5f,1f)}
        }
        return w to v
    }
    @Test fun circumstancesSelectTravelAndChangeAConsequence() {
        val(w,v)=scenario();val l=v.life;val before=l.snapshot();val stock=v.ecology.food
        val paths=mutableMapOf<String,Pair<Point,MutableList<Point>>>()
        var bird=Point(.3f,.1f)
        val passive=(event.group==7&&event!=VillageEvent.BAIT_ELEPHANT)||event in setOf(VillageEvent.BERRIES_REGROW,VillageEvent.FLOOD_TRAIL,VillageEvent.CROP_TRAMPLED,VillageEvent.MEADOW_RECOVER,VillageEvent.BIRDS_EAT_SPILL)
        repeat(2400){
            if((l.journal.counts[event]?:0)>0)return@repeat
            w.advance(.1f)
            if(event==VillageEvent.STOP_EXHAUSTED&&v.jobs[id]!=null)l.person(id).fatigue=.95f
            if(!passive){
                w.respond(id,v.residents.getValue(id),.1f,true)?.let{v.residents[id]=it.point}
                for(who in listOf(peer,third))if(v.jobs[who]?.kind in setOf("life_follow","life_reply"))
                    v.respond(who,v.residents.getValue(who),.1f)
                if(event==VillageEvent.SPILL_WATER&&v.jobs[id]?.carrying==true)v.interrupt(id,v.residents.getValue(id))
            }
            w.wildlife.decide(.1f,w)
            w.wildlife.bodies.values.filter{it.alive}.forEach{b->
                w.wildlife.order(b.id)?.point?.let{goal->
                    var route=paths[b.id]?.takeIf{it.first.distance(goal)<.008f}?.second
                    if(route==null){route=w.navigation.path(b.point,goal){w.wildlife.open(b.id,it)}?.toMutableList();if(route!=null)paths[b.id]=goal to route}
                    val path=route?:return@let
                    while(path.isNotEmpty()&&path.first().distance(b.point)<.0025f)path.removeAt(0)
                    val target=path.firstOrNull()?:goal;val d=b.point.distance(target)
                    if(d>.00001f){val travel=minOf(.0021f,d)
                        w.wildlife.move(b.id,Point(b.point.x+(target.x-b.point.x)*travel/d,b.point.z+(target.z-b.point.z)*travel/d)){w.navigation.open(it)}}
                }
            }
            v.animalLife.settle(.1f)
            if(event==VillageEvent.BIRDS_EAT_SPILL)l.birdTarget()?.let{target->
                val d=bird.distance(target);if(d>.002f){val t=minOf(.003f,d);bird=Point(bird.x+(target.x-bird.x)*t/d,bird.z+(target.z-bird.z)*t/d)}
                l.birdAt(bird)
            }
        }
        assertTrue("${event.name}: selected=${v.jobs[id]?.event}, stock=${v.ecology.food}, blocked=${l.blockedWork[id]}",(l.journal.counts[event]?:0)>0)
        // 各事件必须改变对应的需求、资源、关系或路线；日志计数本身不算后果。
        when(event){
            VillageEvent.GREET_NEIGHBOR,VillageEvent.CHAT_TOGETHER->{
                assertEquals(.025f,l.bonds["$id|$peer"]!!,.0001f)
                assertTrue(l.person(id).lonely<before.people.getValue(id).lonely)
                assertTrue(l.person(id).lastSocial>before.floats.getValue("clock"))
            }
            VillageEvent.STRETCH_BREAK->{
                assertTrue(l.person(id).fatigue<before.people.getValue(id).fatigue)
                assertTrue(l.person(id).lastSocial>before.floats.getValue("clock"))
            }
            VillageEvent.EAT_STOCK,VillageEvent.EAT_FORAGE,VillageEvent.RATION_MEAL,VillageEvent.SHARE_MEAL,VillageEvent.REPAY_HELP,VillageEvent.INVITE_MEAL,VillageEvent.COOK_TOGETHER->assertTrue(l.foodEaten>before.ints.getValue("foodEaten"))
            VillageEvent.FORAGE_BERRIES,VillageEvent.PICK_SEEDHEADS,VillageEvent.CUT_RIPE_CROP,VillageEvent.HARVEST_TOGETHER->assertEquals(1,l.foodProduced)
            VillageEvent.KEEP_LAST_MEAL->assertEquals(peer,l.reservedFor)
            VillageEvent.RESERVE_SEEDS->assertTrue(l.seeds>before.ints.getValue("seeds"))
            VillageEvent.STORE_GRANARY->assertEquals(1,l.foodInGranary)
            VillageEvent.RESCUE_STOCK->assertTrue(l.preservation>0f)
            VillageEvent.DRINK_RIVER,VillageEvent.DRINK_JAR,VillageEvent.ALTERNATE_BANK->assertEquals(1,l.person(id).drinks)
            VillageEvent.FILL_JAR,VillageEvent.CATCH_RAIN->assertTrue(l.water>before.floats.getValue("water"))
            VillageEvent.SHARE_WATER,VillageEvent.ESCORT_WATER->assertEquals(1,l.person(peer).drinks)
            VillageEvent.DAMP_ROOF->assertTrue(l.homes.getValue("Shelter_0").damp>.9f)
            VillageEvent.SPILL_WATER->assertTrue(w.wetSpots.any{it.wet>0f})
            VillageEvent.WATER_FIELD,VillageEvent.WATER_SEEDLINGS->assertTrue(l.fieldWater>.3f)
            VillageEvent.LEAD_CALF_WATER->assertEquals(1,v.animalLife.states.getValue("MammothAdult_0").drinks)
            VillageEvent.SHARE_ELEPHANT_WATER->assertEquals(1,v.animalLife.states.getValue("MammothCalf").drinks)
            VillageEvent.CALF_REJOIN->assertEquals(1,v.animalLife.states.getValue("MammothCalf").reunions)
            VillageEvent.ELEPHANT_MUD->assertTrue(v.animalLife.states.getValue("MammothAdult_0").coat>.8f)
            VillageEvent.TIGER_DRINK->assertEquals(1,v.animalLife.states.getValue("Sabertooth_0").drinks)
            VillageEvent.TIGER_AVOID_FENCE->assertEquals(1f,v.animalLife.states.getValue("Sabertooth_0").fenceMemory,0f)
            VillageEvent.RETURN_CARCASS_SOIL->assertTrue(w.wetSpots.any{it.growth>.2f})
            VillageEvent.ELEPHANT_AVOID_GUARD,VillageEvent.KEEP_GRASS_REFUGE->assertTrue(v.ecology.recoveryUntil>w.seconds)
            VillageEvent.TIGER_YIELD->assertTrue(w.wildlife.bodies.getValue("Sabertooth_0").rest>=5f)
            VillageEvent.ELEPHANT_SHADE,VillageEvent.CALF_REST_PROTECTED->assertTrue(v.animalLife.states.values.any{it.fatigue<.5f})
            VillageEvent.CROP_TRAMPLED->assertTrue(l.trampled>.25f)
            VillageEvent.MEADOW_RECOVER->assertEquals(w.wetSpots.single().point,w.wildlife.bodies.getValue("MammothAdult_0").goal)
            VillageEvent.BIRDS_EAT_SPILL->{assertTrue(v.foodBundles.isEmpty());assertEquals(1,l.foodLost)}
            VillageEvent.BERRIES_REGROW->assertTrue(l.patches.single().berries>=2f)
            VillageEvent.FLOOD_TRAIL->assertTrue(l.trailDebris>.3f)
            VillageEvent.BUILD_TOGETHER->assertTrue(v.houses.getValue("Shelter_1").progress>=4f)
            VillageEvent.RECOVER_CARGO,VillageEvent.MOVE_GRAIN_DRY->assertTrue(v.jobs[id]?.carrying==true&&stock==v.ecology.food)
            VillageEvent.REST_SHADE,VillageEvent.SLEEP_HOME,VillageEvent.SLEEP_LONGHOUSE->assertTrue(l.person(id).fatigue<.3f)
            VillageEvent.DRY_CLOTHES,VillageEvent.SHELTER_FROM_RAIN->assertTrue(l.person(id).wet<.2f)
            VillageEvent.WARM_AT_HEARTH->assertEquals(0f,l.person(id).wet,0f)
            VillageEvent.STOP_EXHAUSTED->{assertTrue(l.person(id).interrupted);assertNull(v.jobs[id])}
            VillageEvent.RESUME_RECOVERED->assertFalse(l.person(id).interrupted)
            VillageEvent.DRY_WET_GRAIN->assertTrue(l.dampGrain<.35f)
            VillageEvent.PRESERVE_GRAIN->assertTrue(l.preservation>.9f)
            VillageEvent.COMPOST_SPOILED,VillageEvent.COMPOST_SCAR->{assertEquals(0,l.spoiled);assertTrue(l.soilFertility>before.floats.getValue("soilFertility"))}
            VillageEvent.TURN_STOCK->assertTrue(l.stockTurn>.9f)
            VillageEvent.AGREE_RATIONS->assertTrue(l.ration)
            VillageEvent.WASH_MUD->assertEquals(0f,l.person(id).mud,0f)
            VillageEvent.COOL_ASH->assertTrue(v.ecology.scarAge>75f)
            VillageEvent.CLEAR_BANK->assertEquals(0f,l.bankDebris,0f)
            VillageEvent.SOW_SEEDS,VillageEvent.REPLANT_GAP->assertEquals(before.ints.getValue("seeds")-1,l.seeds)
            VillageEvent.PULL_WEEDS->assertTrue(l.weeds<.3f)
            VillageEvent.MULCH_FIELD->{assertTrue(l.mulch>.8f);assertEquals(2,l.reeds)}
            VillageEvent.LOOSEN_SOIL->assertTrue(l.soilHard<.3f)
            VillageEvent.DRAIN_FIELD->{assertTrue(l.fieldWater<.5f);assertTrue(l.drainage>.9f)}
            VillageEvent.STAKE_CROP->assertTrue(l.trampled<.25f)
            VillageEvent.PICK_PESTS->assertTrue(l.pests<.3f)
            VillageEvent.CHECK_FENCE->assertEquals(.5f,l.fenceWear,0f)
            VillageEvent.MEND_FENCE->{assertEquals(0f,l.fenceWear,0f);assertTrue(w.woods.isEmpty())}
            VillageEvent.REST_SOIL->assertTrue(l.restingSoil)
            VillageEvent.SAVE_OVERWATERED->{assertEquals(.5f,l.fieldWater,0f);assertEquals(1f,l.drainage,0f)}
            VillageEvent.PATCH_ROOF->{assertEquals(0f,l.homes.getValue("Shelter_0").leak,0f);assertEquals(2,l.reeds)}
            VillageEvent.BRACE_HOUSE->{assertTrue(l.homes.getValue("Shelter_0").brace>.9f);assertTrue(w.woods.isEmpty())}
            VillageEvent.CLEAR_TRAIL->assertEquals(0f,l.trailDebris,0f)
            VillageEvent.FEED_HEARTH->assertTrue(l.hearthFuel>.6f)
            VillageEvent.COVER_WOOD->{assertTrue(l.woodCover>.9f);assertEquals(2,l.reeds)}
            VillageEvent.DRY_WOOD->assertTrue(l.woodWet<.3f)
            VillageEvent.SALVAGE_TIMBER,VillageEvent.COLLECT_BRANCH->assertTrue(w.woods.any{it.phase==WoodPhase.WAITING})
            VillageEvent.MAKE_TOOL->assertEquals(1,l.spareTools)
            VillageEvent.MEND_TOOL->{assertEquals(0,l.spareTools);assertEquals(1f,l.person(id).tool,0f)}
            VillageEvent.INSPECT_HOME->assertEquals(1f,l.homes.getValue("Shelter_0").inspected,0f)
            VillageEvent.RETURN_HOST_SPACE->assertNull(l.person(id).hostedBy)
            VillageEvent.COMFORT_FEAR,VillageEvent.ESCORT_AFRAID->assertTrue(l.person(peer).fear<.2f)
            VillageEvent.THANK_HELPER->assertEquals(.4f,l.bonds["$id|$peer"]!!,0f)
            VillageEvent.TEACH_FARM->assertTrue(l.person(peer).farmSkill>before.people.getValue(peer).farmSkill)
            VillageEvent.TEACH_BUILD->assertTrue(l.person(peer).buildSkill>before.people.getValue(peer).buildSkill)
            VillageEvent.RELAY_WARNING,VillageEvent.RECALL_WORKER->assertTrue(l.people.any{(who,state)->state.warned&&before.people[who]?.warned==false})
            VillageEvent.VISIT_DISPLACED,VillageEvent.KEEP_COMPANY->assertTrue(l.person(peer).lonely<.2f)
            VillageEvent.WELCOME_GUEST->assertEquals("Shelter_0",l.person(peer).hostedBy)
            VillageEvent.GUIDE_CARRIER,VillageEvent.MARK_SAFE_TRAIL->assertNotNull(l.safeTrail)
            VillageEvent.WAIT_ROUTE->assertNotNull(l.blockedWork[id])
            VillageEvent.WATCH_TIGER_PATH,VillageEvent.RELIEVE_WATCH->assertEquals(1f,l.person(id).watch,0f)
            VillageEvent.REUNITE_HOME->{assertFalse(l.person(peer).warned);assertEquals(0f,l.person(peer).fear,0f)}
            VillageEvent.REOPEN_WORK->{assertFalse(l.person(id).warned);assertNull(l.blockedWork[id])}
            VillageEvent.BAIT_ELEPHANT->{assertTrue(l.grassBait>.9f);assertNotNull(l.cutGrass)}
            VillageEvent.REST_FORAGE_PATCH->assertTrue(l.patches.single().resting)
            VillageEvent.CUT_REEDS->{assertEquals(1,l.reeds);assertTrue(l.bankReeds<1.2f)}
            VillageEvent.CLEAN_MUD_TRAIL->assertEquals(0f,l.trailMud,0f)
            VillageEvent.CLEAR_SILT->{assertEquals(0f,l.silt,0f);assertTrue(l.drainage>.3f)}
        }
    }
}
