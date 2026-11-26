package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld.*
import kotlin.math.*

/** Additional animal routines request real movement; contact settles their consequences. */
internal class VillageWildlifeLife(private val world:LocalInterventionWorld,private val village:LivingVillage) {
    data class State(var thirst:Float=.25f,var fatigue:Float=.1f,var coat:Float=0f,
        var intent:VillageEvent?=null,var goal:Point?=null,var start:Point?=null,
        var elapsed:Float=0f,var cooldown:Float=0f,var reunions:Int=0,var drinks:Int=0,
        var fenceMemory:Float=0f,var carcassReturned:Boolean=false, var nextCalfAt:Float=0f)
    val states=linkedMapOf<String,State>()
    // Tests with a flat synthetic bank use the supplied point directly. In the
    // live valley, each animal has its own body-clearance graph and shoreline.
    var habitatGoal:(Body,VillageEvent,Point)->Point?={_,_,p->p}
    var excursionSite: (Body,List<Body>) -> Point? = { _,_ -> null }
    private var nextOuting = 0f
    private var outingEnd = 0f
    private var pasture:Point? = null

    fun calfLost(calf:Body, hunter:Body) {
        state(calf).nextCalfAt = life().clock + 30f
        state(hunter).carcassReturned = false
        pasture = null
    }

    var calfEntrySite: (Body, List<Body>) -> Point? = { _, _ -> null }
    private val grazed=mutableMapOf<Int,Float>()
    private val stompCooldown=mutableMapOf<String,Float>()
    private val active=mutableSetOf<String>()
    private fun life()=village.life
    private fun state(b:Body)=states.getOrPut(b.id){State()}
    private fun issue(b:Body,event:VillageEvent,goal:Point,activity:String="idle") {
        val s=state(b)
        val site=habitatGoal(b,event,goal)
        if(site==null){s.cooldown=8f;return}
        if(s.intent!=event){s.intent=event;s.start=b.point;s.elapsed=0f}
        s.goal=site
        world.wildlife.setOrder(b.id,Order(site,activity))
        active+=b.id
    }
    private fun away(from:Point,threat:Point):Point {
        val d=maxOf(.0001f,from.distance(threat))
        val target=Point(from.x+(from.x-threat.x)/d*.07f,from.z+(from.z-threat.z)/d*.07f)
        return world.navigation.points.asSequence().filter{it.distance(from)<.1f&&!world.weatherDangerous(it)}.minByOrNull{it.distance(target)}?:from
    }
    fun decide(dt:Float) {
        active.clear()
        if(!life().enabled)return
        val bodies=world.wildlife.bodies.values
        val dead = bodies.firstOrNull { it.species == Species.CALF && !it.alive }
        if (dead != null) {
            val hunters = bodies.filter { it.species==Species.TIGER && it.kills>0 }
            if (hunters.isNotEmpty() && hunters.all { state(it).carcassReturned }) dead.returnedToSoil=true
            val calfState = state(dead)
            // Older saves started a four-minute wait only after the carcass was consumed.
            if (calfState.nextCalfAt == 0f || calfState.nextCalfAt > life().clock + 30f)
                calfState.nextCalfAt = life().clock + 30f
            if (dead.returnedToSoil) {
                if (life().clock >= calfState.nextCalfAt) {
                    val entry = calfEntrySite(dead, bodies.filter { it.species == Species.ADULT && it.alive })
                    if (entry == null) calfState.nextCalfAt = life().clock + 1f
                    else {
                        dead.point=entry;dead.health=100f;dead.alive=true;dead.returnedToSoil=false
                        states[dead.id]=State()
                        nextOuting=life().clock+95f
                        world.tell("远处草丛里走来一头新的幼象，正往象群靠拢。")
                    }
                }
            }
        }
        val calf=bodies.firstOrNull{it.species==Species.CALF&&it.alive}
        val adults=bodies.filter{it.species==Species.ADULT&&it.alive}
        val tigers=bodies.filter{it.species==Species.TIGER&&it.alive}
        val parent=calf?.let{c->adults.minByOrNull{it.point.distance(c.point)}}
        val tigerThreat=calf?.let{c->tigers.any{it.hunger>.6f&&it.point.distance(c.point)<.13f}}
        val now=life().clock
        val leader=adults.minByOrNull { it.id }
        if(nextOuting==0f) nextOuting=now+70f
        if(calf==null || tigerThreat==true || world.weatherDangerous(calf.point) || now>=outingEnd) pasture=null
        if(calf!=null && parent!=null && adults.size>=2 && now>=nextOuting && pasture==null &&
            adults.all { it.point.distance(calf.point)<.115f } && tigerThreat!=true &&
            !world.weatherDangerous(calf.point) && state(calf).intent==null) {
            pasture=excursionSite(calf,adults)
            nextOuting=now+if(pasture==null)10f else 125f+(now.toInt()%24)
            outingEnd=now+38f
            if(pasture!=null)world.tell("小象循着嫩草的气味，独自往草甸深处走去。")
        }
        bodies.filter{it.species.persistentAnimal&&it.alive}.forEach{b->
            val s=state(b)
            s.thirst=(s.thirst+dt*.002f).coerceAtMost(1f)
            s.fatigue=(s.fatigue+dt*.0012f).coerceAtMost(1f)
            s.coat=(s.coat-dt*.0005f).coerceAtLeast(0f)
            s.cooldown=(s.cooldown-dt).coerceAtLeast(0f)
            s.fenceMemory=(s.fenceMemory-dt*.0002f).coerceAtLeast(0f)
            // Hunting, actual feeding and immediate defence retain precedence.
            if(b.feeding>0f||world.weatherDangerous(b.point)||tigerThreat==true&&b.species!=Species.TIGER){s.elapsed=0f;return@forEach}
            if(b.species==Species.TIGER&&world.wildlife.order(b.id)?.flee==true&&b.target==null){s.elapsed=0f;return@forEach}
            // Once the hunter closes on prey, a saved drinking/resting intent may
            // resume later. Leave the physical pursuit order intact until then.
            if (b.species==Species.TIGER && b.target?.let { bodiesTarget ->
                    world.wildlife.bodies[bodiesTarget]?.let { it.alive && it.point.distance(b.point)<.14f }
                } == true) { s.elapsed=0f;return@forEach }
            if(s.intent==VillageEvent.CALF_REJOIN) {
                if(parent==null){s.intent=null;s.goal=null}else s.goal=parent.point
            }
            if(s.intent==VillageEvent.CALF_REST_PROTECTED&&(parent==null||b.point.distance(parent.point)>.05f)) {
                s.intent=null;s.goal=null;s.elapsed=0f
            }
            if(s.intent==VillageEvent.SHARE_ELEPHANT_WATER&&parent==null){s.intent=null;s.goal=null}
            if(s.intent==VillageEvent.LEAD_CALF_WATER&&calf==null){s.intent=null;s.goal=null}
            if(s.intent!=null&&s.goal!=null&&s.cooldown<=0f) {
                world.wildlife.setOrder(b.id,Order(s.goal,if(s.intent in setOf(VillageEvent.ELEPHANT_MUD,VillageEvent.ELEPHANT_SHADE,VillageEvent.CALF_REST_PROTECTED))"rest"else"idle"))
                active+=b.id
                return@forEach
            }
            if(b===calf && pasture!=null) {
                world.wildlife.setOrder(b.id,Order(pasture,"graze",brisk=true))
                return@forEach
            }
            if(s.cooldown>0f)return@forEach
            when(b.species) {
                Species.CALF->when {
                    parent!=null&&b.point.distance(parent.point)>.075f->issue(b,VillageEvent.CALF_REJOIN,parent.point)
                    parent!=null&&s.thirst>.6f&&parent.point.distance(village.ecology.bank)<.035f->issue(b,VillageEvent.SHARE_ELEPHANT_WATER,village.ecology.bank)
                    parent!=null&&s.fatigue>.6f&&b.point.distance(parent.point)<.05f->issue(b,VillageEvent.CALF_REST_PROTECTED,b.point,"rest")
                }
                Species.ADULT-> {
                    val wet=world.wetSpots.filter{it.wet>.5f&&it.point.distance(b.point)<.2f}.minByOrNull{it.point.distance(b.point)}
                    val shade=life().patches.minByOrNull{it.point.distance(b.point)}?.point
                    val guards=life().people.filter{it.value.warned&&village.residents[it.key]?.distance(b.point)?.let{d->d<.11f}==true}
                    when {
                        guards.size>=2->issue(b,VillageEvent.ELEPHANT_AVOID_GUARD,away(b.point,village.residents.getValue(guards.keys.first())))
                        village.influence.meadow(b.point)!=null && s.thirst<.6f -> world.wildlife.setOrder(b.id,Order(village.influence.meadow(b.point),"graze"))
                        life().grassBait>.1f&&life().cutGrass!=null->world.wildlife.setOrder(b.id,Order(life().cutGrass,"graze"))
                        calf!=null&&parent==b&&state(calf).thirst>.6f-> {
                            issue(b,VillageEvent.LEAD_CALF_WATER,village.ecology.bank)
                            issue(calf,VillageEvent.SHARE_ELEPHANT_WATER,village.ecology.bank)
                        }
                        wet!=null&&s.coat<.2f&&s.fatigue>.3f->issue(b,VillageEvent.ELEPHANT_MUD,wet.point,"rest")
                        shade!=null&&s.fatigue>.65f->issue(b,VillageEvent.ELEPHANT_SHADE,shade,"rest")
                    }
                }
                Species.TIGER-> {
                    val other=tigers.firstOrNull{it.id!=b.id&&it.point.distance(b.point)<.04f&&(it.target!=null||it.hunger>b.hunger+.2f)}
                    val fence=village.houses["FieldFence"]?.takeIf{it.available&&!it.ruined&&life().fenceWear<.5f}
                    val goal=world.wildlife.order(b.id)?.point
                    when {
                        s.thirst>.75f->issue(b,VillageEvent.TIGER_DRINK,village.ecology.bank)
                        other!=null&&b.rest>0f->issue(b,VillageEvent.TIGER_YIELD,away(b.point,other.point))
                        fence!=null&&goal!=null&&segmentDistance(fence.center,b.point,goal)<.026f-> {
                            val detour=Point(fence.center.x+if(b.point.x<fence.center.x)-.045f else .045f,fence.center.z+.045f)
                            issue(b,VillageEvent.TIGER_AVOID_FENCE,world.navigation.points.minByOrNull{it.distance(detour)}?:b.point)
                        }
                    }
                }
                else->Unit
            }
        }
        // Use one moving herd centre. Personal routines and active defence keep their orders.
        if(leader!=null && tigerThreat!=true) {
            val members=adults.filter { it!==leader } + listOfNotNull(calf)
            for(member in members) {
                if(member.id in active || member===calf && pasture!=null || world.weatherDangerous(member.point))continue
                val gap=member.point.distance(leader.point)
                val spacing=member.radius+leader.radius+if(member===calf).009f else .02f
                val order=if(gap>spacing+.012f) {
                    val ratio=spacing/gap
                    Order(Point(leader.point.x+(member.point.x-leader.point.x)*ratio,
                        leader.point.z+(member.point.z-leader.point.z)*ratio),"graze",brisk=true)
                } else Order(null,if(world.wildlife.order(leader.id)?.activity=="rest")"rest" else "graze")
                world.wildlife.setOrder(member.id,order)
            }
            if(adults.any { it.point.distance(leader.point)>.13f } && leader.id !in active)
                world.wildlife.setOrder(leader.id,Order(null,"graze"))
        }
    }
    fun settle(dt:Float) {
        if(!life().enabled)return
        world.wildlife.bodies.values.filter{it.species.persistentAnimal}.forEach{b->
            val s=state(b)
            val e=s.intent
            val goal=s.goal
            if(e!=null&&goal!=null&&b.alive&&b.id in active&&
                world.wildlife.order(b.id)?.point==goal&&!world.weatherDangerous(b.point)&&b.feeding<=0f) {
                val reach=if(e==VillageEvent.CALF_REJOIN).045f else .016f+b.radius
                if(b.point.distance(goal)<reach) {
                    s.elapsed+=dt
                    val duration=if(e in setOf(VillageEvent.ELEPHANT_MUD,VillageEvent.ELEPHANT_SHADE,VillageEvent.CALF_REST_PROTECTED))5f else 1f
                    if(s.elapsed>=duration) {
                        when(e) {
                            VillageEvent.LEAD_CALF_WATER->{s.thirst=(s.thirst-.5f).coerceAtLeast(0f);s.drinks++}
                            VillageEvent.SHARE_ELEPHANT_WATER->{s.thirst=0f;s.drinks++}
                            VillageEvent.CALF_REJOIN->{s.reunions++;s.fatigue=(s.fatigue-.1f).coerceAtLeast(0f)}
                            VillageEvent.ELEPHANT_MUD->{s.coat=.9f;s.fatigue=(s.fatigue-.35f).coerceAtLeast(0f)}
                            VillageEvent.ELEPHANT_SHADE,VillageEvent.CALF_REST_PROTECTED->{s.fatigue=(s.fatigue-.65f).coerceAtLeast(0f)}
                            VillageEvent.TIGER_DRINK->{s.thirst=0f;s.drinks++;b.rest=maxOf(b.rest,4f)}
                            VillageEvent.TIGER_AVOID_FENCE->{s.fenceMemory=1f;b.attackTime=0f}
                            VillageEvent.TIGER_YIELD->{b.rest=maxOf(b.rest,5f)}
                            VillageEvent.ELEPHANT_AVOID_GUARD->{village.ecology.recoveryUntil=world.seconds+20f;b.goal=null}
                            else->Unit
                        }
                        life().emit(e,b.id,"实际到达；饮水=${s.drinks}；疲劳=${s.fatigue}")
                        s.intent=null;s.goal=null;s.cooldown=20f;s.elapsed=0f
                    }
                }else s.elapsed=0f
            }else s.elapsed=0f
            if(b.species==Species.TIGER&&b.kills>0&&b.feeding<=0f&&!s.carcassReturned) {
                val dead=world.wildlife.bodies.values.firstOrNull{it.species==Species.CALF&&!it.alive}
                if(dead!=null) {
                    val patch=world.wetSpots.firstOrNull{it.point.distance(dead.point)<.04f}
                    if(patch!=null)patch.growth=(patch.growth+.25f).coerceAtMost(1f)
                    else if(world.wetSpots.size<24)world.wetSpots+=WetSpot(dead.point,0f,.25f)
                    dead.returnedToSoil=true;s.carcassReturned=true;life().emit(VillageEvent.RETURN_CARCASS_SOIL,b.id,"进食残留使草地生长")
                }
            }
            if(b.species==Species.ADULT&&b.alive&&village.agriculture&&b.point.distance(village.ecology.field)<.025f&&life().clock>=(stompCooldown[b.id]?:0f)) {
                village.ecology.crop=(village.ecology.crop-.08f).coerceAtLeast(.05f)
                life().trampled=(life().trampled+.3f).coerceAtMost(1f)
                life().fenceWear=(life().fenceWear+.15f).coerceAtMost(1f)
                stompCooldown[b.id]=life().clock+20f
                life().emit(VillageEvent.CROP_TRAMPLED,b.id,"真实象足踩田；需要扶苗和修栏")
            }
        }
        world.wetSpots.forEachIndexed{i,p->
            val previous=grazed[i]
            if(previous!=null&&previous<.35f&&p.growth>.55f&&!world.weatherDangerous(p.point)) {
                // Recovered grazing actually changes the next feeding route away from the crop.
                world.wildlife.bodies.values.filter{it.species==Species.ADULT&&it.alive&&
                    !world.weatherDangerous(p.point)&&p.point.distance(village.ecology.field)>.05f}.forEach {
                    it.goal=p.point;it.decision=14f
                }
                life().emit(VillageEvent.MEADOW_RECOVER,i.toString(),"雨后草地从放牧后恢复")
                grazed[i]=p.growth
            }else grazed[i]=minOf(previous?:p.growth,p.growth)
        }
    }
    private fun segmentDistance(p:Point,a:Point,b:Point):Float {
        val dx=b.x-a.x;val dz=b.z-a.z
        val t=if(dx*dx+dz*dz<.000001f)0f else (((p.x-a.x)*dx+(p.z-a.z)*dz)/(dx*dx+dz*dz)).coerceIn(0f,1f)
        return p.distance(Point(a.x+t*dx,a.z+t*dz))
    }
}
