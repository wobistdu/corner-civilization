package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.*
import kotlin.math.*

/** Persistent consequences with interruptible, physically travelled work; no SDK objects. */
internal class LivingVillage(private val world: LocalInterventionWorld) {
    data class House(val id:String,val center:Point,val radius:Float,val workPoint:Point,
        var health:Float=100f,var ruined:Boolean=false,var cleared:Float=0f,var materials:Int=0,
        var progress:Float=0f,var safeSeconds:Float=0f,var rebuilds:Int=0,var available:Boolean=true,
        var damageCause:String="none",var animalHits:Int=0) {
        val needed:Int get()=if(ruined)3 else 1
        val workRequired:Float get()=if(ruined)50f else 16f
        val stage:String get()=when {health>=99.99f->"intact";safeSeconds<3f->"unsafe";ruined&&cleared<6f->"clear";materials<needed->"supply";else->"build"}
    }
    data class Job(val kind:String,val house:String?,var path:MutableList<Point>,val goal:Point,
        val wood:Int?=null,var elapsed:Float=0f,var carrying:Boolean=false,val food:FoodBundle?=null,
        val event:VillageEvent?=null,val peer:String?=null,var replanAt:Float=0f,var blocked:Float=0f)
    data class FoodBundle(var point:Point,var carrier:String?=null,var wet:Float=0f,
        var age:Float=0f,val origin:String="forage")
    val foodBundles=mutableListOf<FoodBundle>()
    val restSeats=mutableMapOf<String,Point>()
    val houses=linkedMapOf<String,House>()
    val jobs=mutableMapOf<String,Job>()
    val residents=mutableMapOf<String,Point>()
    var delivered=0;var collected=0;var completedTasks=0;var harvests=0
    var agriculture=false
    var settled=false
    var revision=0L;private set
    private val resumeAt=mutableMapOf<String,Point>()
    private val idleUntil=mutableMapOf<String,Float>()
    private val routineTurn=mutableMapOf<String,Int>()
    private var forageCooldown=0f
    private var foodCooldown=0f
    val ecology = VillageEcology()
    val life = VillageLife(world,this)
    val animalLife = VillageWildlifeLife(world,this)
    fun enableExpansion() {
        ecology.sites.forEach { (name, center) ->
            val work = nearestOpen(Point(center.x,center.z+.011f)) ?: return@forEach
            addHouse(name,center,.011f,work)
            houses.getValue(name).available=false
        }
    }
    val sources=mutableListOf<Point>()
    fun addHouse(id:String,center:Point,radius:Float,workPoint:Point) {houses[id]=House(id,center,radius,workPoint)}
    fun impact(id:String,point:Point,bodyRadius:Float):Boolean {
        val h=houses[id]?:return false
        if(!h.available||h.ruined||point.distance(h.center)>h.radius+bodyRadius+.008f)return false
        h.health=(h.health-60f*(if(life.enabled)1f-life.impactResistance(h.id)else 1f)).coerceAtLeast(0f);h.damageCause="animal";h.animalHits++;h.safeSeconds=0f
        world.tell("猛犸撞上了${VillageChronicle.house(id)}，" + if (h.health == 0f) "屋架倒了。" else "棚柱歪了。")
        if(h.health==0f){h.ruined=true;h.cleared=0f;h.materials=0;h.progress=0f}
        revision++
        return true
    }
    fun nearestOpen(p:Point):Point?=world.navigation.points.minByOrNull{it.distance(p)}
    private fun safe(p:Point)=!world.dangerous(p)
    private fun route(a:Point,b:Point)=world.navigation.path(a,b){safe(it)}
    fun advance(dt:Float) {
        if(life.enabled) life.advance(dt)
        if(agriculture&&(!life.enabled||!life.restingSoil)) ecology.grow(dt, world.wetAt(ecology.field))
        ecology.scarAge += dt
        world.hazards.firstOrNull { it.kind==Kind.FIRE }?.let { ecology.scorch=it.point;ecology.scarAge=0f }
        if(!life.enabled && agriculture && houses.values.none { it.available && it.health<99.99f }) {
            val next=ecology.sites.keys.firstOrNull { houses[it]?.available==false }
            val cost=if(next=="FieldFence" && ecology.raids>0) 1 else 3
            if(next!=null && ecology.food>=cost && world.woods.any { it.phase==WoodPhase.STACKED }) {
                houses.getValue(next).apply { available=true;health=0f;ruined=true;cleared=6f }
                ecology.food-=cost
            }
        }
        forageCooldown=(forageCooldown-dt).coerceAtLeast(0f)
        foodCooldown=(foodCooldown-dt).coerceAtLeast(0f)
        houses.values.filter{it.available}.forEach { h ->
            val heat=world.hazards.filter{it.kind==Kind.FIRE && (it.house==h.id || (it.house==null && it.point.distance(h.center)<h.radius+it.radius*.55f))}.maxOfOrNull{it.strength}?:0f
            if(heat>.025f) {
                h.damageCause="fire"
                val old=h.health;h.health=(h.health-8f*heat*dt*(if(life.enabled)1f-life.roofResistance(h.id)else 1f)).coerceAtLeast(0f);h.safeSeconds=0f
                if(h.health==0f&&!h.ruined){world.tell("${VillageChronicle.house(h.id)}在火里塌了。");h.ruined=true;h.cleared=0f;h.materials=0;h.progress=0f;revision++}
                if(h.health!=old)revision++
            } else if(safe(h.workPoint))h.safeSeconds+=dt else h.safeSeconds=0f
            if(h.health<99.99f&&h.materials>=h.needed&&h.progress>=h.workRequired&&h.safeSeconds>=3f) {
                world.tell("工人收起工具，${VillageChronicle.house(h.id)}修好了。")
                h.health=100f;h.ruined=false;h.cleared=0f;h.materials=0;h.progress=0f;h.damageCause="none";h.rebuilds++;revision++
            }
        }
    }
    fun interrupt(id:String,current:Point) {
        val j=jobs.remove(id)?:return
        if(life.enabled) life.interrupted(id,j,current)
        j.food?.let { if(j.carrying)it.point=current;it.carrier=null;revision++ }
        j.wood?.let { woodId ->world.woods.firstOrNull{it.id==woodId}?.let { w ->
            if(w.phase==WoodPhase.CARRIED){val at=world.woods.indexOf(w);world.woods[at]=w.copy(point=current,phase=WoodPhase.WAITING,carrier=null)} else w.carrier=null
        }}
        idleUntil[id]=world.seconds+1f
    }
    private fun choose(id:String,current:Point):Job? {
        if(life.enabled) return life.choose(id,current)
        val numbered=id.startsWith("PrimitiveResident_")
        val builder=!numbered || id.last() in listOf('3','6')
        val hauler=!numbered || id.last() in listOf('2','5','8')
        fun duty(kind:String, target:Point):Job? {
            val goal=nearestOpen(target) ?: return null
            if(!safe(goal)) return null
            return route(current,goal)?.let { Job(kind,null,it.toMutableList(),goal) }
        }
        val animal=ecology.tiger
        if(animal!=null && current.distance(animal)<.09f) {
            val guard=id.endsWith("4")
            duty(if(guard) "guard" else "shelter", if(guard) ecology.refuge else Point(-.18f,.26f))?.let { return it }
        }
        if(id.endsWith("1") && ecology.mammoth?.distance(ecology.field)?.let { it<.07f }==true)
            duty("shoo",ecology.field)?.let { return it }
        if(id.endsWith("7") && world.hazards.any { it.kind==Kind.FIRE && (it.house==null || it.heat<.7f) && it.strength>.1f } )
            duty("fetchWater",ecology.bank)?.let { return it }
        val repair=houses.values.filter{it.available&&it.health<99.99f&&it.safeSeconds>=3f&&safe(it.workPoint)}.sortedBy{it.health}
        for(h in repair) {
            val peers=jobs.values.filter{it.house==h.id}
            val workstation=nearestOpen(Point(h.workPoint.x+(if(id.hashCode()%2==0)-.005f else .005f),h.workPoint.z))?:h.workPoint
            if(builder && (h.stage=="clear"||h.stage=="build") && peers.count{it.kind=="clear"||it.kind=="build"}<2) {
                route(current,workstation)?.let{return Job(h.stage,h.id,it.toMutableList(),workstation)}
            }
            if(hauler && h.stage=="supply"&&h.materials+peers.count{it.kind=="supply"}<h.needed) {
                val pile=world.woods.firstOrNull{it.phase==WoodPhase.STACKED&&it.carrier==null}
                if(pile!=null&&safe(world.depot))route(current,world.depot)?.let {
                    pile.carrier=id;return Job("supply",h.id,it.toMutableList(),workstation,pile.id)
                }
            }
        }
        val supplier=id.takeLast(1) in listOf("0","1","4","7")
        if(supplier) {
            foodBundles.firstOrNull { it.carrier==null && safe(it.point) }?.let { bundle ->
                if(safe(world.depot)) route(current,bundle.point)?.let { path ->
                    bundle.carrier=id
                    return Job("foodPickup",null,path.toMutableList(),bundle.point,food=bundle)
                }
            }
            val reserved=jobs.values.count { it.kind=="gatherFood" }
            if(ecology.food+foodBundles.size+reserved<ecology.foodCapacity &&
                foodCooldown<=0f && reserved<2 && safe(world.depot)) {
                sources.sortedBy { it.distance(current) }.firstNotNullOfOrNull { source ->
                    if(!safe(source) || route(source,world.depot)==null) null
                    else route(current,source)?.let { source to it }
                }?.let { (source,path) -> return Job("gatherFood",null,path.toMutableList(),source) }
            }
            if(!agriculture && settled && ecology.food>=3 && jobs.values.none { it.kind=="cultivate" }) {
                duty("cultivate",ecology.field)?.let { return it }
            }
        }
        val expansionNeedsWood=agriculture && ecology.food>=3 && ecology.sites.keys.any { houses[it]?.available==false } && world.woods.isEmpty()
        if(hauler && (repair.any{it.materials<it.needed} || expansionNeedsWood) && world.woods.size<6 && forageCooldown<=0f && jobs.values.none{it.kind=="forage"}) {
            sources.sortedBy{it.distance(current)}.firstNotNullOfOrNull{p->if(safe(p))route(current,p)?.let{p to it}else null}?.let{(p,path)->
                return Job("forage",null,path.toMutableList(),p)
            }
        }
        // Unclaimed physical gifts are handled by the existing hauling pipeline before daily life.
        if(world.woods.any{it.phase==WoodPhase.WAITING&&it.carrier==null})return null
        if(id.takeLast(1) !in listOf("0","1","4","7"))return null
        val n=routineTurn.getOrDefault(id,0);routineTurn[id]=n+1
        val household=(id.last().digitToIntOrNull() ?: 0)/3
        val owner=houses["Shelter_$household"]
        if(owner!=null && owner.ruined) {
            val host=houses.values.firstOrNull { it.available && !it.ruined && it.id.startsWith("Shelter_") }
            if(host!=null && n%3==0) duty("shelter",host.workPoint)?.let { return it }
        }
        if(owner!=null && owner.rebuilds>0 && n%4==0) duty("homecoming",owner.workPoint)?.let { return it }
        val grown=world.wetSpots.filter{it.growth>.55f&&safe(it.point)}.minByOrNull{it.point.distance(current)}
        val (kind,desired)=when {
            n%4==0&&grown!=null->"harvest" to grown.point
            n%4==1&&agriculture->"tend" to Point(-.055f,.278f)
            n%4==2->"talk" to Point(-.06f+(id.last().code%3)*.009f,.205f)
            else->"rest" to (owner?.workPoint?:world.depot)
        }
        val goal=nearestOpen(desired)?:return null
        if(!safe(goal))return null
        return route(current,goal)?.let{Job(kind,null,it.toMutableList(),goal)}
    }
    /** Shared by movement and work settlement, so a stopped body can finish. */
    fun workReach(id:String,job:Job)=if(world.wildlife.bodies.containsKey(id)) {
        if(job.event==VillageEvent.SLEEP_HOME && job.house in restSeats) .0018f
        else if(life.enabled&&job.kind.startsWith("life_")&&job.wood==null).018f else .012f
    } else .004f
    /** Supplies can be taken from the edge of the common pile. Keep the
     * existing interaction reach and all live body/weather collision checks. */
    fun supplyPath(id:String,from:Point,goal:Point):List<Point>? {
        val allowed=world.residentRouteAllowed(id)
        if(allowed(goal))world.navigation.path(from,goal,allowed)?.let{return it}
        val reach=if(id in world.wildlife.bodies).012f else .004f
        val contacts=world.navigation.points.filter{it.distance(goal)<reach}
        return world.navigation.pathToAny(from,contacts.sortedBy{it.distance(goal)},allowed)
    }
    fun respond(id:String,current:Point,dt:Float):Motion? {
        residents[id]=current
        if(world.dangerous(current)){interrupt(id,current);return null}
        if(life.enabled&&life.reserveHearthBuilders&&id in listOf("PrimitiveResident_3","PrimitiveResident_6")&&
            id !in jobs&&!life.urgent(id))return null
        if(!life.enabled && jobs[id]?.kind in listOf("talk","rest","tend","harvest","homecoming") &&
            (ecology.tiger?.distance(current)?.let { it<.09f }==true ||
            id.endsWith("1") && ecology.mammoth?.distance(ecology.field)?.let { it<.040f }==true ||
            id.endsWith("7") && world.hazards.any { it.kind==Kind.FIRE && it.house==null })) {
            jobs.remove(id);idleUntil.remove(id)
        }
        var j=jobs[id]
        if(j==null) {
            val daily=life.enabled || id.takeLast(1) in listOf("0","1","4","7")
            if(world.seconds<(idleUntil[id]?:0f))return if(daily||id in resumeAt)Motion(current,false,false,false,"rest")else null
            j=choose(id,current)
            if(j==null && !daily && id in resumeAt) {
                val home=resumeAt.getValue(id)
                j=route(current,home)?.let{Job("return",null,it.toMutableList(),home)}
            }
            if(j==null)return if(daily||id in resumeAt)Motion(current,false,false,false,"rest")else null
            if(!daily&&id !in resumeAt)resumeAt[id]=current
            jobs[id]=j
        }
        val house=j.house?.let{houses[it]}
        if((house!=null&&j.kind in listOf("clear","build","supply")&&(house.safeSeconds<3f||house.health>=99.99f)) ||
            (j.kind=="cultivate" && (!settled || ecology.food<3 || agriculture)) ||
            (j.event==VillageEvent.SLEEP_HOME && house!=null && (!house.available || house.ruined || house.health<80f)) ||
            !safe(j.goal)) {
            if(life.enabled)life.defer(id,j)
            interrupt(id,current);return Motion(current,false,false,false)
        }
        val travelGoal=if(j.kind=="supply"&&!j.carrying)world.depot else j.goal
        fun replan(at:Point) {
            if(world.seconds<j.replanAt)return
            // A moving neighbour can temporarily occupy a path. Keep collision
            // checks live, but do not run a full graph search every frame.
            j.replanAt=world.seconds+.65f+(id.last().digitToIntOrNull()?:0)*.027f
            val path=if(j.kind=="supply")supplyPath(id,at,travelGoal)
                else world.navigation.path(at,travelGoal,world.residentRouteAllowed(id))
            path?.let{j.path=it.toMutableList()}
        }
        // A distant animal crossing the route is not danger at the carrier's
        // feet. Retain the physical cargo and seek a safe detour before giving up.
        if(j.path.any{!safe(it)}) {
            replan(current)
            if(j.path.any{!safe(it)}) {
                j.blocked+=dt
                if(j.blocked>6f){life.defer(id,j);interrupt(id,current)}
                return Motion(current,false,j.carrying,false,"rest")
            }
        }
        // Reach the edge of a worksite or shared pile, rather than forcing every
        // body to occupy its centre. Unregistered rule fixtures retain their strict reach.
        val reach=workReach(id,j)
        if(current.distance(travelGoal)<reach)j.path.clear()
        var p=current;var remaining=.016f*(if(life.enabled)life.speed(id)else 1f)*dt
        while(j.path.isNotEmpty()&&remaining>0f) {
            val target=j.path.first();val d=p.distance(target)
            if(d<.000001f){j.path.removeAt(0);continue}
            val travel=minOf(remaining,d)
            val next=Point(p.x+(target.x-p.x)*travel/d,p.z+(target.z-p.z)*travel/d)
            val actual=world.moveResident(id,next)
            remaining-=travel;p=actual
            if(actual.distance(next)>.000001f) {
                p=world.yieldResident(id,travelGoal,travel*.8f)
                replan(p)
                break
            }
            if(d<=travel+.000001f)j.path.removeAt(0)
        }
        j.blocked=if(p.distance(current)<.00005f && j.path.isNotEmpty())j.blocked+dt else 0f
        if(j.blocked>6f) {
            life.defer(id,j);interrupt(id,p)
            return Motion(p,false,false,false,"rest")
        }
        residents[id]=p
        if(j.carrying) j.food?.point=p
        if(j.path.isEmpty()) {
            if(p.distance(travelGoal)>=reach) {
                replan(p)
                return Motion(p,p.distance(current)>.000001f,j.carrying,false,"walk")
            }
            if(j.kind=="supply"&&!j.carrying) {
                val w=world.woods.firstOrNull{it.id==j.wood&&it.carrier==id}
                val path=supplyPath(id,p,j.goal)
                if(w==null||path==null){interrupt(id,p);return Motion(p,false,false,false)}
                w.phase=WoodPhase.CARRIED;j.carrying=true;j.path=path.toMutableList()
            } else if(j.kind=="supply") {
                val w=world.woods.firstOrNull{it.id==j.wood&&it.carrier==id&&it.phase==WoodPhase.CARRIED}
                if(w!=null&&house!=null){world.woods.remove(w);house.materials++;world.tell("一束木料送到了${VillageChronicle.house(house.id)}工地。");delivered++;revision++}
                jobs.remove(id);completedTasks++
            } else {
                j.elapsed+=dt
                if(life.enabled && life.work(id,j,p,dt)) {
                    // New tasks share the same physical travel and interruption contract.
                } else when(j.kind) {
                    "gatherFood" -> if(j.elapsed>=4f) {
                        val path=if(safe(world.depot))route(p,world.depot)else null
                        if(path!=null && ecology.food+foodBundles.size<ecology.foodCapacity) {
                            val bundle=FoodBundle(p,id);foodBundles+=bundle
                            jobs[id]=Job("foodDelivery",null,path.toMutableList(),world.depot,carrying=true,food=bundle)
                            foodCooldown=6f;revision++
                        } else jobs.remove(id)
                    }
                    "foodPickup" -> {
                        val path=if(safe(world.depot))route(p,world.depot)else null
                        if(path!=null && j.food?.carrier==id) {
                            jobs[id]=Job("foodDelivery",null,path.toMutableList(),world.depot,carrying=true,food=j.food)
                            if(life.enabled && j.event!=null)life.recoveredFood(id,j.event)
                        } else interrupt(id,p)
                    }
                    "foodDelivery" -> {
                        val bundle=j.food
                        if(bundle!=null && bundle.carrier==id && foodBundles.any { it===bundle } &&
                            p.distance(world.depot)<reach && ecology.food<ecology.foodCapacity) {
                            foodBundles.removeAll { it===bundle };ecology.food++;harvests++;world.tell("搬运人带回一束粮，粮仓又添了一份存粮。");revision++
                            if(life.enabled) life.deliveredFood(id,bundle)
                            jobs.remove(id);completedTasks++
                        } else interrupt(id,p)
                    }
                    "cultivate" -> {
                        ecology.cultivation=(ecology.cultivation+dt).coerceAtMost(12f);revision++
                        if(ecology.cultivation>=12f) { agriculture=true;jobs.remove(id);completedTasks++ }
                    }
                    "fetchWater" -> if(j.elapsed>=.6f) {
                        val fire=world.hazards.filter { it.kind==Kind.FIRE && (it.house==null || it.heat<.7f) }.minByOrNull { it.point.distance(p) }
                        jobs.remove(id)
                        if(fire!=null) {
                            val goal=world.navigation.points.filter { safe(it) && it.distance(fire.point)<fire.radius+.030f }.minByOrNull { it.distance(p) }
                            if(goal!=null) route(p,goal)?.let { jobs[id]=Job("quench",null,it.toMutableList(),goal,wood=null,elapsed=0f) }
                        }
                    }
                    "quench" -> { world.hazards.filter { it.kind==Kind.FIRE && it.point.distance(p)<it.radius+.031f }.minByOrNull { it.point.distance(p) }?.let { it.heat=(it.heat-.65f).coerceAtLeast(0f);world.tell("村人提水浇向火头，火势弱了下去。") }; jobs.remove(id) }
                    "shoo" -> { ecology.recoveryUntil=world.seconds+30f;jobs.remove(id) }
                    "guard" -> { ecology.guard=p;if(j.elapsed>6f)jobs.remove(id) }
                    "return"->{jobs.remove(id);resumeAt.remove(id)}
                    "clear"->{if(house!=null){house.cleared=(house.cleared+dt).coerceAtMost(6f);revision++};if(house==null||house.cleared>=6f)jobs.remove(id)}
                    "build"->{if(house!=null){house.progress+=dt*(if(life.enabled)life.workRate(id)else 1f);revision++};if(house==null||house.progress>=house.workRequired)jobs.remove(id)}
                    "forage"->if(j.elapsed>=4f){if(world.dropWood(p)){collected++;world.tell("采集的人在路边拢好了一束柴。")};forageCooldown=6f;jobs.remove(id);completedTasks++}
                    else->if(j.kind!="life_follow"&&j.elapsed>=when(j.kind){"rest"->7f;"talk"->4f;else->5f}) {
                        if(j.kind=="tend") ecology.harvest()
                        if(j.kind=="harvest"){world.wetSpots.minByOrNull{it.point.distance(p)}?.let{it.growth=(it.growth-.5f).coerceAtLeast(0f)};harvests++}
                        jobs.remove(id);idleUntil[id]=world.seconds+2f;completedTasks++
                    }
                }
            }
        }
        return Motion(p,p.distance(current)>.000001f,j.carrying,false,if(j.path.isEmpty())j.kind else "walk")
    }
    fun changed(){revision++}
}
