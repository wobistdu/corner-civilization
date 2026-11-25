package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.math.*
import kotlin.math.*

internal data class FaunaPlacement(val name:String,val species:String,val x:Float,val z:Float,val height:Float,val yaw:Float)
internal val primitiveFaunaPlacements=listOf(
    FaunaPlacement("MammothAdult_0","mammoth",.260f,-.130f,.030f,155f),
    FaunaPlacement("MammothAdult_1","mammoth",.321f,-.160f,.0285f,170f),
    FaunaPlacement("MammothCalf","mammoth",.240f,-.190f,.021f,145f),
    FaunaPlacement("Sabertooth_0","sabertooth",-.320f,-.160f,.0105f,-25f),
    FaunaPlacement("Sabertooth_1","sabertooth",.328f,-.045f,.00945f,15f))

/** Each GLB is independently loaded; skeleton/controller ownership is never cloned or shared. */
internal class PrimitiveFaunaRuntime(world:Entity, models:List<Entity>, private val ground:PrimitiveTerrain,
    private val events:LocalInterventionWorld) {
    /** A quarter turn about +X maps the centre's +Y offset onto +Z.
     * The GLB bind-space bounds are 1 high and ±0.22640108 wide along Z.
     * Evaluate this fixed pose directly: descendant world transforms can lag a frame.
     */
    private class CalfFlank(private val pivot:Entity, private val height:Float) {
        fun layDown(owner:Entity, groundPosition:Vector3) {
            pivot.transform().eulerAngles=EulerAngles(90f,0f,0f)
            pivot.transform().position=Vector3(0f,0f,-height*.5f)
            owner.transform().position=groundPosition+Vector3(0f,height*.22640108f,0f)
        }

        fun standUp() { pivot.transform().apply { eulerAngles=EulerAngles(0f,0f,0f);position=Vector3.ZERO } }
    }

    private class Actor(val entity:Entity,val model:Entity,val mind:PrimitiveFaunaMind,val placement:FaunaPlacement,val water:List<Point>, val flank:CalfFlank?) {
        var lying=false
        val skeleton=model.findSkeletonRootEntity(includeDisabled=true).single()
        val clips=skeleton.getAnimationResources().associateBy {it.getName()}.also {check(it.keys.containsAll(listOf("idle","walk","flee","graze","drink","rest")))}
        var controller:AnimationPlaybackController?=null
        var clip=""
        private var lastStride=1f
        var active=true
        private var visible=true
        fun setVisible(show:Boolean) {
            if(show!=visible){entity.enabled=show;visible=show}
        }
        fun play(gait:PrimitiveFaunaMind.Gait, playing:Boolean) {
            val next=if(gait==PrimitiveFaunaMind.Gait.IDLE)mind.activity else gait.name.lowercase()
            if(next==clip && next=="walk" && lastStride!=mind.strideRate)
                controller?.setSpeed(mind.walkSpeed*mind.strideRate*1.2f/((if(placement.species=="mammoth").16f else .30f)*placement.height))
            lastStride=mind.strideRate
            if(next!=clip) {
                controller?.let {if(it.valid)it.stop();it.close()}
                controller=skeleton.playAnimation(requireNotNull(clips[next])).also {
                    check(it.valid && it.getDuration()>0f)
                    it.setTime((placement.x*21f).let(::abs)%it.getDuration())
                    val stride=if(placement.species=="mammoth") if(next=="flee") .28f else .16f else if(next=="flee") .48f else .30f
                    val duration=if(next=="flee") .7f else 1.2f
                    val speed=if(next !in listOf("walk","flee"))1f else (if(next=="flee")mind.fleeSpeed else mind.walkSpeed*mind.strideRate)*duration/(stride*placement.height)
                    it.setSpeed(speed)
                    if(!playing)it.pause()
                }
                clip=next;active=playing
                Log.i("FaunaMotion","animal=${placement.name} clip=$next skeleton=${skeleton.id} position=${mind.point}")
            } else if(playing!=active) {controller?.let {if(playing)it.resume() else it.pause()};active=playing}
        }
        fun close(){controller?.let {if(it.valid)it.stop();it.close()};controller=null}
    }
    private val root=Entity().apply{setName("LivingFauna");world.addChild(this)}
    private val actors=mutableListOf<Actor>()
    init {
        check(models.size==primitiveFaunaPlacements.size)
        world.requireNode("FaunaLibrary").enabled=false
        val buildings=listOf("Shelter_0","Shelter_1","Shelter_2","RainShelter").map {world.requireNode(it).getVisualBounds(world,recursive=true,enabledOnly=false)}
        fun height(x:Float,z:Float):Float?=ground.heightOrNull(x,z)
        try {models.zip(primitiveFaunaPlacements).forEachIndexed {i,(model,p)->
            val clearance=if(p.species=="mammoth")p.height*.74f else p.height*1.12f
            val offsets=(0..7).map{angle->val a=angle*PI.toFloat()/4f;Point(cos(a)*clearance,sin(a)*clearance)}
            val setback = clearance + if (p.species=="mammoth") .085f else .105f
            val navigation=LocalNavigation({x,z->height(x,z)?.takeIf {offsets.all {height(x+it.x,z+it.z)!=null}}}) {x,z->
                buildings.any { house ->
                    val dx = maxOf(house.min.x-x, 0f, x-house.max.x)
                    val dz = maxOf(house.min.z-z, 0f, z-house.max.z)
                    dx*dx + dz*dz < setback*setback
                }
            }
            navigation.trace=events.navigation.trace
            val desired=Point(p.x,p.z)
            val home=if(navigation.open(desired))desired else navigation.points.minBy{it.distance(desired)}
            val owner=Entity().apply{setName(p.name);root.addChild(this);transform().position=Vector3(home.x,requireNotNull(height(home.x,home.z)),home.z);transform().eulerAngles=EulerAngles(0f,p.yaw,0f)}
            val flank = if (p.name=="MammothCalf") Entity().apply {
                setName("CalfFlankPivot");owner.addChild(this)
            } else null
            (flank ?: owner).addChild(model)
            model.enableTree();model.transform().scaleVector=Vector3(p.height)
            val walk=(if(p.species=="mammoth") .16f else .30f)*p.height/1.2f
            val flee=(if(p.species=="mammoth") .28f else .48f)*p.height/.7f*(if(p.species=="mammoth")1.45f else 2.4f)
            val water=navigation.points.filter { site ->
                offsets.any { edge ->
                    // Reach over the dry-bank margin with the head while all four feet stay on land.
                    val reach=(clearance+.024f)/clearance
                    ground.isRiver(site.x+edge.x*reach,site.z+edge.z*reach)
                }
            }
            val actor=Actor(owner,model,PrimitiveFaunaMind(navigation,home,clearance,walk,flee,i),p,water,flank?.let { CalfFlank(it,p.height) })
            val species=when {p.name=="MammothCalf"->WildlifeWorld.Species.CALF
                p.species=="mammoth"->WildlifeWorld.Species.ADULT
                else->WildlifeWorld.Species.TIGER}
            val bounds=owner.getVisualBounds(world,recursive=true,enabledOnly=true)
            val bodyRadius=maxOf(clearance,hypot(bounds.halfExtent.x,bounds.halfExtent.z))
            events.wildlife.add(p.name,species,home,bodyRadius)
            actor.mind.bindBody{events.wildlife.open(p.name,it)}
            actors+=actor;actor.play(PrimitiveFaunaMind.Gait.IDLE,true)
        }
        events.farBank = { p -> p.distance(events.depot)>.22f && ground.beyondRiver(p.x,p.z) }
        events.wildFoodAllowed = { body, p -> actors.first { it.placement.name==body.id }.mind.navigation.open(p) }
        events.wildMeadow = { adult ->
            val actor = actors.first { it.placement.name == adult.id }
            actor.mind.navigation.pathToNearest(adult.point,{ p ->
                adult.point.distance(p) in .040f.. .11f && !events.weatherDangerous(p) && events.wildlife.open(adult.id,p)
            },{ !events.weatherDangerous(it) })?.lastOrNull()
        }
        events.village?.animalLife?.excursionSite = { calf, adults ->
            val nav=actors.first { it.placement.name==calf.id }.mind.navigation
            nav.pathToNearest(calf.point,{ site ->
                adults.all { it.point.distance(site)>.148f } && events.wildlife.open(calf.id,site)
            },{ !events.weatherDangerous(it) && events.wildlife.open(calf.id,it) })?.lastOrNull()
        }
        events.village?.animalLife?.calfEntrySite = { calf, adults ->
            val nav = actors.first { it.placement.name == calf.id }.mind.navigation
            val hunters = events.wildlife.bodies.values.filter { it.species == WildlifeWorld.Species.TIGER }
            val edge = nav.points.filter { site ->
                (abs(site.x)>.34f || abs(site.z)>.25f) && !events.weatherDangerous(site) &&
                    events.wildlife.open(calf.id,site) && hunters.none { it.point.distance(site)<.14f }
            }.toHashSet()
            adults.firstNotNullOfOrNull { adult ->
                nav.pathToNearest(adult.point,{it in edge && it.distance(adult.point)>.045f},
                    { !events.weatherDangerous(it) })?.lastOrNull()
            }
        }
        events.village?.animalLife?.habitatGoal={ body,event,preferred ->
            val actor=actors.first{it.placement.name==body.id}
            val drinking=event in setOf(VillageEvent.TIGER_DRINK,VillageEvent.LEAD_CALF_WATER,VillageEvent.SHARE_ELEPHANT_WATER)
            if(!drinking && event!=VillageEvent.ELEPHANT_SHADE)preferred else {
                val sites=if(drinking)actor.water else events.village!!.life.patches.map{it.point}
                val nav=actor.mind.navigation
                val allowed={p:Point->events.wildlife.open(body.id,p) && !actor.mind.danger(p,events.hazards)}
                val near=sites.filter{it.distance(preferred)<body.radius+.016f}
                nav.pathToAny(body.point,near.sortedBy{it.distance(preferred)},allowed)?.lastOrNull()
                    ?: if(drinking) {
                        val shores=sites.toHashSet()
                        nav.pathToNearest(body.point,shores::contains,allowed)?.lastOrNull()
                    } else nav.pathToAny(body.point,sites,allowed)?.lastOrNull()
            }
        }
        check(actors.map{it.skeleton.id}.distinct().size==5)
        Log.i("FaunaMotion","ready animals=5 independentSkeletons=5 clips=idle,walk,flee,graze,drink,rest native=true water=blocked bodyClearance=true")
        }catch(error:Exception){close();throw error}
    }
    private var checkedHerdBank=false
    private fun alignRestoredHerd() {
        val lead=events.wildlife.bodies.getValue("MammothAdult_0")
        for(a in actors.filter { it.placement.species=="mammoth" && it.placement.name!=lead.id }) {
            val member=events.wildlife.bodies.getValue(a.placement.name)
            val nav=a.mind.navigation
            if(!member.alive || member.point.distance(lead.point)<.13f || nav.path(member.point,lead.point)!=null)continue
            val start=nav.pathToNearest(lead.point,{ site ->
                site.distance(lead.point) in .065f.. .105f && events.wildlife.open(member.id,site) && !events.weatherDangerous(site)
            },{ !events.weatherDangerous(it) })?.lastOrNull() ?: continue
            member.point=start
        }
    }
    private var lastRaid=-100f
    fun animate(dt:Float,active:Boolean) {
        if(active && !checkedHerdBank){alignRestoredHerd();checkedHerdBank=true}
        // A restore happens after scene creation; copy it to minds before making decisions.
        actors.forEach{a->val body=events.wildlife.bodies.getValue(a.placement.name)
            if (body.alive && body.point.distance(a.mind.point)>.0001f && !a.mind.navigation.open(body.point)) {
                // Migrate a living animal from an old village-side position before its first step.
                val safe = a.mind.navigation.points.asSequence().filter { events.wildlife.open(body.id,it) && !events.weatherDangerous(it) }
                    .minByOrNull { it.distance(body.point) }
                if (safe != null) body.point = safe
            }
            if(body.alive && body.point.distance(a.mind.point)>.0001f && a.mind.navigation.open(body.point)) a.mind.restorePoint(body.point)
        }
        if(active)events.wildlife.decide(dt,events)
        actors.forEach {a->
        val body=events.wildlife.bodies.getValue(a.placement.name)
        if(!body.alive) {
            a.setVisible(!body.returnedToSoil)
            if(body.returnedToSoil)return@forEach
            if(a.active)a.controller?.let{if(it.valid)it.pause()}
            if(!a.lying) {
                val support=ground.heightAt(body.point.x,body.point.z)
                requireNotNull(a.flank).layDown(a.entity,Vector3(body.point.x,support,body.point.z))
                a.lying=true
            }
            a.active=false
            return@forEach
        }
        if((a.lying || !a.entity.enabled) && a.mind.navigation.open(body.point)) a.mind.restorePoint(body.point)
        if(a.lying) {
            requireNotNull(a.flank).standUp()
            a.lying=false
        }
        a.setVisible(true)
        if(active) {
            val order=events.wildlife.order(a.placement.name)
            a.mind.command(order,events.hazards)
            val previousPoint = a.mind.point
            a.mind.advance(dt,events.hazards,events.wetSpots.filter{it.growth>.4f}.map{it.point},a.water) {
                events.wildlife.move(a.placement.name,it){p->a.mind.navigation.open(p)}
            }
            if (a.mind.gait == PrimitiveFaunaMind.Gait.FLEE && previousPoint != a.mind.point) {
                val who = if (a.placement.species == "mammoth") "猛犸" else "剑齿虎"
                events.reportEscape(a.placement.name, who, previousPoint)
            }
            if(a.mind.gait==PrimitiveFaunaMind.Gait.IDLE&&a.mind.activity=="graze")events.wetSpots.filter{it.point.distance(a.mind.point)<.06f}.forEach{it.growth=(it.growth-dt*.008f).coerceAtLeast(0f)}
            val life=events.village?.ecology
            if(life!=null && events.village?.agriculture==true && a.placement.species=="mammoth" && a.mind.gait==PrimitiveFaunaMind.Gait.IDLE && a.mind.activity=="graze" && a.mind.point.distance(life.field)<.032f) {
                life.graze(dt)
                if(events.seconds-lastRaid>18f){life.raids++;lastRaid=events.seconds}
            }
            val p=a.mind.point;a.entity.transform().position=Vector3(p.x,ground.heightAt(p.x,p.z),p.z)
            if(a.mind.gait!=PrimitiveFaunaMind.Gait.IDLE || !a.active)a.entity.transform().eulerAngles=EulerAngles(0f,a.mind.heading,0f)
        }
        a.play(a.mind.gait,active)
        }
        if(active) {
            events.wildlife.settle(dt,events)
            // Feeding may finish during settle. Remove the visual in the same
            // frame that releases its footprint, including on an older save.
            actors.forEach { actor ->
                if(events.wildlife.bodies.getValue(actor.placement.name).returnedToSoil)actor.setVisible(false)
            }
        }
        events.village?.ecology?.let{life->
            life.tiger=events.wildlife.bodies.values.filter{it.alive&&it.species==WildlifeWorld.Species.TIGER}.minByOrNull{it.point.distance(life.refuge)}?.point
            life.mammoth=events.wildlife.bodies.values.filter{it.alive&&it.species==WildlifeWorld.Species.ADULT}.minByOrNull{it.point.distance(life.field)}?.point
        }
    }
    fun close(){actors.forEach{it.close()}}
}
