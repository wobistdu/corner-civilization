package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import kotlin.math.cos
import kotlin.math.sin

internal class VillageInfluence(private val world:LocalInterventionWorld, private val village:LivingVillage) {
    data class Plot(val point:Point, val meadow:Boolean, var growth:Float=0f)
    data class Beat(val point:Point,val at:Float,val alarm:Boolean)
    val plots=mutableListOf<Plot>()
    var beat:Beat?=null
        private set
    private val heard=mutableSetOf<String>()
    private var warned=false
    private var witness:String?=null
    private val grazing=mutableMapOf<Point,Pair<String,Float>>()
    private val nextFruitSearch=mutableMapOf<String,Float>()
    private var nextWatch=0f
    var followed:String?=null
        private set
    var diary=VillageChronicle()
        private set
    private var lastActivity=""
    private var trailPoint:Point?=null
    val footsteps=ArrayDeque<Point>()

    fun plant(points:List<Point>):Int {
        val before=plots.size
        for(p in points) {
            if(plots.size>=MAX_PLOTS)break
            if(world.navigation.open(p) && plots.none{it.point.distance(p)<.017f}) plots+=Plot(p,world.farBank(p))
        }
        if(plots.size>before)world.tell("你把一撮种子撒进土里，等一场落在这里的雨。",true)
        else if(before>=MAX_PLOTS && points.isNotEmpty())world.tell("这些地块都已播种，等采完一处再开新地。",true)
        return plots.size-before
    }
    fun drum(p:Point):Boolean {
        if(beat?.let{world.seconds-it.at<1.2f}==true)return false
        val tiger=world.wildlife.bodies.values.filter{it.alive && it.species==WildlifeWorld.Species.TIGER}
        val listeners=village.residents.entries.filter { it.value.distance(p)<.21f }
            .sortedBy { it.value.distance(p) }
        witness=listeners.firstOrNull{listener->tiger.any{it.point.distance(listener.value)<.115f}}?.key
        val alarm=witness!=null
        beat=Beat(p,world.seconds,alarm)
        heard.clear();heard.addAll(listeners.map{it.key});warned=false
        world.tell("你在沙盘${VillageChronicle.place(p)}敲响了鼓。",true)
        if(alarm) {
            world.tell(VillageChronicle.nameOf(requireNotNull(witness))+"看见了虎影，循着鼓声向同伴喊了一声。")
        } else dispatch(p,false,listeners.map{it.key})
        return true
    }
    private fun dispatch(p:Point,alarm:Boolean,ids:List<String>) {
        var assigned=0
        for (id in ids) {
            val at=village.residents[id] ?: continue
            if(assigned>=3)break
            val old=village.jobs[id]
            if(world.localTask(id)!=null || old?.carrying==true || old?.wood!=null || old?.food!=null ||
                old?.peer!=null || (!alarm && (village.life.urgent(id) || old!=null && old.event !in casual))) continue
            val desired=if(alarm) village.houses.values.filter{it.available && !it.ruined && !world.dangerous(it.workPoint)}
                .minByOrNull{it.workPoint.distance(at)}?.workPoint ?: continue
            else Point(p.x+.021f*cos(assigned*2.1f),p.z+.021f*sin(assigned*2.1f))
            val goal=village.nearestOpen(desired) ?: continue
            if(world.dangerous(goal))continue
            val path=world.navigation.path(at,goal,world.residentRouteAllowed(id)) ?: continue
            if(old!=null)village.interrupt(id,at)
            village.jobs[id]=LivingVillage.Job(if(alarm)"drumShelter" else "drumGather",null,path.toMutableList(),goal)
            assigned++
        }
    }
    fun looking(id:String)=beat?.takeIf{id in heard && world.seconds-it.at<2f}?.point
    fun follow(id:String):Boolean {
        if(id !in village.residents || world.wildlife.bodies[id]?.absent==true)return false
        followed=if(followed==id)null else id
        diary=VillageChronicle();footsteps.clear();trailPoint=null;lastActivity="";nextWatch=0f
        updateWatch()
        return true
    }
    fun stopFollowing(){followed=null;footsteps.clear();trailPoint=null}
    private fun updateWatch(){
        val id=followed ?: return
        val at=village.residents[id]
        val job=village.jobs[id]
        val local=world.localTask(id)
        val activity=when {
            at==null || world.wildlife.bodies[id]?.absent==true -> "消失在视野外，大家等着他回来。"
            local=="flee" -> "正躲开眼前的危险。"
            local!=null -> when(local){"pickup","deliver","carry","wood"->"在为村里搬运木材。";else->"正沿着小路赶往下一处。"}
            job?.event==VillageEvent.MOULD_CLAY && job.carrying -> "正把第一口缸坯捧回村。"
            job?.carrying==true -> "正带着物资赶路。"
            job?.event!=null -> "正忙着「${job.event.title}」。"
            else -> when(job?.kind){"drumGather"->"应着鼓声，到人群里去。";"drumShelter"->"听到虎警，往棚下避让。";"pickSeedFruit"->"正去摘你种下的果子。";"foodDelivery","foodPickup"->"正张罗把食物搬回村。";"build","supply","clear"->"在帮着修缮草棚。";else->"正四下张望，找下一件活。"}
        }
        if(activity!=lastActivity){diary.append(world.seconds,VillageChronicle.nameOf(id)+activity);lastActivity=activity}
        if(at!=null && (trailPoint?.distance(at)?:1f)>.006f) {
            footsteps.addLast(at);if(footsteps.size>5)footsteps.removeFirst();trailPoint=at
        }
    }
    fun witnessed(id:String,event:VillageEvent){if(id==followed)diary.villageEvent(event,id,world.seconds)}
    fun meadow(from:Point)=plots.filter{it.meadow && it.growth>=1f && !world.weatherDangerous(it.point)}.minByOrNull{it.point.distance(from)}?.point
    fun fruitJob(id:String,at:Point):LivingVillage.Job? {
        if(plots.isEmpty() || world.seconds<(nextFruitSearch[id] ?: 0f))return null
        if(village.life.urgent(id) || village.ecology.food+village.foodBundles.size>=village.ecology.foodCapacity)return null
        val fruit=plots.filter{!it.meadow && it.growth>=1f && !world.dangerous(it.point) &&
            village.jobs.values.none{j->j.kind=="pickSeedFruit" && j.goal.distance(it.point)<.02f}}.minByOrNull{it.point.distance(at)} ?: return null
        val path=world.navigation.path(at,fruit.point,world.residentRouteAllowed(id))
        if(path==null){nextFruitSearch[id]=world.seconds+3f;return null}
        return LivingVillage.Job("pickSeedFruit",null,path.toMutableList(),fruit.point)
    }
    fun collectFruit(id:String,goal:Point,at:Point) {
        val fruit=plots.firstOrNull{it.point==goal && !it.meadow && it.growth>=1f}
        if(fruit!=null && village.ecology.food+village.foodBundles.size<village.ecology.foodCapacity){
            plots.remove(fruit);village.foodBundles+=LivingVillage.FoodBundle(at,origin="plantedFruit");village.life.foodProduced++
            world.tell(VillageChronicle.nameOf(id)+"摘下了你种的野果，放在路旁等人搬回。")
        }
        village.jobs.remove(id)
    }
    fun advance(dt:Float) {
        if(world.seconds>=nextWatch){updateWatch();nextWatch=world.seconds+.28f}
        val pulse=beat
        if(pulse!=null && !warned && pulse.alarm && world.seconds-pulse.at>1f){
            warned=true
            heard.forEach{village.life.person(it).warned=true}
            dispatch(pulse.point,true,heard.toList())
        }
        val iterator=plots.iterator()
        while(iterator.hasNext()){
            val plot=iterator.next()
            if(plot.growth<1f){
                val wet=maxOf(world.rainAt(plot.point),world.wetAt(plot.point))
                plot.growth=(plot.growth+dt*wet*.108f).coerceAtMost(1f)
                if(plot.growth>=1f)world.tell(if(plot.meadow)"远岸的草甸长出了嫩草，象群有了新的觅食去处。" else "村边的种子结了果，有人可以来采了。")
            } else if(plot.meadow) {
                val adult=world.wildlife.bodies.values.firstOrNull { it.alive && it.species==WildlifeWorld.Species.ADULT &&
                    it.point.distance(plot.point)<.028f && !world.weatherDangerous(it.point) }
                if(adult==null){grazing.remove(plot.point);continue}
                val elapsed=(grazing[plot.point]?.takeIf{it.first==adult.id}?.second ?: 0f)+dt
                if(elapsed<2f)grazing[plot.point]=adult.id to elapsed else {
                    adult.hunger=(adult.hunger-.18f).coerceAtLeast(0f)
                    grazing.remove(plot.point);iterator.remove()
                    world.tell("猛犸低下头，啃掉了你种下的一丛嫩草。")
                }
            }
        }
    }
    companion object {
        const val MAX_PLOTS=18
        private val casual=setOf(VillageEvent.INSPECT_HOME,VillageEvent.GREET_NEIGHBOR,VillageEvent.STRETCH_BREAK,
            VillageEvent.MARK_SAFE_TRAIL,VillageEvent.COLLECT_BRANCH,VillageEvent.CUT_REEDS,VillageEvent.PICK_SEEDHEADS,
            VillageEvent.CHECK_FENCE,VillageEvent.FORAGE_BERRIES)
    }
}
