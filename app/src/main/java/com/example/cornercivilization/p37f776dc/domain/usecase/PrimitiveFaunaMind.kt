package com.example.cornercivilization.p37f776dc.domain.usecase

import kotlin.math.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Hazard

/** Deterministic local wildlife: a body-clearance navigation grid, in-place gaits and bounded wandering. */
internal class PrimitiveFaunaMind(val navigation: LocalNavigation, initial: Point, val clearance: Float,
    val walkSpeed: Float, val fleeSpeed: Float, private val seed: Int) {
    enum class Gait { IDLE, WALK, FLEE }
    var activity = "rest"; private set
    private var arrival = "rest"
    val home = initial
    var point = initial; private set
    var gait = Gait.IDLE; private set
    var heading = 0f; private set
    private var path = mutableListOf<Point>()
    private var pause = 2f + seed * .8f
    private var returning = false
    private var turn = 0
    private var replan = 0f
    private var missedOrders=0
    private var detour=false
    private var commanded=false
    private var commandFlee=false
    private var plannedCommand:Point?=null
    private var bodyOpen:(Point)->Boolean={true}
    fun bindBody(open:(Point)->Boolean){bodyOpen=open}
    fun restorePoint(p:Point) {require(navigation.open(p));point=p;path.clear()}
    var strideRate=1f
        private set
    fun command(order:WildlifeWorld.Order?,hazards:List<Hazard>) {
        // advance owns the active escape. Resetting its gait or first waypoint
        // for a food/shade order here pins an off-grid animal to the start cell.
        strideRate=if(order?.brisk==true)1.85f else 1f
        if(danger(point,hazards))return
        commanded=order!=null;commandFlee=order?.flee==true
        if(order==null){plannedCommand=null;missedOrders=0;detour=false;return}
        arrival=order.activity
        if(order.point==null){missedOrders=0;detour=false;plannedCommand=null;path.clear();gait=Gait.IDLE;activity=arrival;return}
        // Re-plan moving targets at a bounded frequency; never teleport to a new target.
        // Contact paths may end at a safe edge, away from the requested centre.
        // Comparing that edge with the centre replans a stationary destination
        // every .35s and sends slow animals back to the snapped start cell.
        if(replan<=0f && (path.isEmpty() || plannedCommand?.distance(order.point)?.let{it>.004f}!=false)) {
            val candidates=sequenceOf(order.point)+navigation.points.asSequence()
                .filter{it.distance(order.point)<maxOf(.035f,clearance*2f+.012f)}
                .sortedBy{it.distance(order.point)}.take(24)
            val goals=candidates.filter{navigation.open(it) && !danger(it,hazards) && bodyOpen(it)}.toList()
            val steps=goals.firstNotNullOfOrNull{directRoute(it,hazards)}?:
                navigation.pathToAny(point,goals){p->routeOpen(p,hazards)}
            missedOrders=if(steps==null)missedOrders+1 else 0
            detour=false
            if(steps==null && missedOrders>=2 && !danger(point,hazards)) {
                // Leave an unreachable assignment locally, preserving its real
                // target for later. Never claim to have eaten or drunk here.
                val local=navigation.pathToNearest(point,{it.distance(point)>=.023f},
                    {routeOpen(it,hazards) && !danger(it,hazards)})
                path=local.orEmpty().toMutableList()
                detour=path.isNotEmpty()
                arrival="idle"
            } else path=steps.orEmpty().toMutableList()
            plannedCommand=order.point
            replan=if(detour)8f else if(steps==null)2f else .35f
        }
        if(detour)arrival="idle"
        gait=if(commandFlee&&!detour)Gait.FLEE else Gait.WALK
    }
    fun danger(p: Point, hazards: List<Hazard>) = hazards.any { it.dangerous && p.distance(it.point) < it.radius + clearance + .009f }
    private fun routeOpen(p:Point,hazards:List<Hazard>)=bodyOpen(p) && hazards.none { it.dangerous &&
        p.distance(it.point)<it.radius+clearance+.009f && point.distance(it.point)>=it.radius+clearance+.009f }
    private fun directRoute(goal: Point, hazards: List<Hazard>): List<Point>? {
        if (!navigation.open(goal)) return null
        // A moving contact target is often between grid nodes. Near it, walking back
        // to the snapped start every re-plan makes a faster hunter lose its last millimetres.
        val distance=point.distance(goal)
        if(distance<.018f) {
            val count=ceil(distance/.0015f).toInt().coerceAtLeast(1)
            if((1..count).all { i->
                val f=i.toFloat()/count
                val p=Point(point.x+(goal.x-point.x)*f,point.z+(goal.z-point.z)*f)
                navigation.open(p) && bodyOpen(p) && !danger(p,hazards)
            })return listOf(goal)
        }
        return null
    }
    private fun route(goal:Point,hazards:List<Hazard>):List<Point>? {
        if(!navigation.open(goal))return null
        return directRoute(goal,hazards)?:navigation.path(point,goal){p->routeOpen(p,hazards)}
    }
    fun visit(target:Point, pose:String, hazards:List<Hazard>):Boolean {
        if(danger(point,hazards)) return false
        val goal=navigation.points.asSequence().filter { it.distance(target)<.035f && !danger(it,hazards) }.minByOrNull { it.distance(target) } ?: return false
        val steps=route(goal,hazards) ?: return false
        path=steps.toMutableList();arrival=pose;pause=8f;returning=false;gait=Gait.WALK
        return true
    }
    fun advance(dt: Float, hazards: List<Hazard>, food:List<Point> = emptyList(), water:List<Point> = emptyList(),
        move:((Point)->Point)?=null) {
        require(dt.isFinite() && dt>=0f)
        replan-=dt
        val threatened=danger(point,hazards)
        if(threatened && (!returning || gait!=Gait.FLEE || path.isEmpty() || (replan<=0f && danger(path.last(),hazards)))) {
            val escape=navigation.points.asSequence().filter { !danger(it,hazards) && it.distance(point)<.24f }
                .sortedBy { it.distance(point) }.take(100).mapNotNull { route(it,hazards) }.firstOrNull()
            path=escape?.toMutableList() ?: mutableListOf();gait=Gait.FLEE;returning=true;pause=2f;replan=.5f
        }
        if(!threatened && path.any {danger(it,hazards)}) {path.clear();pause=1f}
        if(path.isEmpty()) {
            gait=Gait.IDLE;activity=if(threatened)"idle" else arrival;pause-=dt
            if(pause<=0f && !threatened && !commanded) {
                val candidates=if(returning) listOf(home).filterNot {danger(it,hazards)} else {
                    val mode=(turn+seed)%4
                    arrival=when(mode){0->if(seed<3)"graze" else "idle";1->"drink";2->"rest";else->if(seed<3)"graze" else "idle"}
                    val target=when {
                        mode==1->water.filter{it.distance(home)<.22f}.minByOrNull{it.distance(point)}
                        seed<3&&mode==0->food.filter{it.distance(home)<.20f}.minByOrNull{it.distance(point)}
                        mode==2->home
                        else->null
                    }
                    val angle=(seed*1.7f+turn*2.399963f);val radius=.025f+(seed%3)*.006f
                    listOf(target?:Point(home.x+cos(angle)*radius,home.z+sin(angle)*radius)).filterNot {danger(it,hazards)}
                }
                val route=candidates.firstNotNullOfOrNull {route(it,hazards)}
                if(route!=null) {path=route.toMutableList();gait=Gait.WALK;returning=false}
                turn++;pause=if(arrival=="rest")9f+(seed%3) else 5.5f+(seed%3)
            }
        }
        var remaining=dt*(if(gait==Gait.FLEE) fleeSpeed else walkSpeed*strideRate)
        val before=point
        while(path.isNotEmpty() && remaining>0f) {
            val goal=path.first();val distance=point.distance(goal)
            val travel=minOf(remaining,distance,.002f)
            if(distance<.000001f) {path.removeAt(0);continue}
            val next=Point(point.x+(goal.x-point.x)*travel/distance,point.z+(goal.z-point.z)*travel/distance)
            if(!navigation.open(next)) {path.clear();pause=.8f;break}
            val actual=move?.invoke(next)?:next
            if(actual.distance(next)>.000001f) {point=actual;path.clear();pause=.8f;break}
            point=actual;remaining-=travel
            if(distance<=travel+.000001f)path.removeAt(0)
        }
        if(point.distance(before)>.000001f) heading=atan2(-(point.z-before.z),point.x-before.x)*180f/PI.toFloat()
        else gait=Gait.IDLE
    }
}
