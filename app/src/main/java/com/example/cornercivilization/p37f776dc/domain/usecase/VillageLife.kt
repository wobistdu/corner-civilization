package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import kotlin.math.*

/** Needs, finite resources and remembered help, resolved only at travelled work sites. */
internal class VillageLife(private val world:LocalInterventionWorld,private val village:LivingVillage) {
    data class Person(var hunger:Float=.22f,var thirst:Float=.18f,var fatigue:Float=.12f,
        var wet:Float=0f,var mud:Float=0f,var fear:Float=0f,var lonely:Float=.2f,
        var farmSkill:Float=0f,var buildSkill:Float=0f,var tool:Float=1f,
        var meals:Int=0,var drinks:Int=0,var helped:Int=0,var hostedBy:String?=null,
        var interrupted:Boolean=false,var warned:Boolean=false,var watch:Float=0f,
        var lastMeal:Float=0f,var lastSocial:Float=0f)
    data class Patch(val point:Point,var berries:Float=3f,var pressure:Float=0f,
        var resting:Boolean=false,var regrowth:Float=0f,var branches:Int=1,var branchGrowth:Float=0f)
    data class Home(var leak:Float=0f,var brace:Float=0f,var inspected:Float=0f,
        var salvage:Int=0,var visits:Int=0,var damp:Float=0f)
    data class Plan(val event:VillageEvent,val point:Point,val priority:Float,val peer:String?=null,
        val house:String?=null)
    var enabled=false
    val people=linkedMapOf<String,Person>()
    val patches=mutableListOf<Patch>()
    val homes=linkedMapOf<String,Home>()
    val journal=VillageJournal()
    /** Directed debt: the receiver remembers the helper. */
    val debts=linkedMapOf<String,Int>()
    val bonds=linkedMapOf<String,Float>()
    val cargoMemory=mutableMapOf<String,Point>()
    val blockedWork=mutableMapOf<String,String>()
    private val retryAt=mutableMapOf<String,Float>()
    private val needRetryAt=mutableMapOf<String,Float>()
    private val deferred=mutableMapOf<String,Float>()
    private val companions=mutableMapOf<String,String>()
    private val moments=setOf(VillageEvent.GREET_NEIGHBOR,VillageEvent.CHAT_TOGETHER,VillageEvent.STRETCH_BREAK)
    private val personalNeeds=setOf(VillageEvent.SLEEP_HOME,VillageEvent.REST_SHADE,VillageEvent.SLEEP_LONGHOUSE,
        VillageEvent.EAT_STOCK,VillageEvent.EAT_FORAGE,VillageEvent.RATION_MEAL,VillageEvent.DRINK_JAR,
        VillageEvent.DRINK_RIVER,VillageEvent.ALTERNATE_BANK)
    var water=0f
    var reeds=0
    var bankReeds=6f
    var seeds=0
    var dampGrain=0f
    var spoiled=0
    var preservation=0f
    var stockTurn=0f
    var foodProduced=0
    var foodEaten=0
    var foodLost=0
    var foodInvested=0
    var foodBaseline=0
    var foodInGranary=0
    var ration=false
    var reservedFor:String?=null
    var weeds=.15f
    var pests=.12f
    var soilHard=.15f
    var soilFertility=.7f
    var fieldWater=0f
    var drainage=0f
    var mulch=0f
    var trampled=0f
    var fenceWear=0f
    var restingSoil=false
    var cropCycles=0
    var hearthFuel=0f
    var hearthWarm=false
    var reserveHearthBuilders=true
    var woodWet=0f
    var woodCover=0f
    var bankDebris=0f
    var trailDebris=0f
    var trailMud=0f
    var silt=0f
    var cutGrass:Point?=null
    var grassBait=0f
    var safeTrail:Point?=null
    var spareTools=0
    var refugeGrass=0f
    var spill=0
    var spillPoint:Point?=null
    var clock=0f
    private var observedRaids=0
    private var observedHarvests=0
    private var observedDamage=mutableMapOf<String,Int>()

    data class Snapshot(val floats:Map<String,Float>,val ints:Map<String,Int>,val flags:Map<String,Boolean>,
        val people:Map<String,Person>,val patches:List<Patch>,val homes:Map<String,Home>,
        val debts:Map<String,Int>,val bonds:Map<String,Float>,val cargo:Map<String,Point>,
        val blocked:Map<String,String>,val points:Map<String,Point>,val reservedFor:String?,
        val counts:Map<VillageEvent,Int>,val recent:List<VillageJournal.Entry>,
        val animals:Map<String,VillageWildlifeLife.State>)
    private fun floatFields()=linkedMapOf("water" to ::water,"bankReeds" to ::bankReeds,
        "dampGrain" to ::dampGrain,"preservation" to ::preservation,"stockTurn" to ::stockTurn,
        "weeds" to ::weeds,"pests" to ::pests,"soilHard" to ::soilHard,"soilFertility" to ::soilFertility,
        "fieldWater" to ::fieldWater,"drainage" to ::drainage,"mulch" to ::mulch,"trampled" to ::trampled,
        "fenceWear" to ::fenceWear,"hearthFuel" to ::hearthFuel,"woodWet" to ::woodWet,"woodCover" to ::woodCover,
        "bankDebris" to ::bankDebris,"trailDebris" to ::trailDebris,"trailMud" to ::trailMud,"silt" to ::silt,
        "grassBait" to ::grassBait,"refugeGrass" to ::refugeGrass,"clock" to ::clock)
    private fun intFields()=linkedMapOf("reeds" to ::reeds,"seeds" to ::seeds,"spoiled" to ::spoiled,
        "foodProduced" to ::foodProduced,"foodEaten" to ::foodEaten,"foodLost" to ::foodLost,
        "foodInvested" to ::foodInvested,"foodBaseline" to ::foodBaseline,"foodInGranary" to ::foodInGranary,
        "cropCycles" to ::cropCycles,"spareTools" to ::spareTools,"spill" to ::spill)
    fun snapshot()=Snapshot(floatFields().mapValues{it.value.get()},intFields().mapValues{it.value.get()},
        mapOf("ration" to ration,"restingSoil" to restingSoil),people.mapValues{it.value.copy()},
        patches.map{it.copy()},homes.mapValues{it.value.copy()},debts.toMap(),bonds.toMap(),cargoMemory.toMap(),
        blockedWork.toMap(),buildMap {cutGrass?.let{put("cutGrass",it)};safeTrail?.let{put("safeTrail",it)};spillPoint?.let{put("spillPoint",it)}},
        reservedFor,journal.counts.toMap(),journal.recent.toList(),village.animalLife.states.mapValues {
            it.value.copy(intent=null,goal=null,start=null,elapsed=0f)
        })
    fun validate(s:Snapshot) {
        fun resident(id:String)=id.matches(Regex("PrimitiveResident_[0-8]"))
        fun unit(v:Float)=v.isFinite()&&v in 0f..1f
        fun point(p:Point)=p.x.isFinite()&&p.z.isFinite()&&world.navigation.open(p)
        require(s.floats.keys==floatFields().keys&&s.ints.keys==intFields().keys&&s.flags.keys==setOf("ration","restingSoil"))
        s.floats.forEach{(k,v)->require(v.isFinite()&&v>=0f&&v<=when(k){"clock"->100000000f;"water"->2f;"bankReeds"->6f;else->1f})}
        require(s.ints.values.all{it in 0..100000000}&&s.ints.getValue("reeds")<=6&&s.ints.getValue("foodInGranary")<=16)
        require(s.people.size<=9&&s.people.keys.all(::resident))
        s.people.values.forEach{p->
            require(listOf(p.hunger,p.thirst,p.fatigue,p.wet,p.mud,p.fear,p.lonely,p.farmSkill,p.buildSkill,p.tool,p.watch).all(::unit))
            require(p.lastMeal.isFinite()&&p.lastSocial.isFinite()&&p.lastMeal in 0f..s.floats.getValue("clock")&&p.lastSocial in 0f..s.floats.getValue("clock"))
            require(p.meals>=0&&p.drinks>=0&&p.helped>=0&&(p.hostedBy==null||p.hostedBy in village.houses))
        }
        require(s.patches.size<=8&&s.patches.all{point(it.point)&&it.berries.isFinite()&&it.berries in 0f..3f&&it.pressure.isFinite()&&it.pressure>=0f&&
            unit(it.regrowth)&&it.branches in 0..2&&unit(it.branchGrowth)})
        require(s.homes.size<=7&&s.homes.keys.all{it in village.houses})
        require(s.homes.values.all{unit(it.leak)&&unit(it.brace)&&unit(it.inspected)&&unit(it.damp)&&it.salvage in 0..2&&it.visits>=0})
        fun relationship(k:String)=k.split('|').let{it.size==2&&it.all(::resident)&&it[0]!=it[1]}
        require(s.debts.size<=72&&s.debts.all{relationship(it.key)&&it.value in 0..8})
        require(s.bonds.size<=72&&s.bonds.all{relationship(it.key)&&unit(it.value)})
        require(s.cargo.size<=9&&s.cargo.all{resident(it.key)&&point(it.value)})
        require(s.blocked.size<=9&&s.blocked.all{resident(it.key)&&VillageEvent.entries.any{e->e.name==it.value}})
        require(s.points.keys.all{it in setOf("cutGrass","safeTrail","spillPoint")}&&s.points.values.all(::point))
        require(s.reservedFor==null||resident(s.reservedFor))
        require(s.counts.values.all{it>=0}&&s.recent.size<=96&&s.recent.all{it.at.isFinite()&&it.at in 0f..s.floats.getValue("clock")&&it.subject.length<=64&&it.result.length<=256})
        require(s.animals.size<=5&&s.animals.keys.all{it in setOf("MammothAdult_0","MammothAdult_1","MammothCalf","Sabertooth_0","Sabertooth_1")})
        require(s.animals.values.all{unit(it.thirst)&&unit(it.fatigue)&&unit(it.coat)&&unit(it.fenceMemory)&&it.cooldown.isFinite()&&it.cooldown in 0f..20f&&it.reunions>=0&&it.drinks>=0})
    }
    fun restore(s:Snapshot) {
        validate(s)
        floatFields().forEach{(k,p)->p.set(s.floats.getValue(k))};intFields().forEach{(k,p)->p.set(s.ints.getValue(k))}
        ration=s.flags.getValue("ration");restingSoil=s.flags.getValue("restingSoil")
        people.clear();s.people.forEach{(k,v)->people[k]=v.copy()}
        patches.clear();patches+=s.patches.map{it.copy()};homes.clear();s.homes.forEach{(k,v)->homes[k]=v.copy()}
        debts.clear();debts.putAll(s.debts);bonds.clear();bonds.putAll(s.bonds);cargoMemory.clear();cargoMemory.putAll(s.cargo)
        blockedWork.clear();blockedWork.putAll(s.blocked);reservedFor=s.reservedFor
        cutGrass=s.points["cutGrass"];safeTrail=s.points["safeTrail"];spillPoint=s.points["spillPoint"]
        journal.restore(s.counts,s.recent)
        village.animalLife.states.clear();s.animals.forEach{(k,v)->village.animalLife.states[k]=v.copy(intent=null,goal=null,start=null,elapsed=0f)}
        companions.clear();retryAt.clear();needRetryAt.clear();deferred.clear();village.jobs.clear()
        observedDamage.clear();village.houses.values.forEach{observedDamage[it.id]=it.animalHits}
        enabled=true
    }

    fun enable() {
        if(enabled)return
        enabled=true;foodBaseline=village.ecology.food+village.foodBundles.size
        patches.clear();village.sources.forEach{patches+=Patch(it)}
    }
    fun person(id:String)=people.getOrPut(id) {
        val n=id.last().digitToIntOrNull()?:0
        Person(hunger=.18f+n*.025f,thirst=.12f+n*.028f,
            farmSkill=if(n%3==0).28f else .03f,buildSkill=if(n%3==1).28f else .03f)
    }
    fun urgent(id:String)=enabled&&people[id]?.let{it.hunger>.8f||it.thirst>.8f||it.fatigue>.92f}==true
    fun availableForHauling(id:String)=!enabled||person(id).let {
        id !in companions&&it.hunger<.58f&&it.thirst<.62f&&it.fatigue<.55f&&it.wet<.4f
    }
    fun defer(id:String,j:LivingVillage.Job) {
        deferred["$id|${j.event}"]=clock+12f
        retryAt[id]=clock+1f
    }
    private fun home(id:String)=village.houses["Shelter_${(id.last().digitToIntOrNull()?:0)/3}"]
    private fun intact(h:LivingVillage.House?)=h!=null&&h.available&&!h.ruined&&h.health>=80f
    private fun completed(name:String)=village.houses[name]?.let{it.available&&!it.ruined&&it.health>=99f}==true
    internal fun emit(e:VillageEvent,id:String,result:String) {
        if (journal.record(e,id,clock,result)) world.chronicle.villageEvent(e,id,clock)
        village.changed()
    }
    fun speed(id:String):Float {
        val at=village.residents[id]
        val muddy=at!=null&&safeTrail?.distance(at)?.let{it<.045f}==true
        return 1f-person(id).fatigue*.28f-person(id).mud*.12f-(if(muddy)trailMud*.12f+trailDebris*.08f else 0f)
    }
    fun workRate(id:String)=.75f+person(id).buildSkill*.5f+person(id).tool*.25f
    fun recoveredFood(id:String,event:VillageEvent){
        village.residents[id]?.let { at->cargoMemory.entries.removeAll{it.value.distance(at)<.025f} }
        emit(event,id,"走到遗落粮束旁取回")
    }
    fun sheltered(id:String)=people[id]?.hostedBy?.let{village.houses[it]}?.let{intact(it)}==true
    fun roofResistance(id:String)=maxOf((homes[id]?.brace?:0f)*.15f,(homes[id]?.damp?:0f)*.45f)
    fun impactResistance(id:String)=(homes[id]?.brace?:0f)*.55f
    fun foodPoint()=if(foodInGranary>0&&foodInGranary>=village.ecology.food)village.ecology.sites.getValue("Granary")else world.depot
    fun birdTarget():Point?=village.foodBundles.firstOrNull{it.carrier==null&&it.age>80f&&!world.dangerous(it.point)}?.point
    fun birdAt(at:Point) {
        if(!enabled)return
        val b=village.foodBundles.firstOrNull{it.carrier==null&&it.age>80f&&it.point.distance(at)<.008f}?:return
        village.foodBundles.remove(b);foodLost++
        emit(VillageEvent.BIRDS_EAT_SPILL,"birds","落地鸟吃掉遗落粮束；散粮-1")
    }
    private fun key(receiver:String,helper:String)="$receiver|$helper"
    private fun help(receiver:String,helper:String,amount:Int=1) {
        val k=key(receiver,helper);debts[k]=((debts[k]?:0)+amount).coerceAtMost(8)
        bonds[k]=((bonds[k]?:0f)+.18f).coerceAtMost(1f)
        person(helper).helped++;person(receiver).lonely=(person(receiver).lonely-.3f).coerceAtLeast(0f)
    }
    fun advance(dt:Float) {
        if(!enabled)return
        clock+=dt
        if(patches.isEmpty())village.sources.forEach{patches+=Patch(it)}
        village.houses.values.forEach { h->
            val m=homes.getOrPut(h.id){Home()}
            m.inspected=(m.inspected-dt/320f).coerceAtLeast(0f)
            m.damp=(m.damp-dt/65f).coerceAtLeast(0f)
            if(intact(h)&&world.rainAt(h.center)>.1f)m.leak=(m.leak+dt*.003f).coerceAtMost(1f)
            if(h.ruined&&h.animalHits>(observedDamage[h.id]?:0))m.salvage=(m.salvage+1).coerceAtMost(2)
            observedDamage[h.id]=h.animalHits
        }
        people.forEach{(id,p)->
            val at=village.residents[id]?:return@forEach
            p.hunger=(p.hunger+dt*.0012f).coerceAtMost(1f)
            p.thirst=(p.thirst+dt*.0021f).coerceAtMost(1f)
            p.fatigue=(p.fatigue+dt*(if(village.jobs[id]!=null).0015f else .0005f)).coerceAtMost(1f)
            p.lonely=(p.lonely+dt*.0009f).coerceAtMost(1f)
            p.tool=(p.tool-dt*(if(village.jobs[id]?.kind in listOf("build","clear","forage")).0009f else .00004f)).coerceAtLeast(0f)
            val roof=village.houses.values.firstOrNull{intact(it)&&at.distance(it.workPoint)<.025f}
            val rain=world.rainAt(at)*(if(roof!=null)(homes[roof.id]?.leak?:0f)*.35f else 1f)
            p.wet=(p.wet+dt*(rain*.13f-.009f)).coerceIn(0f,1f)
            if(world.wetAt(at)>.3f)p.mud=(p.mud+dt*.015f).coerceAtMost(1f)
            p.fear=(p.fear+dt*(if(world.wildlife.frightening(at)).18f else -.012f)).coerceIn(0f,1f)
            if(p.watch>0f) {
                p.watch=(p.watch-dt/30f).coerceAtLeast(0f)
                if(at.distance(village.ecology.refuge)>.035f)p.watch=0f
            }
            val escortedToWater=companions[id]?.let{village.jobs[it]?.event}==VillageEvent.ESCORT_WATER
            val job=village.jobs[id]
            // A full but inaccessible store must not cancel safe food production.
            // Resolve a reachable need first, at bounded intervals, then release
            // the old job's cargo before the normal brief stop and reselection.
            val producingFood=job?.event in setOf(VillageEvent.FORAGE_BERRIES,VillageEvent.PICK_SEEDHEADS,
                VillageEvent.CUT_RIPE_CROP,VillageEvent.HARVEST_TOGETHER)
            val canSeekWildFood=job?.food==null&&!producingFood
            val actionableNeed=p.thirst>.8f||p.fatigue>.92f||
                (p.hunger>.8f&&job?.food==null&&(village.ecology.food>0||canSeekWildFood))
            val reconsiderRest=canSeekWildFood&&p.hunger>.9f&&p.fatigue<.9f&&
                job?.event in setOf(VillageEvent.REST_SHADE,VillageEvent.SLEEP_HOME,VillageEvent.SLEEP_LONGHOUSE)&&
                job?.path?.isNotEmpty()==true
            if(actionableNeed && job!=null&&(job.event !in personalNeeds||reconsiderRest)&&!escortedToWater&&clock>=(needRetryAt[id]?:0f)) {
                needRetryAt[id]=clock+1.5f
                val need=needPlans(id,at,true,job.food==null,canSeekWildFood).sortedByDescending{it.priority}
                    .firstNotNullOfOrNull{plan->job(id,at,plan)}
                if(need!=null) {
                    village.interrupt(id,at);p.interrupted=true
                    if(p.fatigue>.92f)emit(VillageEvent.STOP_EXHAUSTED,id,"放下物品；疲劳=${p.fatigue}")
                }
            }
        }
        patches.forEach { p->
            p.branchGrowth+=dt*(.002f+world.rainAt(p.point)*.012f)
            if(p.branchGrowth>=1f){p.branches=(p.branches+p.branchGrowth.toInt()).coerceAtMost(2);p.branchGrowth%=1f}
            val occupied=village.jobs.values.any{it.event==VillageEvent.FORAGE_BERRIES&&it.goal.distance(p.point)<.025f}
            // A picked-out bush regrows after the gatherers leave. A hungry
            // villager need not spend a second journey granting it permission.
            if(!occupied&&p.berries<1f&&p.pressure>0f)p.resting=true
            if(!occupied&&p.resting) {
                p.regrowth+=dt*(.005f+world.wetAt(p.point)*.012f)
                if(p.regrowth>=1f) {
                    p.berries=(p.berries+p.regrowth.toInt()).coerceAtMost(3f);p.regrowth%=1f
                    if(p.berries>=2f){p.resting=false;p.pressure=0f;emit(VillageEvent.BERRIES_REGROW,patches.indexOf(p).toString(),"野果=${p.berries}")}
                }
            }
        }
        val wet=world.wetAt(village.ecology.field)
        bankReeds=(bankReeds+dt*(.002f+world.rainAt(village.ecology.bank)*.006f)).coerceAtMost(6f)
        if(people.values.none{it.watch>0f})village.ecology.guard=null
        companions.entries.removeAll { (follower,leader)->
            val leaderJob=village.jobs[leader]
            val valid=leaderJob?.peer==follower&&leaderJob.kind=="life_guiding"&&leaderJob.elapsed<50f&&
                village.jobs[follower]?.kind=="life_follow"
            if(!valid&&village.jobs[follower]?.kind=="life_follow")village.jobs.remove(follower)
            if(!valid&&leaderJob?.kind=="life_guiding"&&leaderJob.peer==follower)
                village.residents[leader]?.let { village.interrupt(leader,it) }
            !valid
        }
        fieldWater=(fieldWater+dt*(wet*.022f-.004f-drainage*.009f)).coerceIn(0f,1f)
        drainage=(drainage-dt*.0004f).coerceAtLeast(0f)
        weeds=(weeds+dt*(.0005f+wet*.001f)*(1f-mulch*.8f)).coerceAtMost(1f)
        pests=(pests+dt*.00045f).coerceAtMost(1f)
        soilHard=(soilHard+dt*(if(wet<.1f).0009f else -.0003f)).coerceIn(0f,1f)
        mulch=(mulch-dt*.0007f).coerceAtLeast(0f)
        fenceWear=(fenceWear+dt*.00015f).coerceAtMost(1f)
        if(village.agriculture) {
            village.ecology.crop=(village.ecology.crop-dt*(weeds*.0008f+pests*.0006f+maxOf(0f,fieldWater-.8f)*.004f)).coerceIn(.05f,1f)
            if(restingSoil){soilFertility=(soilFertility+dt*.002f).coerceAtMost(1f)}
            if(restingSoil&&soilFertility>=.85f)restingSoil=false
            village.ecology.crop=(village.ecology.crop+dt*(mulch*.0008f+(1f-soilHard)*soilFertility*.001f)).coerceAtMost(1f)
        }
        foodInGranary=foodInGranary.coerceIn(0,village.ecology.food)
        val stockRain=world.rainAt(world.depot)*(1f-foodInGranary.toFloat()/maxOf(1,village.ecology.food))
        dampGrain=(dampGrain+dt*(stockRain*.025f-(if(completed("Granary")).025f else .002f))).coerceIn(0f,1f)
        stockTurn=(stockTurn-dt*.001f).coerceAtLeast(0f)
        preservation=(preservation-dt*.0005f).coerceAtLeast(0f)
        if(dampGrain>.85f&&village.ecology.food>0&&preservation<.2f&&stockTurn<.2f) {
            village.ecology.food--;spoiled++;foodLost++;dampGrain=.45f;village.changed()
        }
        woodWet=(woodWet+dt*(stockRain*.04f*(1f-woodCover)-.002f)).coerceIn(0f,1f)
        woodCover=(woodCover-dt*.0005f).coerceAtLeast(0f)
        bankDebris=(bankDebris+dt*world.rainAt(village.ecology.bank)*.006f).coerceAtMost(1f)
        silt=(silt+dt*wet*.002f).coerceAtMost(1f)
        if(world.rainAt(safeTrail?:world.depot)>.1f&&trailDebris<.3f&&wet>.6f) {
            trailDebris=.35f;trailMud=(trailMud+.2f).coerceAtMost(1f)
            emit(VillageEvent.FLOOD_TRAIL,"trail","泥沙沉积，小路需清理")
        }
        hearthFuel=(hearthFuel-dt*.003f).coerceAtLeast(0f)
        refugeGrass=(refugeGrass-dt/90f).coerceAtLeast(0f)
        grassBait=(grassBait-dt/90f).coerceAtLeast(0f)
        if(grassBait<=0f)cutGrass=null
        village.foodBundles.forEach{b->
            b.age+=dt;b.wet=(b.wet+dt*(world.rainAt(b.point)*.05f-.002f)).coerceIn(0f,1f)
        }
        if(completed("Granary"))village.ecology.foodCapacity=16 else village.ecology.foodCapacity=8
        if(village.agriculture&&village.houses.values.none{it.available&&it.health<99f}) {
            val demanded=when {
                village.ecology.raids>0&&!completed("FieldFence")->"FieldFence"
                village.ecology.food>=6&&!completed("Granary")->"Granary"
                people.values.count{it.hostedBy!=null||it.fatigue>.6f}>=2&&!completed("Longhouse")->"Longhouse"
                else->null
            }
            val cost=if(demanded=="FieldFence")1 else 3
            village.houses[demanded]?.takeIf{!it.available&&village.ecology.food>=cost&&world.woods.any{w->w.phase==WoodPhase.STACKED}}?.apply{
                available=true;health=0f;ruined=true;cleared=6f;village.ecology.food-=cost;foodInvested+=cost;village.changed()
            }
        }
        observedRaids=village.ecology.raids;observedHarvests=village.harvests
    }
    fun interrupted(id:String,j:LivingVillage.Job,at:Point) {
        if(j.food!=null&&j.carrying)cargoMemory[id]=at
        if(j.kind.startsWith("life_"))person(id).interrupted=true
        if(j.carrying&&j.food==null&&j.wood==null) {
            emit(VillageEvent.SPILL_WATER,id,"避险时水洒在地上")
            val wet=world.wetSpots.firstOrNull{it.point.distance(at)<.02f}
            if(wet!=null)wet.wet=(wet.wet+.2f).coerceAtMost(1f)
            else if(world.wetSpots.size<24)world.wetSpots+=WetSpot(at,.2f)
        }
    }
    fun deliveredFood(id:String,b:LivingVillage.FoodBundle) {
        dampGrain=maxOf(dampGrain,b.wet*.6f)
        cargoMemory.remove(id);village.changed()
    }
    private fun job(id:String,current:Point,p:Plan):LivingVillage.Job? {
        if(p.event in moments) {
            // Short encounters take place where the bodies already are. They do
            // not interrupt hauling or force neighbours to chase one another.
            return LivingVillage.Job("life_${p.event.name}",null,mutableListOf(),current,event=p.event,peer=p.peer)
        }
        val collectAtDepot=p.event in setOf(VillageEvent.SHARE_MEAL,VillageEvent.REPAY_HELP,VillageEvent.INVITE_MEAL,VillageEvent.SHARE_WATER,VillageEvent.KEEP_LAST_MEAL)
        val desired=if(p.peer!=null&&!collectAtDepot)village.residents[p.peer]?:p.point else p.point
        val goal=if(p.event==VillageEvent.RECALL_WORKER)world.navigation.points
            .filter{it.distance(desired)<.023f&&!world.dangerous(it)}
            .minByOrNull{it.distance(current)}?:return null
            else village.nearestOpen(desired)?:return null
        if(world.dangerous(goal))return null
        val path=world.navigation.path(current,goal,world.residentRouteAllowed(id))?:return null
        return LivingVillage.Job("life_${p.event.name}",p.house,path.toMutableList(),goal,event=p.event,peer=p.peer)
    }
    private fun needPlans(id:String,current:Point,urgentOnly:Boolean=false,allowStoredFood:Boolean=true,
        allowWildFood:Boolean=false):List<Plan> {
        val p=person(id);val owner=home(id);val bank=village.ecology.bank
        return buildList {
            fun offer(event:VillageEvent,point:Point,priority:Float,condition:Boolean) {
                if(condition)add(Plan(event,point,priority))
            }
            val hungry=p.hunger>if(urgentOnly).8f else .58f
            val thirsty=p.thirst>if(urgentOnly).8f else .62f
            val tired=p.fatigue>if(urgentOnly).92f else .72f
            offer(if(ration)VillageEvent.RATION_MEAL else VillageEvent.EAT_STOCK,foodPoint(),
                80f+p.hunger*20f,hungry&&allowStoredFood&&village.ecology.food>0&&
                    (reservedFor==null||reservedFor==id||p.hunger>.9f))
            if(hungry&&allowWildFood&&clock>=(deferred["$id|${VillageEvent.FORAGE_BERRIES}"]?:0f)) {
                patches.asSequence().filter { patch->
                    patch.berries>=1f&&!patch.resting&&village.jobs.values.none {
                        it.event==VillageEvent.FORAGE_BERRIES&&it.goal.distance(patch.point)<.02f
                    }
                }.sortedBy{it.point.distance(current)}.take(3).forEach {
                    offer(VillageEvent.FORAGE_BERRIES,it.point,95f+p.hunger*5f,true)
                }
            }
            offer(VillageEvent.DRINK_JAR,world.depot,80f+p.thirst*20f,thirsty&&water>=.25f)
            // A jar behind a large animal is no substitute for reachable water.
            offer(VillageEvent.DRINK_RIVER,bank,79f+p.thirst*20f,thirsty&&bankDebris<.75f)
            offer(VillageEvent.ALTERNATE_BANK,Point(bank.x,bank.z+.035f),94f,
                thirsty&&(bankDebris>=.75f||world.dangerous(bank)))
            if(tired) {
                // Prefer home, then an unoccupied covered seat in the shared rain shelter.
                for(house in listOfNotNull(owner,village.houses["RainShelter"])) {
                    if(!intact(house) || village.jobs.any { (other,j) ->
                        other!=id && j.event==VillageEvent.SLEEP_HOME && j.house==house.id
                    }) continue
                    val seat=village.restSeats[house.id] ?: if(village.restSeats.isEmpty())house.workPoint else continue
                    if(!world.dangerous(seat))add(Plan(VillageEvent.SLEEP_HOME,seat,
                        85f+p.fatigue*10f+(if(house===owner).2f else 0f),house=house.id))
                }
            }
            offer(VillageEvent.SLEEP_LONGHOUSE,village.ecology.sites.getValue("Longhouse"),87f,
                tired&&!intact(owner)&&completed("Longhouse"))
            val shade=patches.minByOrNull{it.point.distance(current)}?.point?:current
            offer(VillageEvent.REST_SHADE,shade,84f,
                p.fatigue>(if(urgentOnly).92f else .55f)&&!world.weatherDangerous(shade))
        }
    }
    fun choose(id:String,current:Point):LivingVillage.Job? {
        person(id)
        if(id in companions)return null
        if(clock<(retryAt[id]?:0f))return null
        retryAt[id]=clock+1.5f
        val p=person(id);val owner=home(id)
        val plans=mutableListOf<Plan>()
        fun offer(e:VillageEvent,at:Point,priority:Float,condition:Boolean=true,peer:String?=null,house:String?=null) {
            // A distant walker must not reserve everybody else's ability to eat,
            // drink or rest. Bodies still stop at separate, collision-safe edges;
            // each person rechecks and consumes the real stock at completion.
            if(condition&&clock>=(deferred["$id|$e"]?:0f)&&
                (e in personalNeeds||village.jobs.values.none{it.event==e&&it.peer==peer&&(peer!=null||it.goal.distance(at)<.02f)}))
                plans+=Plan(e,at,priority,peer,house)
        }
        val stock=village.ecology.food
        // Assign urgent gatherers against food already picked or being picked,
        // while actual eating/construction still requires delivered stock.
        // Otherwise an empty depot sends almost everybody on the same long
        // gathering trip, even with a full village meal already in transit.
        val incomingFood=village.foodBundles.size+village.jobs.values.count {
            it.kind.startsWith("life_")&&it.event in setOf(VillageEvent.FORAGE_BERRIES,
                VillageEvent.PICK_SEEDHEADS,VillageEvent.CUT_RIPE_CROP,VillageEvent.HARVEST_TOGETHER)
        }
        val thirsty=people.entries.filter{it.key!=id&&it.value.thirst>.6f}.maxByOrNull{it.value.thirst}
        val hungry=people.entries.filter{it.key!=id&&it.value.hunger>.65f}.maxByOrNull{it.value.hunger}
        val vulnerable=hungry?.takeIf{it.value.hunger>p.hunger+.12f}
        val refuge=village.houses.values.filter{intact(it)&&it.id.startsWith("Shelter_")}.minByOrNull{it.workPoint.distance(current)}
        val shade=patches.minByOrNull{it.point.distance(current)}?.point?:world.depot
        val ownPoint=owner?.workPoint?:refuge?.workPoint?:world.depot
        val bank=village.ecology.bank
        val field=village.ecology.field
        offer(VillageEvent.KEEP_LAST_MEAL,world.depot,100f,stock==1&&vulnerable!=null&&reservedFor==null,peer=vulnerable?.key)
        needPlans(id,current).forEach{offer(it.event,it.point,it.priority,house=it.house)}
        val localMeal=village.foodBundles.filter{it.carrier==null&&!world.dangerous(it.point)}
            .minByOrNull{it.point.distance(current)}
        if(localMeal!=null)offer(VillageEvent.EAT_FORAGE,localMeal.point,81f+p.hunger*20f,p.hunger>.58f)
        offer(VillageEvent.DRY_CLOTHES,refuge?.workPoint?:ownPoint,75f,p.wet>.4f&&refuge!=null)
        offer(VillageEvent.WARM_AT_HEARTH,world.depot,76f,p.wet>.55f&&hearthWarm&&hearthFuel>.1f)
        // Readiness belongs to the recovered person, not a remembered worksite.
        // Actual abandoned food still requires foodPickup and physical hauling;
        // walking home (or to a bundle already eaten by birds) clears no cargo.
        offer(VillageEvent.RESUME_RECOVERED,current,48f,p.interrupted&&p.fatigue<.35f&&p.fear<.2f)

        // All residents can take an urgent task; expertise affects work rate, not eligibility.
        village.houses.values.filter{it.available&&it.health<99.99f&&it.safeSeconds>=3f}.sortedBy{it.health}.forEach{h->
            if(h.stage in listOf("clear","build")&&village.jobs.values.count{it.house==h.id&&it.kind in listOf("clear","build")}<2) {
                val goal=village.nearestOpen(Point(h.workPoint.x+(if(id.hashCode()%2==0)-.007f else .007f),h.workPoint.z))?:h.workPoint
                world.navigation.path(current,goal,world.residentRouteAllowed(id))?.let{
                    if(p.hunger<.8f&&p.thirst<.8f&&p.fatigue<.75f&&plans.none{q->q.priority>=80f})
                        return LivingVillage.Job(h.stage,h.id,it.toMutableList(),goal)
                }
            }
            if(h.stage=="supply"&&h.materials+village.jobs.values.count{it.house==h.id&&it.kind=="supply"}<h.needed) {
                val pile=world.woods.firstOrNull{it.phase==WoodPhase.STACKED&&it.carrier==null}
                if(pile!=null&&plans.none{it.priority>=80f})village.supplyPath(id,current,world.depot)?.let{
                    pile.carrier=id;return LivingVillage.Job("supply",h.id,it.toMutableList(),h.workPoint,pile.id)
                }
            }
        }
        if(!world.dangerous(world.depot)&&village.foodBundles.any{it.carrier==null}&&
            stock<village.ecology.foodCapacity&&plans.none{it.priority>=80f}) {
            val bundle=village.foodBundles.filter{it.carrier==null&&!world.dangerous(it.point)}.minByOrNull{it.point.distance(current)}
            if(bundle!=null) {
                val event=if(bundle.wet>.35f)VillageEvent.MOVE_GRAIN_DRY else if(cargoMemory.isNotEmpty())VillageEvent.RECOVER_CARGO else null
                val path=world.navigation.path(current,bundle.point,world.residentRouteAllowed(id))
                if(path!=null){bundle.carrier=id;return LivingVillage.Job("foodPickup",null,path.toMutableList(),bundle.point,food=bundle,event=event)}
            }
        }
        if(!village.agriculture&&village.settled&&stock>=3&&village.jobs.values.none{it.kind=="cultivate"}&&plans.none{it.priority>=80f}) {
            val goal=village.nearestOpen(field)
            if(goal!=null)world.navigation.path(current,goal,world.residentRouteAllowed(id))?.let{return LivingVillage.Job("cultivate",null,it.toMutableList(),goal)}
        }
        val woodDemand=village.houses.values.any{it.available&&it.health<99f&&it.materials<it.needed}||
            village.agriculture&&stock>=3&&village.houses.values.any{!it.available}||hearthFuel<.2f&&hearthWarm
        patches.filter{it.branches>0}.minByOrNull{it.point.distance(current)}?.let {
            offer(VillageEvent.COLLECT_BRANCH,it.point,69f,woodDemand&&world.woods.size<6)
        }
        patches.filter{it.berries>=1f&&!it.resting}.sortedBy{it.point.distance(current)}.take(3).forEach{
            offer(VillageEvent.FORAGE_BERRIES,it.point,if(p.hunger>.8f)95f+p.hunger*5f else if(stock+incomingFood<3)72f else 39f,
                p.hunger>.8f||stock+incomingFood<village.ecology.foodCapacity)
        }
        patches.firstOrNull{it.berries<1f&&!it.resting}?.let{offer(VillageEvent.REST_FORAGE_PATCH,it.point,38f)}
        val grown=world.wetSpots.filter{it.growth>.55f}.minByOrNull{it.point.distance(current)}
        offer(VillageEvent.PICK_SEEDHEADS,grown?.point?:field,if(p.hunger>.8f)94f+p.hunger*5f else 43f,
            grown!=null&&(p.hunger>.8f||stock+village.foodBundles.size<village.ecology.foodCapacity))
        offer(VillageEvent.CUT_RIPE_CROP,field,if(p.hunger>.8f)94f+p.hunger*5f else 68f,
            village.agriculture&&!restingSoil&&village.ecology.crop>.68f&&
                (p.hunger>.8f||stock+village.foodBundles.size<village.ecology.foodCapacity))
        offer(VillageEvent.DRY_WET_GRAIN,world.depot,66f,dampGrain>.35f&&stock>0&&world.rainAt(world.depot)<.1f)
        offer(VillageEvent.PRESERVE_GRAIN,world.depot,40f,stock>=4&&preservation<.3f&&hearthWarm&&hearthFuel>.2f)
        offer(VillageEvent.TURN_STOCK,world.depot,30f,stock>=3&&dampGrain>.15f&&stockTurn<.25f)
        offer(VillageEvent.COMPOST_SPOILED,field,52f,spoiled>0&&village.ecology.scorch==null)
        offer(VillageEvent.COMPOST_SCAR,village.ecology.scorch?:field,53f,spoiled>0&&village.ecology.scorch!=null)
        val stockThreat=world.hazards.firstOrNull{it.kind==Kind.FIRE&&it.point.distance(world.depot)<it.radius+.055f}
        val rescueEdge=if(stockThreat!=null)world.navigation.points.filter{it.distance(world.depot)<.035f&&!world.dangerous(it)}
            .minByOrNull{it.distance(current)} else null
        offer(VillageEvent.RESCUE_STOCK,rescueEdge?:world.depot,92f,stock-foodInGranary>0&&stockThreat!=null&&rescueEdge!=null)
        offer(VillageEvent.STORE_GRANARY,world.depot,49f,stock-foodInGranary>0&&completed("Granary"))
        offer(VillageEvent.AGREE_RATIONS,world.depot,70f,stock<=2&&people.values.count{it.hunger>.55f}>=2&&!ration)
        offer(VillageEvent.COOK_TOGETHER,world.depot,34f,stock>=3&&people.entries.count{it.value.hunger>.4f&&village.residents[it.key]?.distance(world.depot)?.let{d->d<.025f}==true}>=2&&hearthWarm&&!village.jobs.values.any{it.event==VillageEvent.COOK_TOGETHER})
        offer(VillageEvent.RESERVE_SEEDS,world.depot,57f,village.agriculture&&stock>=3&&seeds<1)

        offer(VillageEvent.FILL_JAR,bank,55f,water<.5f&&bankDebris<.7f)
        offer(VillageEvent.SHARE_WATER,world.depot,82f,thirsty!=null&&water>=.4f&&thirsty.value.fatigue>.45f,peer=thirsty?.key)
        offer(VillageEvent.WATER_FIELD,bank,47f,village.agriculture&&fieldWater<.25f&&bankDebris<.7f)
        offer(VillageEvent.WASH_MUD,bank,51f,p.mud>.5f&&bankDebris<.7f)
        val rain=world.hazards.firstOrNull{it.kind==Kind.RAIN}
        val rainEdge=rain?.let{r->world.navigation.points.filter{
            it.distance(r.point) in (r.radius+.010f)..(r.radius+.035f)&&!world.dangerous(it)
        }.minByOrNull{it.distance(current)}}
        offer(VillageEvent.CATCH_RAIN,rainEdge?:bank,58f,rain!=null&&water<.6f)
        offer(VillageEvent.DAMP_ROOF,world.depot,89f,intact(owner)&&water>.35f&&(homes[owner?.id]?.damp?:0f)<.25f&&world.hazards.any{it.kind==Kind.FIRE&&it.point.distance(ownPoint)<.12f},house=owner?.id)
        offer(VillageEvent.COOL_ASH,world.depot,45f,water>.35f&&village.ecology.scorch!=null&&village.ecology.scarAge in 20f..70f)
        offer(VillageEvent.WATER_SEEDLINGS,bank,54f,village.agriculture&&cropCycles>0&&village.ecology.crop<.35f&&fieldWater<.4f)
        offer(VillageEvent.CLEAR_BANK,bank,62f,bankDebris>.45f)
        offer(VillageEvent.ESCORT_WATER,bank,60f,p.fear<.15f&&thirsty!=null&&thirsty.value.fear>.4f,peer=thirsty?.key)

        if(village.agriculture) {
            offer(VillageEvent.SOW_SEEDS,field,60f,seeds>0&&village.ecology.crop<.28f&&!restingSoil)
            offer(VillageEvent.PULL_WEEDS,field,46f,weeds>.35f)
            offer(VillageEvent.MULCH_FIELD,field,48f,reeds>0&&fieldWater<.2f&&mulch<.3f)
            offer(VillageEvent.LOOSEN_SOIL,field,43f,soilHard>.45f)
            offer(VillageEvent.DRAIN_FIELD,field,61f,fieldWater>.75f&&drainage<.6f)
            offer(VillageEvent.STAKE_CROP,field,64f,trampled>.25f)
            offer(VillageEvent.PICK_PESTS,field,44f,pests>.4f)
            offer(VillageEvent.REPLANT_GAP,field,56f,seeds>0&&trampled>.1f&&trampled<=.25f)
            offer(VillageEvent.CHECK_FENCE,village.ecology.sites.getValue("FieldFence"),41f,completed("FieldFence")&&fenceWear>.3f&&fenceWear<.5f)
            offer(VillageEvent.MEND_FENCE,world.depot,63f,completed("FieldFence")&&fenceWear>=.5f&&world.woods.any{it.phase==WoodPhase.STACKED&&it.carrier==null})
            offer(VillageEvent.REST_SOIL,field,42f,soilFertility<.4f&&!restingSoil)
            offer(VillageEvent.SAVE_OVERWATERED,field,65f,fieldWater>.9f&&village.ecology.crop<.4f)
            offer(VillageEvent.CLEAR_SILT,field,50f,silt>.4f)
        }
        val hm=owner?.let{homes[it.id]}
        offer(VillageEvent.PATCH_ROOF,ownPoint,59f,intact(owner)&&hm!=null&&hm.leak>.3f&&reeds>0,house=owner?.id)
        offer(VillageEvent.BRACE_HOUSE,world.depot,62f,intact(owner)&&(owner?.animalHits?:0)>0&&hm!=null&&hm.brace<.3f&&world.woods.any{it.phase==WoodPhase.STACKED&&it.carrier==null},house=owner?.id)
        offer(VillageEvent.CLEAR_TRAIL,safeTrail?:world.depot,48f,trailDebris>.3f)
        offer(VillageEvent.FEED_HEARTH,world.depot,51f,hearthWarm&&hearthFuel<.3f&&world.woods.any{it.phase==WoodPhase.STACKED&&it.carrier==null})
        offer(VillageEvent.COVER_WOOD,world.depot,51f,world.woods.isNotEmpty()&&woodCover<.3f&&reeds>0&&world.hazards.any{it.kind==Kind.RAIN})
        offer(VillageEvent.DRY_WOOD,world.depot,41f,world.woods.isNotEmpty()&&woodWet>.4f&&world.rainAt(world.depot)<.1f)
        village.houses.values.firstOrNull{it.ruined&&(homes[it.id]?.salvage?:0)>0}?.let{offer(VillageEvent.SALVAGE_TIMBER,it.workPoint,67f,house=it.id)}
        offer(VillageEvent.MAKE_TOOL,world.depot,35f,spareTools<2&&world.woods.any{it.phase==WoodPhase.STACKED&&it.carrier==null}&&stock>=3)
        offer(VillageEvent.MEND_TOOL,ownPoint,58f,p.tool<.4f&&spareTools>0)
        val buildPeer=village.jobs.entries.firstOrNull{it.key!=id&&it.value.kind=="build"}
        offer(VillageEvent.BUILD_TOGETHER,buildPeer?.value?.goal?:ownPoint,63f,buildPeer!=null&&p.buildSkill>.15f,peer=buildPeer?.key)
        offer(VillageEvent.INSPECT_HOME,ownPoint,26f,intact(owner)&&hm!=null&&hm.inspected<.15f,house=owner?.id)
        offer(VillageEvent.RETURN_HOST_SPACE,ownPoint,46f,p.hostedBy!=null&&intact(owner))

        offer(VillageEvent.SHARE_MEAL,foodPoint(),81f,hungry!=null&&stock>0&&(p.hunger<.5f||hungry.value.hunger>.9f),peer=hungry?.key)
        val peer=people.entries.filter{it.key!=id}.minByOrNull{(village.residents[it.key]?:world.depot).distance(current)}
        val peerAt=peer?.key?.let{village.residents[it]}?:ownPoint
        offer(VillageEvent.COMFORT_FEAR,peerAt,61f,peer!=null&&peer.value.fear>.5f&&p.fear<.2f,peer=peer?.key)
        val debt=debts.entries.firstOrNull{it.key.startsWith("$id|")&&it.value>0}
        val helper=debt?.key?.substringAfter('|')
        val trust=helper?.let{bonds[key(id,it)]?:0f}?:0f
        val helperAt=helper?.let{village.residents[it]}
        val helperStopped=helper!=null&&village.jobs[helper]?.path?.isNotEmpty()!=true
        // Visit a helper who has stopped, rather than chasing a moving worker.
        // Keep the debt until an actual meeting succeeds.
        offer(VillageEvent.THANK_HELPER,helperAt?:ownPoint,38f,
            helperAt!=null&&helperStopped&&trust<.3f,peer=helper)
        offer(VillageEvent.REPAY_HELP,foodPoint(),50f,helper!=null&&stock>=3&&person(helper).hunger>.4f,peer=helper)
        offer(VillageEvent.TEACH_FARM,field,37f,village.agriculture&&peer!=null&&p.farmSkill>peer.value.farmSkill+.25f,peer=peer?.key)
        val apprentice=buildPeer?.key?.let{person(it)}
        offer(VillageEvent.TEACH_BUILD,buildPeer?.value?.goal?:peerAt,if((apprentice?.buildSkill?:1f)<.25f)67f else 37f,apprentice!=null&&p.buildSkill>apprentice.buildSkill+.25f,
            peer=buildPeer?.key,house=buildPeer?.value?.house)
        val activeWarning=world.wildlife.bodies.values.any{it.alive&&it.species==WildlifeWorld.Species.TIGER&&it.point.distance(current)<.15f}
        offer(VillageEvent.RELAY_WARNING,peerAt,68f,p.warned&&activeWarning&&peer!=null&&!peer.value.warned,peer=peer?.key)
        offer(VillageEvent.VISIT_DISPLACED,peerAt,33f,peer?.value?.hostedBy!=null&&peer.value.lonely>.4f,peer=peer?.key)
        offer(VillageEvent.INVITE_MEAL,foodPoint(),40f,peer!=null&&peer.value.lonely>.6f&&stock>=2&&p.hunger>.35f,peer=peer?.key)
        val farmPeer=village.jobs.entries.firstOrNull{it.key!=id&&it.value.event==VillageEvent.CUT_RIPE_CROP}
        offer(VillageEvent.HARVEST_TOGETHER,field,64f,farmPeer!=null&&village.ecology.crop>.5f&&stock+village.foodBundles.size<village.ecology.foodCapacity,peer=farmPeer?.key)
        offer(VillageEvent.WELCOME_GUEST,ownPoint,54f,intact(owner)&&peer!=null&&!intact(home(peer.key))&&peer.value.hostedBy==null,peer=peer?.key)
        offer(VillageEvent.KEEP_COMPANY,peerAt,32f,peer!=null&&peer.value.fatigue>.55f&&peer.value.lonely>.4f,peer=peer?.key)

        val threat=world.wildlife.bodies.values.firstOrNull{it.alive&&it.species==WildlifeWorld.Species.TIGER&&it.point.distance(current)<.15f}
        val endangered=people.entries.firstOrNull{it.key!=id&&(village.residents[it.key]?.let{at->world.wildlife.frightening(at)}==true)}
        offer(VillageEvent.RECALL_WORKER,peerAt,90f,endangered!=null&&!endangered.value.warned,peer=endangered?.key)
        offer(VillageEvent.SHELTER_FROM_RAIN,refuge?.workPoint?:ownPoint,78f,p.wet>.25f&&rain!=null&&refuge!=null)
        offer(VillageEvent.ESCORT_AFRAID,peerAt,65f,peer!=null&&peer.value.fear>.4f&&p.fear<.2f&&refuge!=null,peer=peer?.key)
        val carrier=village.jobs.entries.firstOrNull{it.key!=id&&it.value.carrying&&(village.residents[it.key]?.distance(current)?:1f)<.035f}
        offer(VillageEvent.GUIDE_CARRIER,Point(current.x+.018f,current.z),60f,carrier!=null&&safeTrail==null,peer=carrier?.key)
        offer(VillageEvent.WATCH_TIGER_PATH,village.ecology.refuge,71f,threat!=null&&p.fear<.25f&&people.values.none{it.watch>.1f})
        val watcher=people.entries.firstOrNull{it.key!=id&&it.value.watch>.1f&&it.value.fatigue>.55f}
        offer(VillageEvent.RELIEVE_WATCH,village.ecology.refuge,73f,watcher!=null&&p.fatigue<.4f,peer=watcher?.key)
        offer(VillageEvent.REUNITE_HOME,peerAt,45f,peer!=null&&peer.value.warned&&threat==null&&intact(home(peer.key)),peer=peer?.key)
        offer(VillageEvent.MARK_SAFE_TRAIL,Point(world.depot.x+.045f,world.depot.z),28f,safeTrail==null&&trailDebris<.3f)
        offer(VillageEvent.REOPEN_WORK,ownPoint,44f,p.warned&&threat==null&&p.fear<.2f)

        val adult=world.wildlife.bodies.values.firstOrNull{it.species==WildlifeWorld.Species.ADULT&&it.alive&&it.point.distance(field)<.09f}
        val outsideGrass=world.wetSpots.filter{it.growth>.55f&&it.point.distance(field)>.06f}.minByOrNull{it.point.distance(current)}
        offer(VillageEvent.BAIT_ELEPHANT,outsideGrass?.point?:shade,74f,adult!=null&&outsideGrass!=null&&grassBait<.1f)
        offer(VillageEvent.CUT_REEDS,bank,36f,reeds<3&&bankReeds>=1f&&bankDebris<.5f)
        offer(VillageEvent.CLEAN_MUD_TRAIL,safeTrail?:world.depot,39f,trailMud>.3f)
        offer(VillageEvent.KEEP_GRASS_REFUGE,grown?.point?:shade,47f,adult!=null&&grown!=null&&refugeGrass<.3f)
        val socialReady=clock-p.lastSocial>12f+(id.last().digitToIntOrNull()?:0)*1.1f &&
            p.hunger<.65f&&p.thirst<.65f&&p.fear<.2f&&p.wet<.4f
        val neighbour=village.residents.entries.filter {
            val other=person(it.key)
            it.key!=id&&it.value.distance(current)<.065f&&!world.dangerous(it.value)&&
                village.jobs[it.key]==null&&clock-other.lastSocial>10f&&
                other.hunger<.58f&&other.thirst<.62f&&other.fatigue<.55f&&other.fear<.2f&&other.wet<.4f&&
                (!reserveHearthBuilders||it.key !in listOf("PrimitiveResident_3","PrimitiveResident_6"))&&
                village.jobs.values.none{j->j.peer==it.key}
        }.minByOrNull{it.value.distance(current)}
        if(socialReady && village.jobs.values.none{it.peer==id}) {
            // A local break is preferred to another low-priority inspection,
            // while water, food, safety, repair and harvest retain priority.
            val socialPriority=if(clock-p.lastSocial>24f)36f+p.lonely*20f else 24f
            offer(VillageEvent.CHAT_TOGETHER,current,socialPriority+1f,
                neighbour!=null&&neighbour.value.distance(current)<.030f&&p.lonely>.18f,peer=neighbour?.key)
            offer(VillageEvent.GREET_NEIGHBOR,current,socialPriority,
                neighbour!=null,peer=neighbour?.key)
            offer(VillageEvent.STRETCH_BREAK,current,23f+p.fatigue*26f,neighbour==null&&p.fatigue>.12f)
        }
        // For ordinary chores, nearby work and brief social pauses compete with
        // a long trip. Immediate needs and rescue keep their unmodified priority.
        fun score(plan:Plan,distance:Float)=plan.priority-
            if(plan.priority>=80f)0f else minOf(24f,(distance-.08f).coerceAtLeast(0f)*40f)
        val ordered=plans.sortedByDescending { score(it,current.distance(it.point)) }
        var selected:LivingVillage.Job?=null
        var selectedScore=Float.NEGATIVE_INFINITY
        for(plan in ordered.take(12)) {
            // Direct distance is only an optimistic bound: rivers and animals
            // can turn a nearby chore into a long detour. Compare the route we
            // will actually walk before committing a worker or social partner.
            if(score(plan,current.distance(plan.point))<=selectedScore)break
            val candidate=job(id,current,plan)?:continue
            var previous=current
            var travel=0f
            candidate.path.forEach { point -> travel+=previous.distance(point);previous=point }
            val actualScore=score(plan,maxOf(travel,current.distance(plan.point)))
            if(actualScore>selectedScore){selected=candidate;selectedScore=actualScore}
        }
        selected?.let {
            blockedWork.remove(id)
            if(it.event==VillageEvent.EAT_FORAGE&&localMeal!=null&&localMeal.carrier==null) {
                localMeal.carrier=id
                // Navigation may snap the route to a grid node. Arrival still
                // targets the real food, including at the edge of arm's reach.
                return it.copy(goal=localMeal.point,food=localMeal)
            }
            if(it.event in moments && it.peer!=null) {
                val other=village.residents[it.peer]
                if(other!=null&&village.jobs[it.peer]==null) {
                    village.jobs[it.peer]=LivingVillage.Job("life_reply",null,mutableListOf(),other,
                        event=it.event,peer=id)
                }
            }
            return it
        }
        if(ordered.isNotEmpty()){
            blockedWork[id]=ordered.first().event.name;retryAt[id]=clock+8f
            emit(VillageEvent.WAIT_ROUTE,id,"记下阻断工作；8秒后再检查 ${ordered.first().event.name}")
        }
        return null
    }

    /** True means the task is owned here, including a task still working. */
    fun work(id:String,j:LivingVillage.Job,at:Point,dt:Float):Boolean {
        val event=j.event
        if(j.kind in listOf("foodPickup","foodDelivery"))return false
        if(event==null)return false
        val p=person(id)
        if(event in moments) {
            val other=j.peer?.let{village.residents[it]}
            val partner=j.peer?.let{village.jobs[it]}
            val duration=when(event){VillageEvent.GREET_NEIGHBOR->2.4f;VillageEvent.CHAT_TOGETHER->4f;else->3.2f}
            if(j.peer!=null&&(other==null||at.distance(other)>.075f||partner?.peer!=id||partner.event!=event)) {
                village.jobs.remove(id);retryAt[id]=clock+2f
                return true
            }
            if(j.kind=="life_reply")return true
            if(j.elapsed<duration)return true
            // Resolve the pair together; a partner interrupted by urgent work
            // must not gain a reward from an abandoned conversation.
            for(who in listOfNotNull(id,j.peer)) {
                val person=person(who)
                person.lastSocial=clock
                person.fatigue=(person.fatigue-.035f).coerceAtLeast(0f)
                if(j.peer!=null) {
                    person.lonely=(person.lonely-if(event==VillageEvent.CHAT_TOGETHER).3f else .1f).coerceAtLeast(0f)
                    val k=key(who,if(who==id)j.peer else id);bonds[k]=((bonds[k]?:0f)+.025f).coerceAtMost(1f)
                }
                village.jobs.remove(who);retryAt[who]=clock+1.5f;village.completedTasks++
            }
            emit(event,id,when(event) {
                VillageEvent.GREET_NEIGHBOR->"两人原地转身挥手，记住邻居；孤独减轻"
                VillageEvent.CHAT_TOGETHER->"两人停下交谈后恢复工作；关系加深"
                else->"放松腰背后恢复工作；疲劳略降"
            })
            return true
        }
        // A neighbour may have walked away since the original path was chosen.
        // Social and delivery effects require actual proximity to that neighbour.
        val collect=j.peer!=null&&!j.carrying&&event in setOf(
            VillageEvent.SHARE_MEAL,VillageEvent.REPAY_HELP,VillageEvent.INVITE_MEAL,VillageEvent.SHARE_WATER)
        val peerPosition=j.peer?.let{village.residents[it]}
        if(event==VillageEvent.THANK_HELPER&&
            (peerPosition==null||at.distance(peerPosition)>.075f||j.peer?.let{village.jobs[it]?.path?.isNotEmpty()}==true)) {
            village.jobs.remove(id);retryAt[id]=clock+2f
            return true
        }
        if(j.peer!=null&&!collect&&j.kind!="life_guiding"&&event!=VillageEvent.KEEP_LAST_MEAL&&event!=VillageEvent.GUIDE_CARRIER) {
            if(peerPosition==null)return true
            if(at.distance(peerPosition)>.023f) {
                val path=world.navigation.path(at,peerPosition,world.residentRouteAllowed(id))
                if(path!=null)village.jobs[id]=j.copy(path=path.toMutableList(),goal=peerPosition,elapsed=0f)
                else{village.interrupt(id,at);retryAt[id]=clock+2f}
                return true
            }
        }
        if(event in setOf(VillageEvent.EAT_FORAGE,VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL)) {
            val b=j.food
            if(b==null) {
                if(event==VillageEvent.EAT_FORAGE||village.ecology.food<=0) {
                    village.interrupt(id,at);return true
                }
                // Withdraw one portion only after arrival. The same physical
                // bundle survives an interruption and is consumed after eating.
                val portion=LivingVillage.FoodBundle(at,id,origin="stock")
                village.ecology.food--;village.foodBundles+=portion
                village.jobs[id]=j.copy(goal=at,elapsed=0f,carrying=true,food=portion)
                village.changed()
                return true
            }
            if(b.carrier!=id||village.foodBundles.none{it===b}||
                (!j.carrying&&at.distance(b.point)>=village.workReach(id,j))) {
                village.interrupt(id,at);return true
            }
            if(!j.carrying) {
                b.point=at
                village.jobs[id]=j.copy(goal=at,elapsed=0f,carrying=true)
                cargoMemory.remove(id);village.changed()
                return true
            }
        }
        val required=when(event) {
            VillageEvent.SLEEP_HOME,VillageEvent.SLEEP_LONGHOUSE->10f
            VillageEvent.REST_SHADE->7f
            VillageEvent.DRINK_RIVER,VillageEvent.DRINK_JAR,VillageEvent.ALTERNATE_BANK->1.5f
            VillageEvent.KEEP_LAST_MEAL,VillageEvent.RESUME_RECOVERED->.7f
            else->4f
        }
        if(j.elapsed<required)return true
        fun finish(result:String,changed:Boolean=true) {
            village.jobs.remove(id);village.completedTasks++
            j.peer?.let { follower->
                if(companions[follower]==id){companions.remove(follower);if(village.jobs[follower]?.kind=="life_follow")village.jobs.remove(follower)}
            }
            retryAt[id]=clock+1f
            if(changed)emit(event,id,result)
        }
        fun grain(origin:String):Boolean {
            if(p.hunger<=.8f&&village.ecology.food+village.foodBundles.size>=village.ecology.foodCapacity)return false
            val path=if(world.dangerous(world.depot))null else
                world.navigation.path(at,world.depot,world.residentRouteAllowed(id))
            val eatHere=p.hunger>.8f
            val b=LivingVillage.FoodBundle(at,if(path!=null||eatHere)id else null,origin=origin)
            village.foodBundles+=b;foodProduced++
            if(eatHere) {
                village.jobs[id]=LivingVillage.Job("life_${VillageEvent.EAT_FORAGE.name}",null,
                    mutableListOf(),at,carrying=true,food=b,event=VillageEvent.EAT_FORAGE)
                emit(event,id,"采下一份野食；饥饿的采集者先在安全处进食")
            } else if(path==null) {
                // A blocked return route cannot undo completed harvesting.
                // Leave finite, recoverable cargo here; the depot gains nothing
                // until the existing pickup and delivery jobs really bring it back.
                cargoMemory[id]=at
                finish("采下粮束暂放劳动处；待路线安全再搬回")
            } else {
                village.jobs[id]=LivingVillage.Job("foodDelivery",null,path.toMutableList(),world.depot,carrying=true,food=b,event=event)
                emit(event,id,"粮束出现在劳动处；必须搬回入库")
            }
            return true
        }
        fun wood():Boolean {
            val w=world.woods.firstOrNull{it.phase==WoodPhase.STACKED&&it.carrier==null}?:return false
            world.woods.remove(w);return true
        }
        fun routeNext(goal:Point,kind:String):Boolean {
            val open=village.nearestOpen(goal)?:return false
            val path=world.navigation.path(at,open,world.residentRouteAllowed(id))?:return false
            village.jobs[id]=j.copy(kind=kind,path=path.toMutableList(),goal=open,elapsed=0f,carrying=true)
            return true
        }
        fun carryWood(to:Point,kind:String):Boolean {
            val w=world.woods.firstOrNull{it.phase==WoodPhase.STACKED&&it.carrier==null}?:return false
            if(!routeNext(to,kind))return false
            w.carrier=id;w.phase=WoodPhase.CARRIED
            village.jobs[id]=village.jobs.getValue(id).copy(wood=w.id)
            return true
        }
        fun consumeCarriedWood():Boolean {
            val w=world.woods.firstOrNull{it.id==j.wood&&it.carrier==id&&it.phase==WoodPhase.CARRIED}?:return false
            world.woods.remove(w);return true
        }
        val peer=j.peer?.let{people[it]}
        val hm=j.house?.let{homes.getOrPut(it){Home()}}
        fun guide(destination:Point):Boolean {
            val follower=j.peer?:return false
            val partner=peerPosition?:return false
            if(j.kind=="life_guiding") {
                if(j.elapsed>45f){finish("同行路线已变化，重新安排",false);return false}
                val following=village.jobs[follower]
                if(companions[follower]!=id||following?.kind!="life_follow") {
                    finish("同行者已转去处理别的事情",false);return false
                }
                // Both bodies stop at collision-safe worksite edges. Settlement
                // must use those same reaches, including the follower's offset
                // goal, instead of waiting forever for a closer forbidden point.
                return at.distance(j.goal)<village.workReach(id,j)&&
                    partner.distance(following.goal)<village.workReach(follower,following)&&
                    partner.distance(at)<.045f&&!world.dangerous(partner)
            }
            val goal=village.nearestOpen(destination)?:return false
            val otherGoal=village.nearestOpen(Point(goal.x+.012f,goal.z))?:goal
            val ownPath=world.navigation.path(at,goal,world.residentRouteAllowed(id))?:return false
            val peerPath=world.navigation.path(partner,otherGoal,world.residentRouteAllowed(follower))?:return false
            village.interrupt(follower,partner)
            village.jobs[id]=j.copy(kind="life_guiding",path=ownPath.toMutableList(),goal=goal,elapsed=0f)
            village.jobs[follower]=LivingVillage.Job("life_follow",null,peerPath.toMutableList(),otherGoal)
            companions[follower]=id
            return false
        }
        when(event) {
            VillageEvent.EAT_FORAGE,VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL -> {
                val b=j.food
                if(j.carrying&&b!=null&&b.carrier==id&&village.foodBundles.any{it===b}&&
                    at.distance(b.point)<village.workReach(id,j)) {
                    village.foodBundles.removeAll{it===b};foodEaten++;p.meals++;p.lastMeal=clock
                    p.hunger=(p.hunger-if(event==VillageEvent.RATION_MEAL).42f else .68f).coerceAtLeast(0f)
                    cargoMemory.remove(id)
                    if(reservedFor==id)reservedFor=null
                    if(village.ecology.food>=4)ration=false
                    finish("吃完手中一份食物；饥饿=${p.hunger}")
                } else {
                    if(b?.carrier==id)b.carrier=null
                    finish("食物已不在身边，重新寻找",false)
                }
            }
            VillageEvent.KEEP_LAST_MEAL->{reservedFor=j.peer;finish("最后一份留给${j.peer}")}
            VillageEvent.DRINK_RIVER,VillageEvent.DRINK_JAR,VillageEvent.ALTERNATE_BANK->{
                if(event==VillageEvent.DRINK_JAR&&water<.25f){finish("储水已被取走",false);return true}
                if(event==VillageEvent.DRINK_JAR)water-=.25f
                p.thirst=(p.thirst-.75f).coerceAtLeast(0f);p.drinks++;finish("口渴=${p.thirst}")
            }
            VillageEvent.REST_SHADE,VillageEvent.SLEEP_HOME,VillageEvent.SLEEP_LONGHOUSE->{
                p.fatigue=(p.fatigue-if(event==VillageEvent.REST_SHADE).5f else .8f).coerceAtLeast(0f)
                p.fear=(p.fear-.2f).coerceAtLeast(0f)
                if(event==VillageEvent.SLEEP_LONGHOUSE)p.hostedBy="Longhouse"
                finish("休息恢复；疲劳=${p.fatigue}")
            }
            VillageEvent.DRY_CLOTHES->{p.wet=(p.wet-.65f).coerceAtLeast(0f);finish("衣物晾干；湿度=${p.wet}")}
            VillageEvent.WARM_AT_HEARTH->{p.wet=0f;p.fatigue=(p.fatigue-.15f).coerceAtLeast(0f);hearthFuel=(hearthFuel-.08f).coerceAtLeast(0f);finish("取暖消耗火塘燃料")}
            VillageEvent.RESUME_RECOVERED->{p.interrupted=false;retryAt.remove(id);finish("恢复工作资格")}
            VillageEvent.FORAGE_BERRIES->{
                val patch=patches.minByOrNull{it.point.distance(at)}
                if(patch!=null&&patch.point.distance(at)<.025f&&patch.berries>=1f&&grain("berries")){patch.berries--;patch.pressure++;p.farmSkill=(p.farmSkill+.025f).coerceAtMost(1f)}
                else finish("果地暂不可采",false)
            }
            VillageEvent.PICK_SEEDHEADS->{
                val patch=world.wetSpots.minByOrNull{it.point.distance(at)}
                if(patch!=null&&patch.growth>.55f&&grain("grass"))patch.growth=(patch.growth-.5f).coerceAtLeast(0f)
                else finish("草籽未熟",false)
            }
            VillageEvent.CUT_RIPE_CROP,VillageEvent.HARVEST_TOGETHER->{
                if(village.ecology.crop>=.5f&&grain("crop")) {
                    village.ecology.crop=(village.ecology.crop-.24f).coerceAtLeast(.05f)
                    soilFertility=(soilFertility-.08f).coerceAtLeast(0f);cropCycles++
                    p.farmSkill=(p.farmSkill+.06f).coerceAtMost(1f)
                    if(j.peer!=null)help(j.peer,id)
                }else finish("没有可割熟粮",false)
            }
            VillageEvent.DRY_WET_GRAIN->{dampGrain=(dampGrain-.55f).coerceAtLeast(0f);finish("湿粮摊干")}
            VillageEvent.PRESERVE_GRAIN->{preservation=1f;hearthFuel=(hearthFuel-.12f).coerceAtLeast(0f);dampGrain=(dampGrain-.25f).coerceAtLeast(0f);finish("燃料换取粮食保存")}
            VillageEvent.COMPOST_SPOILED,VillageEvent.COMPOST_SCAR->{
                if(spoiled>0){spoiled--;soilFertility=(soilFertility+.2f).coerceAtMost(1f)
                    if(event==VillageEvent.COMPOST_SCAR)village.ecology.scarAge+=35f
                    finish("腐粮-1；土壤肥力=${soilFertility}")
                }else finish("没有腐粮",false)
            }
            VillageEvent.TURN_STOCK->{stockTurn=1f;dampGrain=(dampGrain-.15f).coerceAtLeast(0f);finish("翻粮防霉")}
            VillageEvent.RESCUE_STOCK,VillageEvent.STORE_GRANARY->{
                if(!j.carrying) {
                    val destination=if(completed("Granary"))village.ecology.sites.getValue("Granary")
                        else village.houses.values.firstOrNull{intact(it)&&!world.dangerous(it.workPoint)}?.workPoint
                    if(destination!=null&&village.ecology.food>foodInGranary&&routeNext(destination,"life_stockDelivery")){
                        village.ecology.food--;val b=LivingVillage.FoodBundle(at,id,wet=dampGrain,origin="stock")
                        village.foodBundles+=b;village.jobs[id]=village.jobs.getValue(id).copy(food=b)
                    }else finish("没有可转移粮或安全储存处",false)
                }else if(j.food!=null&&village.foodBundles.remove(j.food)){
                    village.ecology.food++
                    if(completed("Granary")&&at.distance(village.ecology.sites.getValue("Granary"))<.025f)foodInGranary++
                    dampGrain=(dampGrain-.12f).coerceAtLeast(0f);preservation=maxOf(preservation,.3f)
                    finish("一束粮实际移至安全存放处")
                }else finish("转移粮已遗落",false)
            }
            VillageEvent.AGREE_RATIONS->{ration=true;finish("居民开始小份进食")}
            VillageEvent.COOK_TOGETHER->{people.filter{it.value.hunger>.4f&&(village.residents[it.key]?.distance(at)?:1f)<.025f}.keys.take(maxOf(0,village.ecology.food-foodInGranary)).forEach{who->
                village.ecology.food--;foodEaten++;person(who).hunger=(person(who).hunger-.55f).coerceAtLeast(0f);person(who).meals++;if(who!=id)help(who,id)
            };hearthFuel=(hearthFuel-.1f).coerceAtLeast(0f);finish("共同用餐消耗实际存粮")}
            VillageEvent.RESERVE_SEEDS->{if(village.ecology.food>0){village.ecology.food--;foodInvested++;seeds++;finish("粮食-1；种粮+1")}else finish("粮食已取尽",false)}
            VillageEvent.FILL_JAR->{
                if(!j.carrying){if(!routeNext(world.depot,"life_jarDelivery"))finish("无法搬水回村",false)}
                else{water=(water+.8f).coerceAtMost(2f);finish("河水搬回后补入公共罐")}
            }
            VillageEvent.SHARE_WATER->{
                if(peer==null){finish("邻居不在",false)}
                else if(!j.carrying) {
                    if(water>=.4f&&routeNext(village.residents[j.peer]?:at,"life_waterDelivery"))water-=.4f else finish("无法送水",false)
                }else{peer.thirst=(peer.thirst-.7f).coerceAtLeast(0f);peer.drinks++;help(requireNotNull(j.peer),id);finish("送水；建立互助记忆")}
            }
            VillageEvent.WATER_FIELD,VillageEvent.WATER_SEEDLINGS->{
                if(!j.carrying){if(!routeNext(village.ecology.field,"life_fieldWater"))finish("无法到田",false)}
                else{fieldWater=(fieldWater+.4f).coerceAtMost(1f);village.ecology.crop=(village.ecology.crop+.035f).coerceAtMost(1f);finish("提水到田才浇灌")}
            }
            VillageEvent.WASH_MUD->{p.mud=0f;finish("泥污洗净，步速恢复")}
            VillageEvent.CATCH_RAIN->{
                if(j.carrying) {
                    water=(water+.5f).coerceAtMost(2f)
                    finish("接到的雨水实际搬回后补入公共罐")
                } else if(world.hazards.any{it.kind==Kind.RAIN&&it.strength>.05f&&it.point.distance(at)<it.radius+.035f}) {
                    if(!routeNext(world.depot,"life_rainDelivery"))finish("暂时无法搬水回村",false)
                } else finish("雨已离开",false)
            }
            VillageEvent.DAMP_ROOF,VillageEvent.COOL_ASH->{
                if(!j.carrying) {
                    val destination=if(event==VillageEvent.DAMP_ROOF)j.house?.let{village.houses[it]?.workPoint}else village.ecology.scorch?.let{village.nearestOpen(it)}
                    if(destination!=null&&water>=.35f&&routeNext(destination,"life_protectWater"))water-=.35f
                    else finish("缺水或保护地点不可达",false)
                }else if(event==VillageEvent.DAMP_ROOF&&hm!=null){hm.damp=1f;finish("水实际送到棚顶；短时防火")}
                else if(event==VillageEvent.COOL_ASH&&village.ecology.scorch!=null){village.ecology.scarAge+=50f;finish("水实际送到余烬处；焦痕冷却")}
                else finish("保护地点已变化",false)
            }
            VillageEvent.CLEAR_BANK->{bankDebris=0f;finish("取水处畅通")}
            VillageEvent.ESCORT_WATER->{
                if(peer==null)finish("邻居不在",false)
                else if(guide(village.ecology.bank)){peer.fear=(peer.fear-.5f).coerceAtLeast(0f);peer.thirst=(peer.thirst-.5f).coerceAtLeast(0f);peer.drinks++;help(requireNotNull(j.peer),id);finish("两人实际结伴到河边取水")}
                else if(j.kind!="life_guiding"&&village.jobs[id]===j)finish("河边当前不可达",false)
            }
            VillageEvent.SOW_SEEDS,VillageEvent.REPLANT_GAP->{
                if(seeds>0){seeds--;village.ecology.crop=(village.ecology.crop+.24f).coerceAtMost(1f);trampled=(trampled-.25f).coerceAtLeast(0f);finish("种粮-1；田苗补齐")}else finish("种粮已用完",false)
            }
            VillageEvent.PULL_WEEDS->{weeds=(weeds-.55f).coerceAtLeast(0f);p.farmSkill=(p.farmSkill+.05f).coerceAtMost(1f);finish("除草降低争水")}
            VillageEvent.MULCH_FIELD->{if(reeds>0){reeds--;mulch=.9f;finish("芦苇-1；覆盖保湿")}else finish("芦苇已用完",false)}
            VillageEvent.LOOSEN_SOIL->{soilHard=(soilHard-.6f).coerceAtLeast(0f);finish("松土恢复根系生长")}
            VillageEvent.DRAIN_FIELD->{drainage=1f;fieldWater=(fieldWater-.4f).coerceAtLeast(0f);finish("积水排出")}
            VillageEvent.STAKE_CROP->{trampled=(trampled-.5f).coerceAtLeast(0f);village.ecology.crop=(village.ecology.crop+.06f).coerceAtMost(1f);finish("踩倒苗扶起")}
            VillageEvent.PICK_PESTS->{pests=(pests-.6f).coerceAtLeast(0f);finish("虫害减轻")}
            VillageEvent.CHECK_FENCE->{fenceWear=.5f;finish("发现缺口，转为修补需求")}
            VillageEvent.MEND_FENCE->{
                if(!j.carrying){if(!carryWood(village.ecology.sites.getValue("FieldFence"),"life_fenceDelivery"))finish("缺少可送木料",false)}
                else if(consumeCarriedWood()){fenceWear=0f;finish("木料-1；围栏闭合")}else finish("木料已遗落",false)
            }
            VillageEvent.REST_SOIL->{restingSoil=true;finish("暂停收割让土壤恢复")}
            VillageEvent.SAVE_OVERWATERED->{fieldWater=.5f;drainage=1f;village.ecology.crop=(village.ecology.crop+.04f).coerceAtMost(1f);finish("通根部排水救苗")}
            VillageEvent.PATCH_ROOF->{if(reeds>0&&hm!=null){reeds--;hm.leak=0f;finish("芦苇-1；漏雨消除")}else finish("无补顶材料",false)}
            VillageEvent.BRACE_HOUSE->{
                if(!j.carrying){val h=j.house?.let{village.houses[it]}
                    if(h==null||!carryWood(h.workPoint,"life_braceDelivery"))finish("缺少加固材料",false)
                }else if(hm!=null&&consumeCarriedWood()){hm.brace=1f;finish("木料-1；抗撞加固")}else finish("棚屋或木料不在",false)
            }
            VillageEvent.CLEAR_TRAIL->{trailDebris=0f;finish("小路落枝清理")}
            VillageEvent.FEED_HEARTH->{if(wood()){hearthFuel=(hearthFuel+.7f*(1f-woodWet*.7f)).coerceAtMost(1f);finish("木料-1；按柴干湿续燃")}else finish("无柴可添",false)}
            VillageEvent.COVER_WOOD->{if(reeds>0){reeds--;woodCover=.95f;finish("芦苇-1；木堆遮雨")}else finish("芦苇已用完",false)}
            VillageEvent.DRY_WOOD->{woodWet=(woodWet-.7f).coerceAtLeast(0f);finish("木料干燥")}
            VillageEvent.SALVAGE_TIMBER->{if(hm!=null&&hm.salvage>0&&world.dropWood(at)){hm.salvage--;village.collected++;finish("废棚回收一束可搬木")}else finish("没有可回收材料",false)}
            VillageEvent.MAKE_TOOL->{if(wood()){spareTools++;finish("木料-1；工具+1")}else finish("木料已用完",false)}
            VillageEvent.MEND_TOOL->{if(spareTools>0){spareTools--;p.tool=1f;finish("工具-1；劳动效率恢复")}else finish("工具已用完",false)}
            VillageEvent.BUILD_TOGETHER->{val h=j.peer?.let{village.jobs[it]?.house}?.let{village.houses[it]}
                if(h!=null&&h.materials>=h.needed&&h.stage=="build"){h.progress+=4f;p.buildSkill=(p.buildSkill+.05f).coerceAtMost(1f);help(requireNotNull(j.peer),id);finish("共同施工进度+4")}else finish("工地已完成或缺材",false)}
            VillageEvent.INSPECT_HOME->{hm?.inspected=1f;if(hm!=null)hm.leak=(hm.leak-.08f).coerceAtLeast(0f);finish("检查并处理小漏点",hm!=null)}
            VillageEvent.RETURN_HOST_SPACE->{p.hostedBy=null;finish("搬回完整住家")}
            VillageEvent.SHARE_MEAL,VillageEvent.REPAY_HELP,VillageEvent.INVITE_MEAL->{
                if(peer==null){finish("邻居不在",false)}
                else if(!j.carrying) {
                    if(village.ecology.food>0&&routeNext(village.residents[j.peer]?:at,"life_mealDelivery")) {
                        village.ecology.food--;val b=LivingVillage.FoodBundle(at,id,origin="meal")
                        village.foodBundles+=b;village.jobs[id]=village.jobs.getValue(id).copy(food=b)
                    }else finish("无法送饭",false)
                }else if(j.food!=null&&village.foodBundles.remove(j.food)){
                    foodEaten++;peer.meals++;peer.hunger=(peer.hunger-.7f).coerceAtLeast(0f);peer.lonely=(peer.lonely-.5f).coerceAtLeast(0f)
                    if(event==VillageEvent.REPAY_HELP){val k=key(id,requireNotNull(j.peer));debts[k]=((debts[k]?:0)-1).coerceAtLeast(0)}else help(requireNotNull(j.peer),id)
                    finish("送到邻居身旁才进食；记录互助")
                }else finish("饭已遗落",false)
            }
            VillageEvent.COMFORT_FEAR->{if(peer!=null){peer.fear=(peer.fear-.55f).coerceAtLeast(0f);help(requireNotNull(j.peer),id);finish("邻居恐惧减轻")}else finish("邻居不在",false)}
            VillageEvent.THANK_HELPER->{if(j.peer!=null){bonds[key(id,j.peer)]=.4f;p.lonely=(p.lonely-.2f).coerceAtLeast(0f);finish("当面感谢，关系加深")}else finish("帮助者不在",false)}
            VillageEvent.TEACH_FARM,VillageEvent.TEACH_BUILD->{if(peer!=null){
                val site=if(event==VillageEvent.TEACH_FARM)village.ecology.field
                    else j.house?.let{village.houses[it]?.workPoint}
                if(site==null)finish("工地已结束",false)
                else if(guide(site)){if(event==VillageEvent.TEACH_FARM)peer.farmSkill=(peer.farmSkill+.2f).coerceAtMost(p.farmSkill)else peer.buildSkill=(peer.buildSkill+.2f).coerceAtMost(p.buildSkill);help(requireNotNull(j.peer),id);finish("两人到劳动处传授经验")}
                else if(j.kind!="life_guiding"&&village.jobs[id]===j)finish("劳动现场不可达",false)
            }else finish("邻居不在",false)}
            VillageEvent.RELAY_WARNING,VillageEvent.RECALL_WORKER->{if(peer!=null){peer.warned=true;peer.fear=(peer.fear+.15f).coerceAtMost(1f);
                if(event==VillageEvent.RECALL_WORKER&&peerPosition!=null)village.interrupt(requireNotNull(j.peer),peerPosition)
                finish("兽情传到邻居，危险工作中断")}else finish("邻居不在",false)}
            VillageEvent.VISIT_DISPLACED,VillageEvent.KEEP_COMPANY->{if(peer!=null){peer.lonely=(peer.lonely-.5f).coerceAtLeast(0f);p.lonely=(p.lonely-.2f).coerceAtLeast(0f);help(requireNotNull(j.peer),id);finish("邻居得到陪伴")}else finish("邻居不在",false)}
            VillageEvent.WELCOME_GUEST->{if(peer!=null){
                val host=home(id)
                if(!intact(host))finish("借宿棚已经损坏",false)
                else if(guide(requireNotNull(host).workPoint)){peer.hostedBy=host.id;peer.fear=(peer.fear-.3f).coerceAtLeast(0f);help(requireNotNull(j.peer),id);finish("两人到完整棚下安排借宿")}
                else if(j.kind!="life_guiding"&&village.jobs[id]===j)finish("借宿处不可达",false)
            }else finish("邻居不在",false)}
            VillageEvent.SHELTER_FROM_RAIN->{p.wet=(p.wet-.45f).coerceAtLeast(0f);finish("到完整棚下躲雨")}
            VillageEvent.ESCORT_AFRAID->{if(peer!=null){
                val roof=village.houses.values.filter{intact(it)&&!world.dangerous(it.workPoint)}.minByOrNull{it.workPoint.distance(at)}
                if(roof==null)finish("没有安全棚可去",false)
                else if(guide(roof.workPoint)){peer.fear=(peer.fear-.55f).coerceAtLeast(0f);peer.warned=true;help(requireNotNull(j.peer),id);finish("两人走到安全棚下才恢复安心")}
                else if(j.kind!="life_guiding"&&village.jobs[id]===j)finish("避险路线不可达",false)
            }else finish("邻居不在",false)}
            VillageEvent.GUIDE_CARRIER->{safeTrail=at;finish("走到侧边让出搬运通道")}
            VillageEvent.WATCH_TIGER_PATH->{p.watch=1f;p.warned=true;village.ecology.guard=at;finish("留下实际看守位置")}
            VillageEvent.RELIEVE_WATCH->{if(peer!=null){peer.watch=0f;p.watch=1f;village.ecology.guard=at;help(requireNotNull(j.peer),id);finish("接替疲惫看守")}else finish("看守已离开",false)}
            VillageEvent.REUNITE_HOME->{if(peer!=null){
                val roof=home(requireNotNull(j.peer))
                if(!intact(roof))finish("邻居住家仍受损",false)
                else if(guide(requireNotNull(roof).workPoint)){peer.warned=false;peer.fear=0f;peer.hostedBy=null;help(j.peer,id);finish("陪邻居实际返回住家")}
                else if(j.kind!="life_guiding"&&village.jobs[id]===j)finish("返家路线不可达",false)
            }else finish("邻居不在",false)}
            VillageEvent.MARK_SAFE_TRAIL->{safeTrail=at;finish("亲自走通绕行路线")}
            VillageEvent.REOPEN_WORK->{p.warned=false;p.interrupted=false;blockedWork.remove(id);retryAt.remove(id);finish("危险解除，工作恢复")}
            VillageEvent.BAIT_ELEPHANT->{val patch=world.wetSpots.minByOrNull{it.point.distance(at)}
                if(patch!=null&&patch.growth>.4f){patch.growth-=.3f;cutGrass=at;grassBait=1f;finish("割草堆引象离开村边")}else finish("没有足够草",false)}
            VillageEvent.COLLECT_BRANCH->{val patch=patches.minByOrNull{it.point.distance(at)}
                if(patch!=null&&patch.point.distance(at)<.025f&&patch.branches>0&&world.dropWood(at)){patch.branches--;village.collected++;finish("落枝储量-1；拾成可搬运木束")}else finish("落枝已拾尽或不可达",false)}
            VillageEvent.REST_FORAGE_PATCH->{val patch=patches.minByOrNull{it.point.distance(at)}
                if(patch!=null&&patch.berries<1f){patch.resting=true;finish("暂停采这片野果")}else finish("果地仍可采",false)}
            VillageEvent.CUT_REEDS->{if(bankReeds>=1f&&reeds<6){bankReeds--;reeds++;bankDebris=(bankDebris+.04f).coerceAtMost(1f);finish("河边芦苇-1；采得一束盖草")}else finish("芦苇需要恢复",false)}
            VillageEvent.CLEAN_MUD_TRAIL->{trailMud=0f;p.mud=(p.mud-.2f).coerceAtLeast(0f);finish("整平泥路")}
            VillageEvent.CLEAR_SILT->{silt=0f;drainage=(drainage+.4f).coerceAtMost(1f);finish("田沟排水恢复")}
            VillageEvent.KEEP_GRASS_REFUGE->{refugeGrass=1f;village.ecology.recoveryUntil=world.seconds+60f;finish("村外草地留给动物，暂缓进田")}
            else->finish("由天气或动物实际位置结算",false)
        }
        return true
    }
}
