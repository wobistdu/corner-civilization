package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import kotlin.math.*

/** Temporary riverbank visitors. Only travelled contact changes water/grass needs. */
internal class VisitingDeerLife(private val world:LocalInterventionWorld,private val banks:List<Point>) {
    enum class Intent { REST, DRINK, GRAZE, WANDER, FLEE }
    class State(val body:WildlifeWorld.Body,val home:Point,val seed:Int) {
        var thirst=.64f
        var hunger=.35f
        var intent=Intent.REST
        var goal:Point?=null
        var worked=0f
        var drinks=0
        var grazes=0
        var escapes=0
        var heading=0f
        var moving=false
        internal var retryAt=0f
        internal var turn=0
        internal var path=mutableListOf<Point>()
        internal var patch:LocalInterventionWorld.WetSpot?=null
        val activity:String get()=if(moving)if(intent==Intent.FLEE)"flee"else"walk"
            else if(worked>0f)when(intent){Intent.DRINK->"drink";Intent.GRAZE->"graze";else->"rest"}else"rest"
    }
    val states=linkedMapOf<String,State>()
    private var seconds=0f

    /** Called after saved people and persistent animals have acquired their positions. */
    fun add(id:String,preferred:Point,radius:Float,seed:Int):State? {
        require(id !in states && id !in world.wildlife.bodies && radius>0f&&radius.isFinite())
        val at=world.navigation.points.asSequence().filter { p->
            !world.dangerous(p)&&world.wildlife.bodies.values.all{it.returnedToSoil || p.distance(it.point)>=radius+it.radius+.0006f}
        }.minByOrNull{it.distance(preferred)}?:return null
        val body=world.wildlife.add(id,WildlifeWorld.Species.DEER,at,radius)
        return State(body,at,seed).also{states[id]=it}
    }
    // Inside weather, allow the graph connection and river detours. Once outside,
    // forbid re-entry; every movement still checks terrain and swept bodies.
    private fun escapeAllowed(s:State,from:Point,p:Point)=
        world.wildlife.open(s.body.id,p)&&world.wildlife.escapeSafe(from,p)&&world.hazards.none {
            it.dangerous&&p.distance(it.point)<it.radius+.008f&&
                from.distance(it.point)>=it.radius+.008f
        }
    private fun clear(s:State) {
        s.goal=null;s.path.clear();s.worked=0f;s.patch=null;s.intent=Intent.REST
    }
    private fun plan(s:State,threatened:Boolean) {
        s.retryAt=seconds+.9f+s.seed*.13f
        val at=s.body.point
        if(threatened) {
            s.worked=0f;s.patch=null
            if(s.intent!=Intent.FLEE) { s.escapes++;world.reportEscape(s.body.id,"小鹿",at) }
            s.intent=Intent.FLEE
            val path=world.navigation.pathToNearest(at,
                { !world.dangerous(it)&&it.distance(at)<.18f },
                { escapeAllowed(s,at,it) })
            s.path=path?.toMutableList()?:mutableListOf();s.goal=path?.last()
            return
        }
        val allow=world.residentRouteAllowed(s.body.id)
        fun route(targets:List<Point>,intent:Intent):Boolean {
            val path=world.navigation.pathToPreferred(at,targets.filter(allow),allow)?:return false
            s.path=path.toMutableList();s.goal=path.last();s.intent=intent;s.worked=0f
            return true
        }
        if(s.thirst>.62f&&route(banks.sortedBy{it.distance(at)}.take(12),Intent.DRINK))return
        if(s.hunger>.5f) {
            val patches=world.wetSpots.filter{it.growth>=.2f&&!world.dangerous(it.point)}
                .sortedBy{it.point.distance(at)}
            if(route(patches.map{it.point},Intent.GRAZE)) {
                s.patch=patches.first{it.point==s.goal}
                return
            }
        }
        val angle=s.seed*1.8f+s.turn*2.399963f
        val desired=Point(s.home.x+cos(angle)*.045f,s.home.z+sin(angle)*.045f)
        val nearby=world.navigation.points.asSequence().filter{it.distance(s.home)<.10f&&allow(it)}
            .sortedBy{it.distance(desired)}.take(8).toList()
        if(route(nearby,Intent.WANDER))s.turn++
    }
    fun advance(dt:Float) {
        require(dt.isFinite()&&dt>=0f)
        if(dt==0f)return
        seconds+=dt
        states.values.forEach { s->
            s.thirst=(s.thirst+dt*.003f).coerceAtMost(1f)
            s.hunger=(s.hunger+dt*.002f).coerceAtMost(1f)
            val before=s.body.point
            val threatened=world.dangerous(before)
            if(threatened) {
                s.worked=0f
                if(s.intent!=Intent.FLEE||s.path.isEmpty()&&seconds>=s.retryAt)plan(s,true)
            } else {
                if(s.intent==Intent.FLEE) {
                    clear(s);s.retryAt=seconds+1f
                }
                if(s.goal?.let{world.dangerous(it)}==true||
                    s.intent==Intent.GRAZE&&(s.patch !in world.wetSpots||(s.patch?.growth?:0f)<.2f)) {
                    clear(s);s.retryAt=seconds
                }
                if(s.goal==null&&seconds>=s.retryAt)plan(s,false)
                if(s.path.any{world.dangerous(it)}) {s.path.clear();s.worked=0f;s.goal=null}
            }
            var remaining=dt*(if(s.intent==Intent.FLEE).026f else .011f)
            while(s.path.isNotEmpty()&&remaining>0f) {
                val target=s.path.first();val at=s.body.point;val distance=at.distance(target)
                if(distance<.000001f){s.path.removeAt(0);continue}
                val travel=minOf(remaining,distance,.002f)
                val next=Point(at.x+(target.x-at.x)*travel/distance,at.z+(target.z-at.z)*travel/distance)
                val actual=world.wildlife.move(s.body.id,next) { p->
                    world.navigation.open(p)&&(if(threatened)escapeAllowed(s,before,p)else !world.dangerous(p))
                }
                remaining-=travel
                if(actual.distance(next)>.000001f){s.path.clear();s.goal=null;s.worked=0f;break}
                if(distance<=travel+.000001f)s.path.removeAt(0)
            }
            val at=s.body.point
            s.moving=at.distance(before)>.000001f
            if(s.moving)s.heading=atan2(-(at.z-before.z),at.x-before.x)*180f/PI.toFloat()
            val arrived=s.goal?.let{at.distance(it)<.006f}==true&&s.path.isEmpty()
            if(!threatened&&!s.moving&&arrived) {
                s.worked+=dt
                when(s.intent) {
                    Intent.DRINK->if(s.worked>=2f&&banks.any{it.distance(at)<.006f}) {
                        s.thirst=(s.thirst-.7f).coerceAtLeast(0f);s.drinks++;world.tell("一只鹿在岸边低下头，喝足了水。");clear(s);s.retryAt=seconds+3f
                    }
                    Intent.GRAZE->if(s.worked>=3f) {
                        val patch=s.patch
                        if(patch!=null&&patch in world.wetSpots&&patch.growth>=.2f&&patch.point.distance(at)<.006f) {
                            patch.growth-=.2f;s.hunger=(s.hunger-.4f).coerceAtLeast(0f);s.grazes++;world.tell("一只鹿在雨后长出的草斑上吃起了嫩草。")
                        }
                        clear(s);s.retryAt=seconds+3f
                    }
                    Intent.WANDER->if(s.worked>=5f){clear(s);s.retryAt=seconds}
                    else->Unit
                }
            } else s.worked=0f
        }
    }
    fun close() {
        states.values.forEach { s->if(world.wildlife.bodies[s.body.id]===s.body)world.wildlife.bodies.remove(s.body.id) }
        states.clear()
    }
}
