package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.domain.usecase.WildlifeWorld
import com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3

/** Real Editor mesh sampler in PrimitiveWorld coordinates. A missed/water sample must fail. */
internal fun interface PrimitiveGroundSurface {
    fun heightAt(x: Float, z: Float): Float
    /** Queries may miss water or the mesh edge; strict placement still uses heightAt. */
    fun heightOrNull(x:Float,z:Float):Float? = try {heightAt(x,z)}
        catch(_:IllegalArgumentException){null} catch(_:IllegalStateException){null}
}

/** Measured palm positions in body-height units, exported with the skeletal clip. */
internal data class PrimitiveGripTrack(val seconds:Float,val samples:List<Pair<Vector3,Float>>) {
    init { require(seconds>0f&&samples.size>=2) }
    fun at(time:Float):Pair<Vector3,Float> {
        val index=(time/seconds).coerceIn(0f,1f)*(samples.size-1)
        val lo=index.toInt();val hi=minOf(lo+1,samples.lastIndex);val fraction=index-lo
        val a=samples[lo];val b=samples[hi]
        return (a.first*(1f-fraction)+b.first*fraction) to (a.second*(1f-fraction)+b.second*fraction)
    }
}

/** Delivered source measurements, not a guessed generic character transform. */
internal data class PrimitiveResidentAssetContract(
    val sourceFootAnchor: Vector3,
    val sourceForwardYawDegrees: Float,
    val targetHeightMetres: Float,
    val clipNames: Map<PrimitiveActivity, String>,
    val authoredMotionMetresPerSecond: Map<PrimitiveActivity, Float>,
    val clipRanges: Map<PrimitiveActivity, PrimitiveClipRange> = emptyMap(),
    val gripTracks: Map<PrimitiveActivity,PrimitiveGripTrack> = emptyMap(),
)

/** A real managed resident model, independently loaded or transferred from its Editor owner. */
internal data class PrimitiveLoadedResident(
    val id: String,
    val model: Entity,
    val asset: PrimitiveResidentAssetContract,
    val route: PrimitiveResidentRoute,
    val ownsImportedPlacement: Boolean = false,
)

/** Consumes every loaded model, including on failure; the caller retains bundle/world ownership. */
internal class PrimitiveResidentsRuntime(
    parent: Entity,
    loaded: List<PrimitiveLoadedResident>,
    private val ground: PrimitiveGroundSurface,
    private val campGround: PrimitiveGroundSurface = ground,
    firstField: Entity,
    private val local: PrimitiveLocalEffects? = null,
) {
    private data class Actor(
        val id: String,
        val owner: Entity,
        val route: PrimitiveResidentRoute,
        val heading: PrimitiveResidentHeading,
        val player: PrimitiveSkeletalPlayer,
        val asset: PrimitiveResidentAssetContract,
        val fireWalk: PrimitiveFireWalk?,
        val carriedProp: Entity?,
        val farmWalk: PrimitiveFarmWalk?,
        val campWalk: PrimitiveCampWalk?,
        val needGrip: Entity,
        var routeSeconds: Float = 0f,
        var poseArrived:Boolean=true,
        var lastResponse:com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Motion?=null,
    )

    private val root = Entity().apply { setName("PrimitiveRuntimeResidents"); parent.addChild(this) }
    private val actors = mutableListOf<Actor>()
    private var seconds = 0f
    private var active = true
    private var disposed = false

    init {
        val consumedModels = mutableSetOf<Long>()
        try {
            require(loaded.size in 8..10) { "Primitive visual preview requires 8–10 residents" }
            require(loaded.map { it.id }.distinct().size == loaded.size)
            require(loaded.map { it.model.id }.distinct().size == loaded.size) { "Each resident must own a distinct managed Entity/skeleton instance" }
            loaded.forEach { resident ->
                if (resident.ownsImportedPlacement) {
                    // The handoff explicitly transfers this authored resident owner's
                    // placement to the preview; keep all imported model/skeleton children.
                    resident.model.transform().apply {
                        position = Vector3.ZERO
                        eulerAngles = EulerAngles(0f, 0f, 0f)
                        scaleVector = Vector3(1f)
                    }
                }
                val owner = Entity().apply { setName(resident.id); root.addChild(this) }
                val needGrip = Entity().apply {
                    setName("${resident.id}_NeedGrip");owner.addChild(this)
                    transform().position=Vector3(0f,.00665f,.00245f)
                }
                val sizeOwner = Entity().apply { setName("${resident.id}_AssetFit"); owner.addChild(this) }
                val footOwner = Entity().apply { setName("${resident.id}_FootAnchor"); sizeOwner.addChild(this); addChild(resident.model) }
                consumedModels += resident.model.id
                val sourceBounds = resident.model.getVisualBounds(sizeOwner, recursive = true, enabledOnly = false)
                check(!sourceBounds.isEmpty() && sourceBounds.size.y.isFinite() && sourceBounds.size.y > 0f) {
                    "Cannot size real resident geometry: ${resident.id} bounds=$sourceBounds"
                }
                val contract = resident.asset
                require(contract.sourceFootAnchor.x.isFinite() && contract.sourceFootAnchor.y.isFinite() && contract.sourceFootAnchor.z.isFinite())
                require(contract.sourceForwardYawDegrees.isFinite())
                require(contract.targetHeightMetres.isFinite() && contract.targetHeightMetres > 0f)
                val scale = contract.targetHeightMetres / sourceBounds.size.y
                footOwner.transform().position = contract.sourceFootAnchor * -1f
                sizeOwner.transform().apply {
                    scaleVector = Vector3(scale)
                    eulerAngles = EulerAngles(0f, -contract.sourceForwardYawDegrees, 0f)
                }
                val first = resident.route.sample(0f)
                val worker = resident.id in listOf("PrimitiveResident_3", "PrimitiveResident_6")
                // Any resident can be assigned a daily fence/house supply job.
                val prop = run {
                    val source = requireNotNull(parent.requireNode("Resources_0").requireNode("NativeModel").components.get<ModelComponent>())
                    ModelEntity(source.mesh, source.materials.toList().toTypedArray()).apply {
                        setName("${resident.id}_CarriedWood"); owner.addChild(this)
                        val b = getVisualBounds(owner, recursive = true, enabledOnly = true)
                        val factor = PrimitiveWoodAppearance.BUNDLE_LENGTH_METRES / maxOf(b.size.x, b.size.y, b.size.z)
                        transform().scaleVector = Vector3(factor)
                        val after = getVisualBounds(owner, recursive = true, enabledOnly = true)
                        transform().position = Vector3(0f, .00665f, .00245f) - after.center
                        enabled = false
                    }
                }
                val fieldBounds = firstField.getVisualBounds(parent, recursive = true, enabledOnly = false)
                check(!fieldBounds.isEmpty())
                val farmWalk = if (resident.id == "PrimitiveResident_0") PrimitiveFarmWalk(
                    PrimitiveGroundPoint(fieldBounds.center.x-fieldBounds.halfExtent.x*.45f, fieldBounds.max.z+.004f),
                    PrimitiveGroundPoint(fieldBounds.center.x+fieldBounds.halfExtent.x*.45f, fieldBounds.max.z+.004f),
                ) else null
                val campSlot = listOf("PrimitiveResident_1", "PrimitiveResident_4", "PrimitiveResident_7").indexOf(resident.id)
                val campWalk = if (campSlot >= 0) {
                    val home = parent.requireNode("Shelter_0").getVisualBounds(parent, recursive=true, enabledOnly=false)
                    val rainShelter = parent.requireNode("RainShelter").getVisualBounds(parent, recursive=true, enabledOnly=false)
                    check(!home.isEmpty() && !rainShelter.isEmpty())
                    PrimitiveCampWalk(campSlot, PrimitiveGroundPoint(first.x,first.z),
                        PrimitiveGroundPoint(home.max.x+.015f+campSlot*.010f,
                            rainShelter.max.z+.022f+if(campSlot==1) .004f else 0f)).also { walk ->
                        val obstacles = listOf("Shelter_0","Shelter_1","Shelter_2","RainShelter","Resources_0","Resources_1","FirstField")
                            .map { parent.requireNode(it).getVisualBounds(parent,recursive=true,enabledOnly=false) }
                        walk.anchors.zipWithNext().forEach { (a,b) ->
                            val samples=kotlin.math.ceil(a.distanceTo(b)/.002f).toInt().coerceAtLeast(1)
                            for(i in 0..samples) {
                                val t=i.toFloat()/samples
                                val x=a.x+(b.x-a.x)*t;val z=a.z+(b.z-a.z)*t
                                check(campGround.heightAt(x,z).isFinite())
                                check(obstacles.none { x>it.min.x-.004f && x<it.max.x+.004f && z>it.min.z-.004f && z<it.max.z+.004f }) {
                                    "Camp visitor corridor overlaps prop: ${resident.id} ($x,$z)"
                                }
                            }
                        }
                        Log.i("CampLife", "ready visitor=${resident.id} source=(${first.x},${first.z}) camp=${walk.camp} terrain=actual margin=.004 resourceMutation=false")
                    }
                } else null
                val actor = Actor(resident.id, owner, resident.route, PrimitiveResidentHeading(first.yawDegrees),
                    PrimitiveSkeletalPlayer(resident.model, resident.id, contract.clipNames, contract.clipRanges), contract,
                    when (resident.id) { "PrimitiveResident_3" -> PrimitiveFireWalk(0, resourceMotionEnabled = true); "PrimitiveResident_6" -> PrimitiveFireWalk(1, resourceMotionEnabled = true); else -> null }, prop, farmWalk, campWalk, needGrip)
                actors += actor
                updateActor(actor, first, 0f)
                val nativeBounds = owner.getVisualBounds(parent, recursive = true, enabledOnly = true)
                check(!nativeBounds.isEmpty()) { "Resident has empty native geometry: ${resident.id}" }
                Log.i(TAG, "resident id=${resident.id} sourceBounds=$sourceBounds scale=$scale targetHeight=${contract.targetHeightMetres} nativeBounds=$nativeBounds boundsOwner=${parent.getName()} footSource=${contract.sourceFootAnchor}")
                val bodyBounds=resident.model.getVisualBounds(parent,recursive=true,enabledOnly=true)
                val bodyRadius=kotlin.math.hypot(bodyBounds.halfExtent.x,bodyBounds.halfExtent.z)
                local?.simulation?.wildlife?.add(resident.id,WildlifeWorld.Species.RESIDENT,Point(first.x,first.z),bodyRadius.coerceAtLeast(.003f))
            }
            val skeletonIds = actors.flatMap { it.player.skeletonRootIds }
            check(skeletonIds.distinct().size == skeletonIds.size) { "Managed residents must have independent skeleton roots" }
        } catch (error: Exception) {
            actors.forEach { it.player.dispose() }
            val unconsumedModels = loaded.filter { it.model.id !in consumedModels }.map { it.model }
            root.destroy(true)
            unconsumedModels.forEach { it.destroy(true) }
            throw error
        }
    }

    val fireParticipantsReady: Boolean get() = actors.filter { it.fireWalk != null }.all { it.poseArrived && local?.simulation?.busy(it.id) != true && it.fireWalk!!.ready }
    val shelterWorkersReady: Boolean get() = actors.filter { it.fireWalk != null }.all { it.poseArrived && local?.simulation?.busy(it.id) != true && it.fireWalk!!.workReady }
    val resourceWorkersReady: Boolean get() = actors.filter { it.fireWalk != null }.all { it.poseArrived && local?.simulation?.busy(it.id) != true && it.fireWalk!!.resourceReady }

    fun animate(deltaSeconds: Float, motionActive: Boolean, fire: FireSliceState? = null) {
        if (disposed) return
        require(deltaSeconds.isFinite() && deltaSeconds >= 0f)
        if (active != motionActive) {
            active = motionActive
            actors.forEach { it.player.setActive(active) }
        }
        if (!active) return
        seconds += deltaSeconds
        actors.forEach { actor ->
            val sim=local?.simulation
            val body=sim?.wildlife?.bodies?.get(actor.id)
            if (body!=null && !body.alive) {
                if(actor.owner.enabled){ actor.owner.enabled=false;actor.player.setActive(false) }
                actor.poseArrived=false;actor.lastResponse=null
                return@forEach
            }
            if(body!=null && (sim.enteredResidents.remove(actor.id) || !actor.owner.enabled)) {
                val p=body.point
                actor.owner.transform().position=Vector3(p.x,requireNotNull(sim.navigation.height(p.x,p.z)),p.z)
                actor.owner.enabled=true;actor.player.setActive(true)
            }
            val current=actor.owner.transform().position
            val response=timedResident("Village.residentDecision",actor.id) { local?.simulation?.respond(actor.id,
                com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point(current.x,current.z),deltaSeconds,
                actor.id in listOf("PrimitiveResident_2","PrimitiveResident_5","PrimitiveResident_8")) }
            actor.lastResponse=response
            if (response == null) actor.routeSeconds += deltaSeconds
            val ordinary = actor.route.sample(actor.routeSeconds)
            val lifeJob=local?.living?.jobs?.get(actor.id)
            val lifeEvent=lifeJob?.event
            // A blocked traveller can be stationary with a future work event.
            // Only the work clock proves the body actually reached its worksite.
            val working=lifeJob?.path?.isEmpty()==true&&lifeJob.elapsed>0f
            val listening=local?.living?.influence?.looking(actor.id)
            val peer=lifeJob?.peer?.let{local?.living?.residents?.get(it)}
            val lifeAction=when {
                lifeJob?.kind=="drumGather" && actor.asset.clipNames.containsKey(PrimitiveActivity.SIT_REST)->PrimitiveActivity.SIT_REST
                lifeJob?.kind=="drumGather"->PrimitiveActivity.TALK
                lifeJob?.kind=="pickSeedFruit"->PrimitiveActivity.GATHER
                lifeEvent==null->null
                lifeEvent==VillageEvent.FIRE_CISTERN->PrimitiveActivity.TEND_FIRE
                lifeEvent==VillageEvent.WEAVE_FISH_TRAP->PrimitiveActivity.LASH_ROOF
                lifeEvent==VillageEvent.MOULD_CLAY || lifeEvent==VillageEvent.LIFT_FISH_TRAP->PrimitiveActivity.GATHER
                lifeEvent==VillageEvent.SLEEP_HOME && lifeJob.house in local?.living?.restSeats.orEmpty() &&
                    actor.asset.clipNames.containsKey(PrimitiveActivity.SIT_REST)->PrimitiveActivity.SIT_REST
                lifeEvent in setOf(VillageEvent.EAT_FORAGE,VillageEvent.EAT_STOCK,VillageEvent.RATION_MEAL)->PrimitiveActivity.EAT
                lifeEvent in setOf(VillageEvent.DRINK_JAR,VillageEvent.DRINK_RIVER,VillageEvent.ALTERNATE_BANK)->PrimitiveActivity.DRINK
                lifeEvent==com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.GREET_NEIGHBOR->PrimitiveActivity.WAVE
                lifeEvent==com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.CHAT_TOGETHER->PrimitiveActivity.TALK
                lifeEvent==com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.STRETCH_BREAK->PrimitiveActivity.STRETCH
                lifeEvent==com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.CATCH_RAIN &&
                    actor.asset.clipNames.containsKey(PrimitiveActivity.CATCH_RAIN)->PrimitiveActivity.CATCH_RAIN
                lifeEvent.group in listOf(1,2,3,4,8)->PrimitiveActivity.GATHER
                else->PrimitiveActivity.IDLE
            }
            val pose = when {
                response != null -> {
                    val dx=response.point.x-current.x;val dz=response.point.z-current.z
                    val speed=if(response.moving && deltaSeconds>0f) kotlin.math.hypot(dx,dz)/deltaSeconds else 0f
                    val walkYaw=if(response.moving) kotlin.math.atan2(dx,dz)*180f/kotlin.math.PI.toFloat() else actor.owner.transform().eulerAngles.yaw
                    val toward=listening ?: peer
                    val facing=if(toward==null)walkYaw else {
                        val look=kotlin.math.atan2(toward.x-current.x,toward.z-current.z)*180f/kotlin.math.PI.toFloat()
                        val turn=(look-walkYaw+540f)%360f-180f
                        if(response.moving && listening!=null)walkYaw+turn.coerceIn(-22f,22f) else if(response.moving)walkYaw else look
                    }
                    PrimitiveResidentPose(response.point.x,response.point.z,
                        if(working && lifeAction==PrimitiveActivity.SIT_REST)0f else facing,
                        if(!response.moving&&working&&
                            lifeAction in setOf(PrimitiveActivity.EAT,PrimitiveActivity.DRINK)) requireNotNull(lifeAction)
                        else if(response.carrying) PrimitiveActivity.CARRY
                        else if(response.moving) PrimitiveActivity.WALK
                        else if(response.action=="build") PrimitiveActivity.LASH_ROOF
                        else if(working) lifeAction ?: if(response.action in listOf("clear","forage","harvest","tend","gatherFood","cultivate")) PrimitiveActivity.GATHER else PrimitiveActivity.IDLE
                        else PrimitiveActivity.IDLE,speed)
                }
                fire != null && actor.fireWalk != null -> actor.fireWalk.sample(ordinary, fire, deltaSeconds)
                fire != null && actor.farmWalk != null -> actor.farmWalk.sample(ordinary, fire.agricultural, fire.activeMillis-fire.lastWaterAt in 0..4_000, deltaSeconds)
                actor.campWalk != null -> actor.campWalk.sample(deltaSeconds,
                    fire != null && fire.activeMillis-fire.lastWaterAt in 0..4_000,
                    PrimitiveGroundPoint(-.18f,.205f))
                else -> ordinary
            }
            timedResident("Village.residentPose",actor.id) { updateActor(actor, pose, deltaSeconds) }
            actor.carriedProp?.enabled = if(response!=null) response.carrying && (lifeJob?.wood!=null || lifeJob==null) else actor.fireWalk!=null && ((fire?.carriedWood ?: 0) > 0 || (fire?.haulingWood ?: 0) > 0)
        }
    }

    private inline fun <T> timedResident(stage:String,id:String,action:()->T):T {
        val started=android.os.SystemClock.elapsedRealtimeNanos()
        android.os.Trace.beginSection(stage)
        try { return action() }
        finally {
            android.os.Trace.endSection()
            val millis=(android.os.SystemClock.elapsedRealtimeNanos()-started)/1_000_000.0
            if(millis>25) {
                val job=local?.living?.jobs?.get(id)
                Log.w("ResidentTiming","stage=$stage id=$id millis=$millis event=${job?.event} remaining=${job?.path?.size} work=${job?.elapsed}")
            }
        }
    }

    private fun updateActor(actor: Actor, pose: PrimitiveResidentPose, deltaSeconds: Float) {
        val current=actor.owner.transform().position
        val desired=Point(pose.x,pose.z)
        val registered=local?.simulation?.wildlife?.bodies?.containsKey(actor.id)==true
        val actual=if(registered) {
            val origin=Point(current.x,current.z)
            val distance=origin.distance(desired)
            val limit=maxOf(pose.metresPerSecond,.016f)*deltaSeconds
            val target=if(distance>limit && distance>0f)Point(origin.x+(desired.x-origin.x)*limit/distance,origin.z+(desired.z-origin.z)*limit/distance)else desired
            local!!.simulation.moveResident(actor.id,target)
        } else desired
        // Fleeing and hearth routes bypass LivingVillage.respond. Social
        // decisions must still see the final collision-resolved body position.
        if(registered)local?.living?.residents?.set(actor.id,actual)
        actor.poseArrived=actual.distance(desired)<.004f
        val moved=actual.distance(Point(current.x,current.z))
        val activity=if(registered && moved>.000001f) {
            if(pose.activity==PrimitiveActivity.CARRY)PrimitiveActivity.CARRY else PrimitiveActivity.WALK
        } else if(pose.activity==PrimitiveActivity.CARRY) {
            // A stopped carrier keeps the cradle pose; the gait is paused below.
            PrimitiveActivity.CARRY
        } else if(registered && (!actor.poseArrived || pose.activity==PrimitiveActivity.WALK)) {
            PrimitiveActivity.IDLE
        } else pose.activity
        val y = local?.support(actual.x,actual.z) ?: (if (actor.fireWalk != null || actor.farmWalk != null || actor.campWalk != null) campGround else ground).heightAt(actual.x, actual.z)
        check(y.isFinite()) { "No dry terrain sample for ${actor.id} at (${pose.x}, ${pose.z})" }
        actor.owner.transform().apply {
            // No synthetic Y offset: locomotion is parent X/Z, limbs come only from the clip.
            position = Vector3(actual.x, y, actual.z)
            eulerAngles = EulerAngles(0f, actor.heading.advance(pose.yawDegrees, deltaSeconds), 0f)
        }
        val speed=if(registered && deltaSeconds>0f)moved/deltaSeconds else pose.metresPerSecond
        val playbackSpeed = if (speed > .000001f && activity in listOf(PrimitiveActivity.WALK,PrimitiveActivity.CARRY)) {
            val authoredSpeed = requireNotNull(actor.asset.authoredMotionMetresPerSecond[activity]) {
                "Missing measured gait speed for ${actor.id} $activity"
            }
            require(authoredSpeed.isFinite() && authoredSpeed > 0f)
            speed / authoredSpeed
        } else if(activity==PrimitiveActivity.CARRY) 0f else 1f
        val gripTrack=actor.asset.gripTracks[activity]
        val workTime=local?.living?.jobs?.get(actor.id)?.elapsed?:0f
        val seated=activity==PrimitiveActivity.SIT_REST
        actor.player.play(activity, if(seated||gripTrack!=null||activity==PrimitiveActivity.CARRY&&playbackSpeed==0f)0f else actor.route.phaseSeconds,
            if(seated||gripTrack!=null)0f else playbackSpeed)
        if(seated)actor.player.seekWorkTime(workTime)
        if(gripTrack!=null) {
            actor.player.seekWorkTime(workTime)
            val grip=gripTrack.at(workTime)
            actor.needGrip.transform().apply {
                position=grip.first*actor.asset.targetHeightMetres
                eulerAngles=EulerAngles(grip.second,0f,0f)
            }
        } else actor.needGrip.transform().apply {
            position=Vector3(0f,.00665f,.00245f)
            eulerAngles=EulerAngles(0f,0f,0f)
        }
        actor.player.enforceSegmentLoop()
    }

    /** Native transformed geometry at route supports and all headings, for the one outer fit.
     * Skeletal animated extents still require the delivered clip review and runtime monitoring.
     */
    fun measureRouteEnvelope(boundsOwner: Entity): BoundingBox {
        check(!disposed)
        var envelope: BoundingBox? = null
        actors.forEach { actor ->
            val base = actor.owner.ownerPose()
            try {
                val supportHeights = mutableListOf<Float>()
                val routeStops = actor.route.stops + ((actor.farmWalk?.anchors ?: emptyList()) +
                    (actor.campWalk?.anchors ?: emptyList())).map { PrimitiveRouteStop(it) }
                routeStops.indices.forEach { index ->
                    val from = routeStops[index].point
                    val to = routeStops[(index + 1) % routeStops.size].point
                    val samples = kotlin.math.ceil(from.distanceTo(to) / .002f).toInt().coerceAtLeast(1)
                    for (sample in 0..samples) {
                        val fraction = sample.toFloat() / samples
                        val x = from.x + (to.x - from.x) * fraction
                        val z = from.z + (to.z - from.z) * fraction
                        val y = (if (actor.farmWalk != null || actor.campWalk != null) campGround else ground).heightAt(x, z)
                        check(y.isFinite()) { "Resident dry route misses real terrain: ${actor.id} ($x,$z)" }
                        supportHeights += y
                    }
                }
                // Translation extrema on each straight X/Z segment occur at its ends.
                // Combining both terrain Y extrema at every end is conservative, and
                // avoids thousands of identical native bounds queries along one segment.
                routeStops.forEach { stop ->
                    for (y in listOf(supportHeights.min(), supportHeights.max())) {
                        actor.owner.transform().position = Vector3(stop.point.x, y, stop.point.z)
                        for (yaw in 0 until 360 step 15) {
                            actor.owner.transform().eulerAngles = EulerAngles(0f, yaw.toFloat(), 0f)
                            val measured = actor.owner.getVisualBounds(boundsOwner, recursive = true, enabledOnly = true)
                            check(!measured.isEmpty()) { "Cannot measure resident native route geometry: ${actor.id}" }
                            envelope = envelope?.let { unionBounds(it, measured) } ?: measured
                        }
                    }
                }
            } finally { base.restore(actor.owner) }
        }
        return requireNotNull(envelope)
    }

    fun logDiagnostics() {
        if (disposed) return
        local?.let { effects->
            actors.forEach { actor->
                val job=effects.living.jobs[actor.id]
                val at=actor.owner.transform().position
                val body=effects.simulation.wildlife.bodies[actor.id]
                val person=effects.living.life.person(actor.id)
                Log.i("ResidentWork","id=${actor.id} at=($at) body=${body?.point} radius=${body?.radius} " +
                    "action=${actor.lastResponse?.action} fleeing=${actor.lastResponse?.fleeing} " +
                    "job=${job?.kind} event=${job?.event} goal=${job?.goal} next=${job?.path?.firstOrNull()} " +
                    "remaining=${job?.path?.size} work=${job?.elapsed} blocked=${job?.blocked} carrying=${job?.carrying} " +
                    "foodHeld=${job?.carrying==true && job.food?.carrier==actor.id && effects.living.foodBundles.any{it===job.food}} " +
                    "meals=${person.meals} drinks=${person.drinks} " +
                    "hunger=${person.hunger} thirst=${person.thirst} fatigue=${person.fatigue} ${actor.player.diagnostic()}")
            }
        }
        val samples = actors.take(3).joinToString(separator = " | ") { actor ->
            val position = actor.owner.transform().position
            "id=${actor.id} position=$position groundY=${position.y} ${actor.player.diagnostic()}"
        }
        val visitors = actors.filter { it.campWalk != null }.joinToString(" | ") {
            "id=${it.id} position=${it.owner.transform().position} ${it.player.diagnostic()}"
        }
        Log.i("CampLife", "visitors={$visitors} clock=presentation-only resourceMutation=false")
        Log.i(TAG, "residents ready=true count=${actors.size} roots=${root.getChildren().size} skeletonRoots=${actors.sumOf { it.player.skeletonCount }} uniqueSkeletonRoots=${actors.flatMap { it.player.skeletonRootIds }.distinct().size} routeSeconds=$seconds active=$active samples={$samples}")
    }

    fun visualBounds(boundsOwner: Entity): BoundingBox =
        root.getVisualBounds(boundsOwner, recursive = true, enabledOnly = true)

    fun dispose() {
        if (disposed) return
        disposed = true
        actors.forEach { it.player.dispose() }
        actors.clear()
        root.destroy(true)
    }

    companion object { private const val TAG = "PrimitivePreview" }
}
