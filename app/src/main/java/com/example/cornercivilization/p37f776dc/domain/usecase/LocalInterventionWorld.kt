package com.example.cornercivilization.p37f776dc.domain.usecase

import kotlin.math.*

/** Short-lived spatial events. No mutations of the saved civilization's wood ledger. */
internal class LocalInterventionWorld(val navigation: LocalNavigation) {
    data class Point(val x: Float, val z: Float) {
        fun distance(p: Point):Float {
            // Local world coordinates are finite. Widen before squaring so the
            // hot collision/path predicate uses sqrt without Float overflow.
            val dx=(x-p.x).toDouble();val dz=(z-p.z).toDouble()
            return sqrt(dx*dx+dz*dz).toFloat()
        }
    }
    enum class Kind { RAIN, FIRE }
    data class Hazard(val id: Int, val kind: Kind, var point: Point, var y: Float, val radius: Float, var age: Float = 0f, var house: String? = null, var held: Boolean = false, var heat: Float = 1f) {
        val warnedActors = mutableSetOf<String>()
        val strength: Float get() = if (kind == Kind.RAIN) if (held || age < 15f) 1f else 0f else ((18f-age)/3f).coerceIn(0f,1f)*heat
        val dangerous: Boolean get() = strength > .05f
    }
    enum class WoodPhase { WAITING, CARRIED, STACKED, CONSUMED }
    data class Wood(val id: Int, val point: Point, var phase: WoodPhase = WoodPhase.WAITING, var carrier: String? = null, var stackedAge: Float = 0f)
    data class Motion(val point: Point, val moving: Boolean, val carrying: Boolean, val fleeing: Boolean, val action: String = "idle")
    private data class Task(val home: Point, var goal: Point, var path: MutableList<Point>, var kind: String, var wood: Int? = null,
        var replanAt:Float=0f,var blocked:Float=0f)
    var village: LivingVillage? = null
    var farBank: (Point) -> Boolean = { false }
    val returnAt = mutableMapOf<String,Float>()
    val enteredResidents = mutableSetOf<String>()
    fun loseResident(person: WildlifeWorld.Body) {
        if (!person.alive) return
        tasks.remove(person.id)?.let { releaseWood(it,person.point) }
        village?.interrupt(person.id,person.point)
        village?.residents?.remove(person.id)
        person.alive=false
        returnAt[person.id]=seconds+20f
        tell("河对岸的剑齿虎扑住了一位落单的村人。")
    }
    private fun returnResidents() {
        if (returnAt.isEmpty()) return
        val pending=returnAt.iterator()
        while (pending.hasNext()) {
            val item=pending.next()
            if (seconds<item.value) continue
            val id=item.key
            val allowed=residentRouteAllowed(id)
            // A blocked depot must not delay arrivals at a safe, distant edge.
            val edges=navigation.points.asSequence().filter { p ->
                (abs(p.x)>.40f || abs(p.z)>.292f) && p.distance(depot)>.12f &&
                    !farBank(p) && allowed(p) && !dangerous(p)
            }.sortedBy { it.distance(depot) }
            val path=edges.firstNotNullOfOrNull { edge ->
                navigation.pathToNearest(edge, { p ->
                    p.distance(edge)>.045f && p.distance(depot)<edge.distance(depot)-.02f
                }, allowed)?.let { listOf(edge)+it }
            }
            if (path==null) { item.setValue(seconds+1f);continue }
            val body=wildlife.bodies.getValue(id)
            val edge=path.first()
            body.point=edge;body.alive=true
            village?.residents?.set(id,edge)
            val inward=path.dropWhile { it.distance(edge)<.001f }.take(6)
            if (inward.isNotEmpty()) tasks[id]=Task(edge,inward.last(),inward.toMutableList(),"return")
            enteredResidents+=id
            pending.remove()
            tell("远处的小路上，又有一位村人走了进来。")
        }
    }
    var wildFoodAllowed: (WildlifeWorld.Body, Point) -> Boolean = { _, _ -> true }
    var wildMeadow: ((WildlifeWorld.Body) -> Point?)? = null
    val chronicle = VillageChronicle()
    fun tell(text: String, player: Boolean = false) = chronicle.append(village?.life?.clock ?: seconds, text, player)
    val wildlife=WildlifeWorld()
    val hazards = mutableListOf<Hazard>()
    val woods = mutableListOf<Wood>()
    data class WetSpot(val point: Point, var wet: Float = 0f, var growth: Float = 0f)
    val wetSpots = mutableListOf<WetSpot>()
    fun wetAt(p: Point) = wetSpots.maxOfOrNull { it.wet*(1f-it.point.distance(p)/.085f).coerceIn(0f,1f) } ?: 0f
    fun rainAt(p: Point) = hazards.filter { it.kind==Kind.RAIN }.maxOfOrNull { ((it.radius-p.distance(it.point))/.025f).coerceIn(0f,1f) } ?: 0f
    val depot = Point(-.088f,.227f)
    private val tasks = mutableMapOf<String,Task>()
    private val fleeRetryAt = mutableMapOf<String,Float>()
    private val woodRetryAt = mutableMapOf<String,Float>()
    private var sequence = 0
    var seconds = 0f; private set
    fun cast(kind: Kind, point: Point, y: Float, house: String? = null, radius: Float = .055f): Boolean {
        if (!point.x.isFinite() || !point.z.isFinite() || !y.isFinite()) return false
        if (hazards.count { it.kind==kind } >= 3) return false
        if (kind==Kind.RAIN) hazards.filter { it.kind==Kind.FIRE && it.point.distance(point)<it.radius+radius }.forEach { it.age=maxOf(it.age,15f) }
        hazards += Hazard(++sequence,kind,point,y,radius,house=house,heat=if(kind==Kind.FIRE)(1f-wetAt(point)*.85f)else 1f)
        return true
    }
    fun beginRain(point: Point, y: Float, house: String? = null): Int? {
        if (!cast(Kind.RAIN, point, y, house, .06f)) return null
        return hazards.last().also { it.held = true }.id
    }
    fun moveRain(id: Int, point: Point, y: Float, house: String? = null): Boolean {
        val rain = hazards.firstOrNull { it.id == id && it.kind == Kind.RAIN && it.held } ?: return false
        if (!point.x.isFinite() || !point.z.isFinite() || !y.isFinite()) return false
        rain.point = point; rain.y = y; rain.house = house
        hazards.filter { it.kind == Kind.FIRE && it.point.distance(point) < it.radius + rain.radius }
            .forEach { it.age = maxOf(it.age, 15f) }
        return true
    }
    fun endRain(id: Int, cancelled: Boolean = false) {
        val rain = hazards.firstOrNull { it.id == id && it.kind == Kind.RAIN && it.held } ?: return
        if (cancelled) {
            hazards.remove(rain)
            tell("你收回了手中的雨云。", true)
        } else {
            rain.held = false
            rain.age = 0f
            tell("你把雨云留在沙盘${VillageChronicle.place(rain.point)}，细雨就地落下。", true)
        }
    }

    fun dropWood(point: Point): Boolean {
        if (woods.count { it.phase!=WoodPhase.CONSUMED }>=6 || !navigation.open(point)) return false
        // Every accepted pile must have a route to the depot, including the shallow ford.
        if (navigation.path(point,depot) == null) return false
        woods += Wood(++sequence,point)
        return true
    }
    fun advance(dt: Float) {
        require(dt.isFinite() && dt>=0f)
        seconds+=dt
        // Continuous overlap also handles a new fire underneath an already fixed rain cloud.
        wetSpots.forEach { it.wet=(it.wet-dt/28f).coerceAtLeast(0f);it.growth=(it.growth+dt*(if(it.wet>.12f).032f else -.004f)).coerceIn(0f,1f) }
        hazards.filter { it.kind==Kind.RAIN }.forEach { rain ->
            val patch=wetSpots.minByOrNull { it.point.distance(rain.point) }?.takeIf { it.point.distance(rain.point)<.035f }
                ?: WetSpot(rain.point).also { if(wetSpots.size>=24)wetSpots.removeAt(0);wetSpots+=it }
            patch.wet=(patch.wet+dt*.8f).coerceAtMost(1f)
        }
        hazards.filter { it.kind==Kind.FIRE }.forEach { fire ->
            val coverage=rainAt(fire.point)
            fire.heat=(fire.heat-dt*(coverage*.5f+wetAt(fire.point)*.06f)).coerceAtLeast(0f)
        }
        hazards.forEach { it.age+=dt }; hazards.removeAll {
            if (it.strength > 0f) false else {
                val place = it.house?.let(VillageChronicle::house) ?: VillageChronicle.place(it.point)
                tell(when {
                    it.kind == Kind.RAIN -> "${place}雨歇，云散了。"
                    rainAt(it.point) > 0f -> "${place}的火在雨中熄灭了。"
                    else -> "${place}的余火渐渐熄灭。"
                })
                true
            }
        }
        woods.filter { it.phase==WoodPhase.STACKED && it.carrier==null }.forEach { it.stackedAge+=dt; if(it.stackedAge>=30f) { it.phase=WoodPhase.CONSUMED; tell("村里用去了一束存柴。") } }
        woods.removeAll { it.phase==WoodPhase.CONSUMED }
        returnResidents()
        village?.advance(dt)
    }
    fun reportEscape(id: String, who: String, from: Point) {
        val hazard = hazards.firstOrNull { it.dangerous && from.distance(it.point) < it.radius + .012f } ?: return
        if (hazard.warnedActors.add(id)) {
            val area = if (hazard.kind == Kind.RAIN) "雨幕" else "火光"
            tell("${who}受惊，转身避开${area}。")
        }
    }
    fun localTask(id:String)=tasks[id]?.kind
    fun busy(id: String) = id in tasks || village?.jobs?.containsKey(id)==true
    fun weatherDangerous(p: Point) = hazards.any { it.dangerous && it.point.distance(p)<it.radius+.008f }
    fun dangerous(p: Point) = weatherDangerous(p) || wildlife.frightening(p)
    /** One immutable snapshot per synchronous ordinary route, never reused next frame. */
    fun residentRouteAllowed(id:String):(Point)->Boolean {
        val discs=ArrayList<LocalNavigation.Disc>()
        for(h in hazards)if(h.dangerous)discs+=LocalNavigation.Disc(h.point,h.radius+.008f)
        val own=wildlife.bodies[id]
        for(b in wildlife.bodies.values) {
            if(b.absent)continue
            var radius=if(b.alive&&b.species.frightensResidents)
                b.radius+if(b.species==WildlifeWorld.Species.TIGER).062f else .037f else 0f
            // A fallen animal still occupies space; an unknown walker retains
            // WildlifeWorld.open's original no-body behavior.
            if(own!=null&&b.id!=id)radius=maxOf(radius,own.radius+b.radius+.0006f)
            if(radius>0f)discs+=LocalNavigation.Disc(b.point,radius)
        }
        return navigation.outsideDiscs(discs)
    }
    fun moveResident(id:String,target:Point)=wildlife.move(id,target){navigation.open(it)}
    /** A blocked walker steps around the actual body, through the same swept collision check. */
    fun yieldResident(id:String,toward:Point,distance:Float):Point {
        val body=wildlife.bodies[id]?:return toward
        val at=body.point
        if(distance<=0f)return at
        val obstacle=wildlife.bodies.values.filter{!it.absent&&it.id!=id&&it.point.distance(at)<body.radius+it.radius+.009f}
            .minByOrNull{it.point.distance(at)}?:return at
        val direction=atan2(obstacle.point.z-at.z,obstacle.point.x-at.x)
        // Opposite walkers choose the same right-hand rule and separate sideways.
        for(offset in listOf(-PI/2,PI/2,-PI*.75,PI*.75,PI)) {
            val angle=direction+offset
            val target=Point(at.x+cos(angle).toFloat()*distance,at.z+sin(angle).toFloat()*distance)
            if(!wildlife.escapeSafe(at,target)||weatherDangerous(target))continue
            val moved=moveResident(id,target)
            if(moved.distance(at)>.000001f)return moved
        }
        return at
    }
    private fun route(a: Point,b: Point,id:String):List<Point>? {
        if(!dangerous(a))return navigation.path(a,b,residentRouteAllowed(id))
        return navigation.path(a,b) { p ->
        wildlife.open(id,p) && wildlife.escapeSafe(a,p) && hazards.none { it.dangerous && it.point.distance(p)<it.radius+.008f && a.distance(it.point)>=it.radius+.008f }
        }
    }
    private fun releaseWood(task: Task, current: Point) {
        woods.firstOrNull { it.id==task.wood }?.let { w ->
            if(w.phase==WoodPhase.CARRIED) {
                // A frightened carrier sets the real pile down; it remains available for later collection.
                val at=woods.indexOf(w);woods[at]=w.copy(point=current,phase=WoodPhase.WAITING,carrier=null)
            } else if(w.phase==WoodPhase.WAITING) w.carrier=null
        }
        task.wood=null
    }
    fun respond(id: String, current: Point, dt: Float, canHaul: Boolean): Motion? {
        var task=tasks[id]
        if(task!=null && task.kind!="flee" && task.kind!="return" && village?.life?.urgent(id)==true) {
            releaseWood(task,current);tasks.remove(id);task=null
        }
        if (dangerous(current) && (task==null || task.kind!="flee" || dangerous(task.goal))) {
            village?.interrupt(id,current)
            val home=task?.home ?: current
            task?.let { releaseWood(it,current) }
            tasks.remove(id)
            if(seconds<(fleeRetryAt[id]?:0f))return Motion(current,false,false,true)
            fleeRetryAt[id]=seconds+.8f
            // Search once for the nearest reachable refuge. Trying a complete
            // route for every safe cell stalls the main thread when surrounded.
            val path=navigation.pathToNearest(current,
                accept={ !dangerous(it) && it.distance(current)<.18f },
                allow={ wildlife.open(id,it) && wildlife.escapeSafe(current,it) &&
                    hazards.none { h -> h.dangerous && it.distance(h.point)<h.radius+.008f &&
                        current.distance(h.point)>=h.radius+.008f } })
            if(path!=null) {
                task=Task(home,path.last(),path.toMutableList(),"flee");tasks[id]=task
                reportEscape(id,"村人",current)
                fleeRetryAt.remove(id)
            }
            else return Motion(current,false,false,true)
        }
        // Daily life can use a safe refuge without waiting for the old worksite
        // to become safe. Its own remembered work/home rules arrange the return.
        if(task?.kind in listOf("flee","wait") && task?.path?.isEmpty()==true &&
            !dangerous(current) && village?.life?.enabled==true) {
            tasks.remove(id);task=null
        }
        // Only a rested, fed hauler gives a waiting physical gift precedence.
        // Pending timber must not prevent every other resident from finding food.
        val canSeekWood=seconds>=(woodRetryAt[id]?:0f)
        val preferWood=task==null&&canHaul&&canSeekWood&&village?.jobs?.containsKey(id)!=true&&
            village?.life?.availableForHauling(id)!=false&&
            woods.any{it.phase==WoodPhase.WAITING&&it.carrier==null&&!dangerous(it.point)}
        val daily=if(task==null && !dangerous(current)&&!preferWood)village?.respond(id,current,dt)else null
        if(daily!=null && (daily.action!="rest" || village?.jobs?.containsKey(id)==true || !canHaul ||
            woods.none{it.phase==WoodPhase.WAITING && it.carrier==null && !dangerous(it.point)}))return daily
        if(task==null && canHaul && canSeekWood && !dangerous(current) && village?.life?.availableForHauling(id)!=false) {
            val candidates=woods.filter { it.phase==WoodPhase.WAITING && it.carrier==null && !dangerous(it.point) }
                .sortedWith(compareByDescending<Wood> { w -> hazards.any { it.kind==Kind.FIRE && it.point.distance(w.point)<it.radius+.055f } }
                    .thenBy { it.point.distance(current) })
            if(candidates.isNotEmpty()) {
                // Multiple blocked piles share one search. An unavailable pile
                // must not restart a complete village scan on every display frame.
                val path=navigation.pathToPreferred(current,candidates.map{it.point},residentRouteAllowed(id))
                if(path!=null) {
                    val candidate=candidates.first{it.point==path.last()}
                    candidate.carrier=id;woodRetryAt.remove(id)
                    task=Task(current,candidate.point,path.toMutableList(),"pickup",candidate.id);tasks[id]=task
                } else woodRetryAt[id]=seconds+.8f+(id.last().digitToIntOrNull()?:0)*.031f
            }
        }
        task ?: return daily?:if(preferWood&&!dangerous(current))village?.respond(id,current,dt)else null
        fun replan() {
            if(seconds<task.replanAt)return
            task.replanAt=seconds+.8f+(id.last().digitToIntOrNull()?:0)*.031f
            route(current,task.goal,id)?.let{task.path=it.toMutableList()}
        }
        if(task.kind!="flee" && task.path.any { dangerous(it) }) {
            releaseWood(task,current);task.kind="wait";task.path.clear()
        }
        val reach=if(wildlife.bodies.containsKey(id)).012f else .004f
        if(task.kind in listOf("pickup","deliver") && current.distance(task.goal)<reach)task.path.clear()
        if(task.path.isEmpty()) {
            when(task.kind) {
                "flee", "wait" -> if(!dangerous(task.home) && seconds>=task.replanAt) {
                    task.replanAt=seconds+1f
                    route(current,task.home,id)?.let { task.goal=task.home;task.path=it.toMutableList();task.kind="return" }
                }
                "pickup" -> {
                    val w=woods.firstOrNull { it.id==task.wood }
                    if(w!=null && current.distance(w.point)<reach && !dangerous(depot) && seconds>=task.replanAt) {
                        task.replanAt=seconds+1f
                        route(current,depot,id)?.let { w.phase=WoodPhase.CARRIED;tell("搬运人扛起了地上的木束。");task.goal=depot;task.path=it.toMutableList();task.kind="deliver" }
                    }
                }
                "deliver" -> {
                    val w=woods.firstOrNull { it.id==task.wood }
                    if(w!=null && current.distance(depot)<reach) { w.phase=WoodPhase.STACKED;tell("一束新柴码进了村里的材堆。");w.carrier=null;w.stackedAge=0f;task.wood=null;task.kind="wait" }
                }
                "return" -> { tasks.remove(id);return Motion(current,false,false,false) }
            }
        }
        var p=current;var remaining=(if(task.kind=="flee") .026f else .016f)*dt
        while(task.path.isNotEmpty() && remaining>0f) {
            val target=task.path.first();val distance=p.distance(target)
            if(distance<.000001f){task.path.removeAt(0);continue}
            val travel=minOf(remaining,distance)
            val next=Point(p.x+(target.x-p.x)*travel/distance,p.z+(target.z-p.z)*travel/distance)
            val actual=moveResident(id,next)
            remaining-=travel;p=actual
            if(actual.distance(next)>.000001f) {
                p=yieldResident(id,task.goal,(travel-actual.distance(current)).coerceAtLeast(travel*.5f))
                replan()
                break
            }
            if(distance<=travel+.000001f)task.path.removeAt(0)
        }
        task.blocked=if(p.distance(current)<.00005f && task.path.isNotEmpty())task.blocked+dt else 0f
        if(task.blocked>6f && task.kind!="flee") {
            releaseWood(task,p);tasks.remove(id)
        }
        return Motion(p,p.distance(current)>.000001f,task.kind=="deliver",task.kind=="flee")
    }
}

/** Cached sampled terrain navigation with eight neighbours and no diagonal corner cutting. */
internal class LocalNavigation(val height: (Float,Float)->Float?, val obstacle: (Float,Float)->Boolean = {_,_->false}) {
    // Optional native profiler bridge; the domain remains independent of Android.
    var trace: ((String,Boolean)->Unit)? = null
    private inline fun <T> measured(name:String, action:()->T):T {
        trace?.invoke(name,true)
        try { return action() } finally { trace?.invoke(name,false) }
    }
    private val step=.007f
    private val nx=127;private val nz=93
    private val grid=Array(nx*nz) { i->LocalInterventionWorld.Point(-.441f+(i%nx)*step,-.322f+(i/nx)*step) }
    private fun point(i:Int)=grid[i]
    private val valid=BooleanArray(nx*nz) { i -> val p=point(i);height(p.x,p.z)!=null && !obstacle(p.x,p.z) }
    val points: List<LocalInterventionWorld.Point> = valid.indices.filter { valid[it] }.map(::point)
    data class Disc(val point:LocalInterventionWorld.Point,val radius:Float)
    /** Rasterize only each disc's bounding box, retaining the exact strict distance test. */
    fun outsideDiscs(discs:List<Disc>):(LocalInterventionWorld.Point)->Boolean {
        val blocked=BooleanArray(grid.size)
        for(d in discs) {
            // Extra cells protect Float rounding at the bounding-box edge.
            val left=(floor((d.point.x-d.radius+.441f)/step).toInt()-1).coerceAtLeast(0)
            val right=(ceil((d.point.x+d.radius+.441f)/step).toInt()+1).coerceAtMost(nx-1)
            val top=(floor((d.point.z-d.radius+.322f)/step).toInt()-1).coerceAtLeast(0)
            val bottom=(ceil((d.point.z+d.radius+.322f)/step).toInt()+1).coerceAtMost(nz-1)
            for(z in top..bottom)for(x in left..right) {
                val i=z*nx+x
                if(!blocked[i]&&grid[i].distance(d.point)<d.radius)blocked[i]=true
            }
        }
        return { p->
            val x=((p.x+.441f)/step).roundToInt()
            val z=((p.z+.322f)/step).roundToInt()
            val i=if(x in 0 until nx&&z in 0 until nz)z*nx+x else -1
            // Search nodes use the table. Exact off-grid targets retain the
            // continuous predicate rather than inheriting a nearby cell.
            if(i>=0&&grid[i]==p)!blocked[i]
            else discs.none{p.distance(it.point)<it.radius}
        }
    }
    private val dx=intArrayOf(-1,0,1,-1,1,-1,0,1)
    private val dz=intArrayOf(-1,-1,-1,0,0,1,1,1)
    private val edgeCost=FloatArray(8){if(dx[it]!=0&&dz[it]!=0)1.414214f else 1f}
    // Immutable terrain graph shared by every search. A diagonal exists only
    // when both terrain corners are open; dynamic corners are still rechecked.
    private val neighbours=IntArray(valid.size*8){-1}.also { edges ->
        for(i in valid.indices) if(valid[i]) {
            val x=i%nx;val z=i/nx
            for(k in 0..7) {
                val xx=x+dx[k];val zz=z+dz[k]
                if(xx !in 0 until nx||zz !in 0 until nz)continue
                val j=zz*nx+xx
                if(!valid[j])continue
                if(dx[k]!=0&&dz[k]!=0&&(!valid[i+dx[k]]||!valid[i+dz[k]*nx]))continue
                edges[i*8+k]=j
            }
        }
    }
    private inline fun adjacent(i:Int, visit:(Int,Int)->Unit) {
        for(k in 0..7) {
            val j=neighbours[i*8+k]
            if(j>=0)visit(j,k)
        }
    }
    private inline fun cornersOpen(i:Int,k:Int,pass:(Int)->Boolean)=
        dx[k]==0||dz[k]==0||(pass(i+dx[k])&&pass(i+dz[k]*nx))
    // Terrain/buildings are fixed for this grid. Reject disconnected banks before
    // evaluating dynamic bodies across thousands of cells for every food target.
    private val components:IntArray by lazy {
        val labels=IntArray(valid.size){-1}
        val queue=java.util.ArrayDeque<Int>()
        var component=0
        for(start in valid.indices) {
            if(!valid[start] || labels[start]>=0)continue
            labels[start]=component;queue.add(start)
            while(queue.isNotEmpty()) {
                val i=queue.removeFirst()
                adjacent(i) { j,_ ->
                    if(labels[j]<0){labels[j]=component;queue.add(j)}
                }
            }
            component++
        }
        labels
    }
    fun open(p: LocalInterventionWorld.Point)=p.x.isFinite() && p.z.isFinite() && p.x in -.441f.. .441f && p.z in -.322f.. .322f && height(p.x,p.z)!=null && !obstacle(p.x,p.z)
    private fun index(p:LocalInterventionWorld.Point,allow:(LocalInterventionWorld.Point)->Boolean={true}):Int? {
        val ix=((p.x+.441f)/step).roundToInt();val iz=((p.z+.322f)/step).roundToInt()
        var best=-1;var distance=Float.POSITIVE_INFINITY
        for(z in iz-2..iz+2)for(x in ix-2..ix+2) {
            if(x !in 0 until nx||z !in 0 until nz)continue
            val i=z*nx+x
            if(!valid[i]||!allow(point(i)))continue
            val gx=point(i).x-p.x;val gz=point(i).z-p.z
            val d=gx*gx+gz*gz
            if(d<distance){best=i;distance=d}
        }
        return best.takeIf{it>=0}
    }
    // Only fixed terrain/buildings are cached. Bodies, weather and endpoint
    // snapping are checked afresh on every search. Bound memory to about 1.5 MB.
    private val distancesByGoal=object:LinkedHashMap<Int,FloatArray>(32,.75f,true) {
        override fun removeEldestEntry(eldest:MutableMap.MutableEntry<Int,FloatArray>?)=size>32
    }
    private fun terrainDistances(end:Int):FloatArray=distancesByGoal.getOrPut(end) { measured("Village.routeTerrain") {
        val dist=FloatArray(valid.size){Float.POSITIVE_INFINITY}
        val queue=NavigationQueue()
        val done=BooleanArray(valid.size)
        dist[end]=0f;queue.add(end,0f)
        while(queue.isNotEmpty()) {
            val i=queue.remove()
            if(done[i])continue
            done[i]=true
            adjacent(i) { j,k ->
                val cost=dist[i]+edgeCost[k]
                if(!done[j]&&cost<dist[j]){dist[j]=cost;queue.add(j,cost)}
            }
        }
        dist
    } }
    fun path(a:LocalInterventionWorld.Point,b:LocalInterventionWorld.Point,allow:(LocalInterventionWorld.Point)->Boolean={true}):List<LocalInterventionWorld.Point>? {
        return measured("Village.route") {
        val start=index(a,allow)?:return null;val end=index(b,allow)?:return null
        if(components[start]!=components[end])return null
        // Dynamic body/weather predicates are stable during this synchronous
        // search. Evaluate each grid cell once, never across separate searches.
        val allowed=ByteArray(valid.size)
        fun pass(i:Int):Boolean {
            if(!valid[i])return false
            if(allowed[i].toInt()==0)allowed[i]=if(allow(point(i)))1 else -1
            return allowed[i].toInt()==1
        }
        // Probe only a small component at the destination. If it is sealed,
        // reject without scanning the rest of the village. Hitting the budget
        // proves nothing: the complete forward search must still run.
        if(start!=end) {
            val seen=BooleanArray(valid.size)
            val probe=IntArray(64)
            var read=0;var count=1
            probe[0]=end;seen[end]=true
            while(read<count&&count<probe.size&&!seen[start]) {
                val i=probe[read++]
                adjacent(i) { j,k ->
                    if(count<probe.size&&!seen[j]&&pass(j)&&cornersOpen(i,k,::pass)) {
                        seen[j]=true;probe[count++]=j
                    }
                }
            }
            if(read==count&&!seen[start])return null
        }
        // The river's actual ford is a stronger lower bound than straight-line
        // distance. Equal-cost alternatives prefer progress toward the goal.
        val estimate=terrainDistances(end)
        val queue=NavigationQueue(estimate);val dist=FloatArray(valid.size) { Float.POSITIVE_INFINITY };val prev=IntArray(valid.size) {-1};val done=BooleanArray(valid.size)
        dist[start]=0f;queue.add(start,estimate[start])
        measured("Village.routeDynamic") { while(queue.isNotEmpty()) {
            val i=queue.remove();if(done[i])continue;done[i]=true
            if(i==end) { val out=mutableListOf(b);var j=i;while(j!=start) {out.add(point(j));j=prev[j]};out.add(point(start));out.reverse();return out }
            adjacent(i) { j,k ->
                val nd=dist[i]+edgeCost[k]
                if(!done[j]&&nd<dist[j]&&pass(j)&&cornersOpen(i,k,::pass)) {
                    dist[j]=nd;prev[j]=i;queue.add(j,nd+estimate[j])
                }
            }
        } }
        return null
        }
    }
    /** First reachable target in caller priority order, through one shared search. */
    fun pathToPreferred(a:LocalInterventionWorld.Point,targets:List<LocalInterventionWorld.Point>,
        allow:(LocalInterventionWorld.Point)->Boolean):List<LocalInterventionWorld.Point>? {
        return measured("Village.routePreferred") {
        if(targets.isEmpty())return null
        val allowed=ByteArray(valid.size)
        fun pass(i:Int):Boolean {
            if(!valid[i])return false
            if(allowed[i].toInt()==0)allowed[i]=if(allow(point(i)))1 else -1
            return allowed[i].toInt()==1
        }
        val start=index(a,allow)?:return null
        val ends=targets.map { target->
            index(target,allow)?.takeIf{components[it]==components[start]}
        }
        val first=ends.indexOfFirst{it!=null}
        if(first<0)return null
        val rank=IntArray(valid.size){Int.MAX_VALUE}
        ends.forEachIndexed { order,end->if(end!=null)rank[end]=minOf(rank[end],order) }
        val estimate=terrainDistances(ends[first]!!)
        val queue=NavigationQueue(estimate)
        val dist=FloatArray(valid.size){Float.POSITIVE_INFINITY}
        val prev=IntArray(valid.size){-1}
        val done=BooleanArray(valid.size)
        var best=-1
        dist[start]=0f;queue.add(start,estimate[start])
        while(queue.isNotEmpty()) {
            val i=queue.remove()
            if(done[i])continue
            done[i]=true
            if(rank[i]!=Int.MAX_VALUE&&(best<0||rank[i]<rank[best]))best=i
            if(best>=0&&rank[best]==first)break
            adjacent(i) { j,k->
                val cost=dist[i]+edgeCost[k]
                if(!done[j]&&cost<dist[j]&&pass(j)&&cornersOpen(i,k,::pass)) {
                    dist[j]=cost;prev[j]=i;queue.add(j,cost+estimate[j])
                }
            }
        }
        if(best<0)return null
        val out=mutableListOf(targets[rank[best]])
        var i=best
        while(i!=start){out+=point(i);i=prev[i]}
        out+=point(start);out.reverse()
        out
        }
    }
    /** Dijkstra to the first admissible destination, with one dynamic check per cell. */
    fun pathToNearest(a:LocalInterventionWorld.Point,accept:(LocalInterventionWorld.Point)->Boolean,
        allow:(LocalInterventionWorld.Point)->Boolean):List<LocalInterventionWorld.Point>? {
        return measured("Village.routeNearest") {
        val allowed=ByteArray(valid.size)
        fun pass(i:Int):Boolean {
            if(!valid[i])return false
            if(allowed[i].toInt()==0)allowed[i]=if(allow(point(i)))1 else -1
            return allowed[i].toInt()==1
        }
        val start=index(a)?:return null
        // A body may start inside an overlapping danger/body. Its first leg is
        // still validated continuously by moveResident, so it can separate.
        val queue=NavigationQueue()
        val dist=FloatArray(valid.size){Float.POSITIVE_INFINITY}
        val prev=IntArray(valid.size){-1}
        val done=BooleanArray(valid.size)
        dist[start]=0f;queue.add(start,0f)
        while(queue.isNotEmpty()) {
            val i=queue.remove()
            if(done[i])continue
            done[i]=true
            if(pass(i)&&accept(point(i))) {
                val out=mutableListOf(point(i))
                var j=i
                while(j!=start){j=prev[j];out+=point(j)}
                out.reverse();return out
            }
            adjacent(i) { next,k ->
                val cost=dist[i]+edgeCost[k]
                if(!done[next]&&cost<dist[next]&&pass(next)&&cornersOpen(i,k,::pass)) {
                    dist[next]=cost;prev[next]=i;queue.add(next,cost)
                }
            }
        }
        return null
        }
    }
    /** Contact alternatives share one graph search and one dynamic check per cell. */
    fun pathToAny(a:LocalInterventionWorld.Point,targets:List<LocalInterventionWorld.Point>,
        allow:(LocalInterventionWorld.Point)->Boolean):List<LocalInterventionWorld.Point>? {
        return measured("Village.routeAny") {
        if(targets.isEmpty())return null
        val allowed=ByteArray(valid.size)
        fun checked(p:LocalInterventionWorld.Point):Boolean {
            val i=((p.z+.322f)/step).roundToInt()*nx+((p.x+.441f)/step).roundToInt()
            if(allowed[i].toInt()==0)allowed[i]=if(allow(p))1 else -1
            return allowed[i].toInt()==1
        }
        val start=index(a,::checked)?:return null
        val preferred=targets.first()
        val ends=mutableMapOf<Int,LocalInterventionWorld.Point>()
        for(target in targets) {
            if(!open(target)||!allow(target))continue
            val end=index(target,::checked)?:continue
            if(components[start]!=components[end])continue
            val existing=ends[end]
            if(existing==null||target.distance(preferred)<existing.distance(preferred))ends[end]=target
        }
        if(ends.isEmpty())return null
        val queue=NavigationQueue()
        val dist=FloatArray(valid.size){Float.POSITIVE_INFINITY}
        val prev=IntArray(valid.size){-1}
        val done=BooleanArray(valid.size)
        dist[start]=0f;queue.add(start,point(start).distance(preferred)/step)
        var best=-1;var bestCost=Float.POSITIVE_INFINITY
        while(queue.isNotEmpty()) {
            if(queue.firstCost()>=bestCost)break
            val i=queue.remove()
            if(done[i])continue
            done[i]=true
            ends[i]?.let { target ->
                // Prefer approaching the requested contact point over stopping
                // at the first reachable edge of its neighbourhood.
                val cost=dist[i]+target.distance(preferred)*4f/step
                if(cost<bestCost){best=i;bestCost=cost}
            }
            adjacent(i) { j,k ->
                val nd=dist[i]+edgeCost[k]
                if(!done[j]&&nd<dist[j]&&checked(point(j))&&
                    cornersOpen(i,k){checked(point(it))}) {
                    dist[j]=nd;prev[j]=i
                    queue.add(j,nd+point(j).distance(preferred)/step)
                }
            }
        }
        if(best<0)return null
        val out=mutableListOf(ends.getValue(best))
        var i=best
        while(i!=start){out+=point(i);i=prev[i]}
        out+=point(start);out.reverse()
        return out
        }
    }
}

/** Primitive heap: no boxed comparator keys or per-relaxation node allocation. */
private class NavigationQueue(private val tie:FloatArray?=null) {
    private var ids=IntArray(128)
    private var costs=FloatArray(128)
    private var size=0
    fun isNotEmpty()=size>0
    fun firstCost()=costs[0]
    private fun less(cost:Float,id:Int,otherCost:Float,otherId:Int)=
        cost<otherCost||(cost==otherCost&&tie!=null&&tie[id]<tie[otherId])
    fun add(id:Int,cost:Float) {
        if(size==ids.size){ids=ids.copyOf(size*2);costs=costs.copyOf(size*2)}
        var at=size++
        while(at>0) {
            val parent=(at-1)/2
            if(!less(cost,id,costs[parent],ids[parent]))break
            ids[at]=ids[parent];costs[at]=costs[parent];at=parent
        }
        ids[at]=id;costs[at]=cost
    }
    fun remove():Int {
        val result=ids[0]
        size--
        if(size==0)return result
        val id=ids[size];val cost=costs[size]
        var at=0
        while(at*2+1<size) {
            var child=at*2+1
            if(child+1<size&&less(costs[child+1],ids[child+1],costs[child],ids[child]))child++
            if(!less(costs[child],ids[child],cost,id))break
            ids[at]=ids[child];costs[at]=costs[child];at=child
        }
        ids[at]=id;costs[at]=cost
        return result
    }
}
