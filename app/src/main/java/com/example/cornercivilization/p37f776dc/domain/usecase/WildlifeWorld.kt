package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import kotlin.math.*

/** One body registry for every moving resident and animal, in PrimitiveWorld metres. */
internal class WildlifeWorld {
    enum class Species(val persistentAnimal:Boolean=false,val frightensResidents:Boolean=false) {
        RESIDENT, ADULT(true,true), CALF(true,true), TIGER(true,true), DEER
    }
    data class Body(val id:String, val species:Species, var point:Point, val radius:Float,
        var alive:Boolean=true, var health:Float=100f, var hunger:Float=0f,
        var rest:Float=0f, var feeding:Float=0f, var kills:Int=0, var impacts:Int=0) {
        val absent get() = returnedToSoil || (species==Species.RESIDENT && !alive)
        var returnedToSoil=false
        var target:String?=null
        var attackTime=0f
        var impactCooldown=0f
        var decision=0f
        var goal:Point?=null
    }
    data class Order(val point:Point?, val activity:String="idle", val flee:Boolean=false, val brisk:Boolean=false)
    data class SavedAnimal(val id:String,val alive:Boolean,val point:Point,val health:Float,
        val hunger:Float,val rest:Float,val feeding:Float,val kills:Int,val impacts:Int)
    val bodies=linkedMapOf<String,Body>()
    private val restored=mutableMapOf<String,SavedAnimal>()
    private val orders=mutableMapOf<String,Order>()
    var protections=0; private set
    private var guarding=false
    fun add(id:String,species:Species,point:Point,radius:Float):Body {
        require(id !in bodies && radius.isFinite() && radius>0f && point.x.isFinite() && point.z.isFinite())
        val saved=restored.remove(id)
        return Body(id,species,saved?.point?:point,radius).also { b->
            if(saved!=null) {
                b.alive=saved.alive;b.health=saved.health;b.hunger=saved.hunger;b.rest=saved.rest
                b.feeding=saved.feeding;b.kills=saved.kills;b.impacts=saved.impacts
            }
            bodies[id]=b
        }
    }
    fun save()=bodies.values.filter { it.species.persistentAnimal }.map {
        SavedAnimal(it.id,it.alive,it.point,it.health,it.hunger,it.rest,it.feeding,it.kills,it.impacts)
    }
    fun restore(saved:List<SavedAnimal>,open:(Point)->Boolean) {
        require(saved.size<=5 && saved.map{it.id}.distinct().size==saved.size)
        saved.forEach { s->
            require(s.id in listOf("MammothAdult_0","MammothAdult_1","MammothCalf","Sabertooth_0","Sabertooth_1"))
            require(open(s.point) && s.health.isFinite() && s.health in 0f..100f &&
                s.hunger.isFinite() && s.hunger in 0f..1f && s.rest.isFinite() && s.rest in 0f..60f &&
                s.feeding.isFinite() && s.feeding in 0f..12f && s.kills>=0 && s.impacts>=0)
            require(s.alive == (s.health>0f))
            require(s.alive || s.id=="MammothCalf")
        }
        saved.forEach { s->
            val b=bodies[s.id]
            if(b==null)restored[s.id]=s else {
                b.returnedToSoil=false;b.point=s.point;b.alive=s.alive;b.health=s.health;b.hunger=s.hunger;b.rest=s.rest
                b.feeding=s.feeding;b.kills=s.kills;b.impacts=s.impacts
                b.target=null;b.goal=null;b.attackTime=0f
            }
        }
    }
    /** Restore resident locations after animals, avoiding both saved and default spawn bodies. */
    fun restoreResidents(saved:Map<String,Point>,points:List<Point>):Map<String,Point> {
        require(saved.keys.all{bodies[it]?.species==Species.RESIDENT})
        require(saved.values.all{it.x.isFinite()&&it.z.isFinite()&&it in points})
        val placed=linkedMapOf<String,Point>()
        bodies.values.filter{it.species==Species.RESIDENT}.forEach { b->
            val preferred=saved[b.id]?:b.point
            val at=if(points.any{it.distance(preferred)<.004f}&&open(b.id,preferred))preferred
                else points.asSequence().filter{open(b.id,it)}.minByOrNull{it.distance(preferred)}
                    ?:error("No free resident restore position ${b.id}")
            b.point=at;placed[b.id]=at
        }
        return placed
    }
    fun frightening(p:Point)=bodies.values.any {
        it.alive && it.species.frightensResidents && p.distance(it.point)<it.radius+
            if(it.species==Species.TIGER).062f else .037f
    }
    fun escapeSafe(from:Point,p:Point)=bodies.values.none{b->
        if(!b.alive || !b.species.frightensResidents)return@none false
        val reach=b.radius+if(b.species==Species.TIGER).062f else .037f
        p.distance(b.point)<reach && (from.distance(b.point)>=reach ||
            p.distance(b.point)<from.distance(b.point)-.004f)
    }
    fun open(id:String,p:Point)=bodies[id]?.let { b->
        bodies.values.none { !it.absent && it.id!=id && p.distance(it.point)<b.radius+it.radius+.0006f }
    }?:true
    /** Swept discs, including large frame steps. Existing overlaps may only separate. */
    private fun segmentOpen(b:Body,from:Point,to:Point):Boolean {
        val dx=to.x-from.x;val dz=to.z-from.z;val length=dx*dx+dz*dz
        return bodies.values.none { other->
            if(other.id==b.id || other.absent)return@none false
            val r=b.radius+other.radius+.0006f
            val t=if(length==0f)0f else (((other.point.x-from.x)*dx+(other.point.z-from.z)*dz)/length).coerceIn(0f,1f)
            val nearest=Point(from.x+t*dx,from.z+t*dz).distance(other.point)
            val before=from.distance(other.point)
            if(before<r-.000001f) nearest<before-.000001f || to.distance(other.point)<=before
            else nearest<r-.000001f
        }
    }
    fun move(id:String,to:Point,terrain:(Point)->Boolean={true}):Point {
        val b=bodies[id]?:return to
        val from=b.point
        if(!to.x.isFinite()||!to.z.isFinite())return from
        val distance=from.distance(to)
        if(distance<.000001f)return from
        // Terrain and disc checks apply to the entire requested movement, not only its endpoint.
        val steps=ceil(distance/.0015f).toInt()
        var p=from
        for(i in 1..steps) {
            val f=i.toFloat()/steps
            val next=Point(from.x+(to.x-from.x)*f,from.z+(to.z-from.z)*f)
            if(!terrain(next)||!segmentOpen(b,p,next))break
            p=next
        }
        b.point=p
        return p
    }
    fun order(id:String)=orders[id]
    fun setOrder(id:String,order:Order){if(id in bodies)orders[id]=order}
    /** Decisions precede movement; consequences are settled afterwards at actual body positions. */
    fun decide(dt:Float,world:LocalInterventionWorld) {
        require(dt.isFinite()&&dt>=0f)
        orders.clear()
        val calf=bodies.values.firstOrNull{it.species==Species.CALF && it.alive}
        val adults=bodies.values.filter{it.species==Species.ADULT && it.alive}
        val tigers=bodies.values.filter{it.species==Species.TIGER && it.alive}
        val danger=calf?.let{c->tigers.filter{it.hunger>=.65f && it.feeding<=0f && it.point.distance(c.point)<.105f}.minByOrNull{it.point.distance(c.point)}}
        val protect=calf!=null && danger!=null && adults.any{it.point.distance(calf.point)<.12f}
        if(protect&&!guarding)protections++
        guarding=protect
        bodies.values.filter{it.species.persistentAnimal}.forEach{b->
            b.impactCooldown=(b.impactCooldown-dt).coerceAtLeast(0f)
            b.rest=(b.rest-dt).coerceAtLeast(0f)
            b.decision-=dt
        }
        fun away(b:Body,threat:Point):Point {
            val length=maxOf(.0001f,b.point.distance(threat))
            val desired=Point(b.point.x+(b.point.x-threat.x)/length*.09f,b.point.z+(b.point.z-threat.z)/length*.09f)
            return world.navigation.points.asSequence().filter{it.distance(b.point)<.13f && !world.weatherDangerous(it) && open(b.id,it)}
                .minByOrNull{it.distance(desired)}?:b.point
        }
        tigers.forEach{t->
            if(t.feeding>0f) {
                t.target=null
                t.feeding=(t.feeding-dt).coerceAtLeast(0f);t.hunger=0f
                if(t.feeding==0f)t.rest=30f
                orders[t.id]=Order(null,"graze");return@forEach
            }
            t.hunger=(t.hunger+dt*.008f).coerceAtMost(1f)
            val defender=adults.filter { a->a.point.distance(t.point)<.07f && calf?.point?.distance(a.point)?.let{it<.13f}==true }.minByOrNull{it.point.distance(t.point)}
            val guard=world.village?.ecology?.guard?.takeIf{it.distance(t.point)<.09f}
            if(defender!=null || guard!=null) {
                t.rest=6f;t.attackTime=0f;t.target=null
                orders[t.id]=Order(away(t,defender?.point?:requireNotNull(guard)),"rest",true)
            } else if(t.rest>0f) { t.target=null;orders[t.id]=Order(null,"rest") }
            else {
                val person = if(world.farBank(t.point)) bodies.values.asSequence()
                    .filter { it.species==Species.RESIDENT && it.alive && it.point.distance(t.point)<.082f && world.farBank(it.point) }
                    .minByOrNull { it.point.distance(t.point) } else null
                val nearCalf=calf?.takeIf { t.hunger>=.65f && it.point.distance(t.point)<.27f }
                val prey = if(person!=null && (nearCalf==null || person.point.distance(t.point)<nearCalf.point.distance(t.point))) person else nearCalf
                if (prey==null) { t.target=null;t.attackTime=0f;return@forEach }
                if (t.target!=prey.id) t.attackTime=0f
                t.target=prey.id
                val contact=t.radius+prey.radius+.0015f
                if(t.point.distance(prey.point)<=contact)orders[t.id]=Order(null,"idle")
                else {
                    val d=t.point.distance(prey.point)
                    orders[t.id]=Order(Point(prey.point.x+(t.point.x-prey.point.x)/d*contact,
                        prey.point.z+(t.point.z-prey.point.z)/d*contact),"idle",true)
                }
            }
        }
        if(calf!=null && danger!=null) orders[calf.id]=Order(away(calf,danger.point),"rest",true)
        adults.forEach{a->
            if(protect && calf!=null && danger!=null && a.point.distance(calf.point)<.12f) {
                val d=maxOf(.0001f,a.point.distance(danger.point))
                val contact=a.radius+danger.radius+.0018f
                orders[a.id]=Order(Point(danger.point.x+(a.point.x-danger.point.x)/d*contact,
                    danger.point.z+(a.point.z-danger.point.z)/d*contact),"idle")
            } else {
                val life=world.village?.ecology
                if(life!=null && (life.food>0 || world.wetSpots.any{it.growth>.4f})) {
                    if(a.decision<=0f) {
                        val diverted=life.recoveryUntil>world.seconds
                        val food=world.wetSpots.filter{it.growth>.4f && world.wildFoodAllowed(a,it.point) && !world.weatherDangerous(it.point) && (!diverted || it.point.distance(life.field)>.05f)}.maxByOrNull{it.growth}?.point
                        val target=food ?: if (diverted || world.wildMeadow != null) world.wildMeadow?.invoke(a) else if(world.village?.agriculture==true)life.field else world.depot
                        // Stop at a reachable contact point on the outside of a building.
                        a.goal=target
                        a.decision=14f
                    }
                    a.goal?.let{orders[a.id]=Order(it,"graze")}
                }
            }
        }
        world.village?.animalLife?.decide(dt)
    }
    fun settle(dt:Float,world:LocalInterventionWorld) {
        val calf=bodies.values.firstOrNull{it.species==Species.CALF && it.alive}
        bodies.values.filter{it.species==Species.TIGER && it.alive}.forEach{t->
            val person=bodies[t.target]?.takeIf { it.species==Species.RESIDENT && it.alive }
            if (person!=null && t.rest<=0f && t.feeding<=0f &&
                world.farBank(t.point) && world.farBank(person.point) &&
                !world.weatherDangerous(t.point) && !world.weatherDangerous(person.point) &&
                person.point.distance(t.point)<=person.radius+t.radius+.0032f) {
                world.loseResident(person)
                t.target=null;t.attackTime=0f;t.hunger=0f;t.rest=18f
                return@forEach
            }
            val protected=calf!=null && bodies.values.any{it.species==Species.ADULT && it.alive &&
                it.point.distance(calf.point)<.12f && it.point.distance(t.point)<.07f}
            val contact=calf!=null && calf.alive && t.target==calf.id && t.rest<=0f && t.feeding<=0f &&
                !protected && !world.weatherDangerous(t.point) &&
                t.point.distance(calf.point)<=t.radius+calf.radius+.0032f
            if(contact) {
                t.attackTime+=dt
                if(t.attackTime>=.7f && calf!=null) {
                    calf.health=(calf.health-40f).coerceAtLeast(0f);t.attackTime=0f
                    if(calf.health==0f){calf.alive=false;world.village?.animalLife?.calfLost(calf,t);world.tell("剑齿虎扑倒了幼象，在草丛中进食。");t.kills++;t.feeding=12f;t.hunger=0f;t.target=null}
                }
            } else t.attackTime=0f
        }
        bodies.values.filter{it.species==Species.ADULT && it.alive && it.impactCooldown<=0f}.forEach{a->
            val order=orders[a.id]
            val movingIntent=order?.point?.let{it.distance(a.point)>.003f}==true
            if(movingIntent) {
                val hit=world.village?.houses?.values?.filter { it.available && !it.ruined &&
                    a.point.distance(it.center)<=it.radius+a.radius+.008f }?.minByOrNull{it.center.distance(a.point)}
                if(hit!=null && world.village?.impact(hit.id,a.point,a.radius)==true) {
                    a.impacts++;a.impactCooldown=22f
                    a.goal=null;a.decision=8f
                    a.rest=3f
                    orders[a.id]=Order(null,"rest")
                }
            }
        }
        world.village?.animalLife?.settle(dt)
    }
}
