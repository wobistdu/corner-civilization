package com.example.cornercivilization.p37f776dc.scene

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import org.json.JSONObject
import androidx.compose.ui.unit.IntSize
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import kotlin.math.abs

/** One common world fit; the physical toolbar remains independent of the world scale. */
internal class PrimitivePreviewRuntime private constructor(
    private val bundles: List<AssetBundle>,
    private val loadedScenes: List<Pair<AssetBundle, String>>,
    private val model: Entity,
    private val content: SpatialViewContent,
) {
    private val placementOwner = Entity().apply { setName("PrimitivePlacementOwner") }
    private val fittedSceneRoot = Entity().apply { setName("PrimitiveFittedSceneRoot") }
    private val toolbarOwner = Entity().apply { setName("PrimitiveToolbarOwner") }
    private lateinit var world: Entity
    private lateinit var residents: PrimitiveResidentsRuntime
    private lateinit var fire: PrimitiveFireRuntime
    private lateinit var kit: ShelterFireKit
    private var audio: PrimitiveWorldAudio? = null
    private var fauna: PrimitiveFaunaRuntime? = null
    private var villageView: PrimitiveVillagePresentation? = null
    private var villageStore: LivingVillageStore? = null
    private var localEffects: PrimitiveLocalEffects? = null
    private val rainPointers = mutableMapOf<String, Int>()
    private val inputTargets = mutableMapOf<Long, String>()
    private var legacyFlame: Entity? = null
    private var legacyParticles: Entity? = null
    private var timberGeometry: PrimitiveTimberGeometry? = null
    private var woodAppearance: PrimitiveWoodAppearance? = null
    private var appearance: PrimitiveUnlitAppearance? = null
    private var groundAppearance: PrimitiveGroundAppearance? = null
    private var groundDetail: PrimitiveGroundDetail? = null
    private var livingTrail: PrimitiveLivingTrail? = null
    private var treeBakedAppearance: PrimitiveTreeBakedAppearance? = null
    private var propBakedAppearance: PrimitivePropBakedAppearance? = null
    private lateinit var memory: PrimitiveWorldMemoryRuntime
    private var fireState = FireSliceState()
    private val scrollOwner = Entity().apply { setName("VillageChronicleOwner") }
    var scrollOpen by androidx.compose.runtime.mutableStateOf(false)
        private set
    val chronicle get() = localEffects?.simulation?.chronicle

    fun toggleScroll() {
        if (ready && !disposed) {
            scrollOpen = !scrollOpen
            scrollOwner.enabled = scrollOpen
            Log.i("VillageScroll", "open=$scrollOpen entries=${chronicle?.snapshot()?.size}")
        }
    }
    private var fireBaseline = false
    private lateinit var panel: Entity
    private lateinit var bodyEnvelope: BoundingBox
    private var fitKey: String? = null
    private var disposed = false
    private var evidenceSeconds = 0f
    private var hitAuditComplete = false
    private var hitAuditAttempts = 0
    private var safeHalfExtent: Vector3? = null
    private var safeCenter = Vector3.ZERO
    private var lastViewSize = IntSize.Zero
    private var lastFooterSize = IntSize.Zero
    private var lastWindowSize = IntSize.Zero
    var ready = false
        private set

    private suspend fun initialize(
        loadedResidents: List<PrimitiveLoadedResident>, ground: PrimitiveGroundSurface,
        attachments: SpatialViewAttachments, hiddenNodes: List<String>,
        fireCentroids: JSONObject, kitModel: Entity, campGround: PrimitiveTerrain,
        fieldModel: Entity, groundBitmap: android.graphics.Bitmap, naturalTree: Entity,
        animalModels: List<Entity>, saveDirectory: java.io.File?,
        awaitActive: suspend (String) -> Unit,
    ) {
        check(android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
        var segmentStarted = android.os.SystemClock.elapsedRealtime()
        suspend fun checkpoint(stage: String) {
            Log.i("VillageStartup", "stage=$stage elapsedMillis=${android.os.SystemClock.elapsedRealtime()-segmentStarted}")
            awaitActive("after-$stage")
            segmentStarted = android.os.SystemClock.elapsedRealtime()
        }
        var residentsConsumed = false
        try {
            content.addEntity(placementOwner)
            placementOwner.addChild(fittedSceneRoot)
            placementOwner.addChild(toolbarOwner)
            fittedSceneRoot.addChild(model)
            world = model.requireNode("PrimitiveWorld")
            world.enableTree()
            awaitActive("attached-world")
            timberGeometry = PrimitiveTimberGeometry(world)
            miniaturizeProps(world)
            arrangePrimitiveTribe(world, campGround)
            world.requireNode("FaunaLibrary").enabled = false
            val replacedTerrain = world.requireNode("TerrainSurface").requireNode("NativeModel")
            replacedTerrain.enabled = false
            fun renderedModels(entity: Entity): Int =
                (if (entity.enabled && entity.components.get<ModelComponent>()?.isVisible == true) 1 else 0) +
                    entity.getChildren().sumOf(::renderedModels)
            check(renderedModels(replacedTerrain) == 0) { "Replaced terrain is still rendered" }
            Log.i(TAG, "terrainReplacement oldRenderedModels=0 newRenderedModels=${renderedModels(world.requireNode("TerrainV5"))}")
            scaleGrounded(model.requireNode("Hourglass"), .7f)
            hiddenNodes.forEach { world.requireNode(it).enabled = false }
            world.requireNode("TerrainSurface")
            model.requireNode("Hourglass")
            panel = requireNotNull(attachments.entity(FOOTER_PANEL)) { "Missing primitive footer attachment" }
            toolbarOwner.addChild(panel)
            placementOwner.addChild(scrollOwner)
            scrollOwner.addChild(requireNotNull(attachments.entity(SCROLL_PANEL)))
            scrollOwner.enabled = false
            checkpoint("placement")
            fire = PrimitiveFireRuntime(world, fireCentroids)
            kit = ShelterFireKit(world, kitModel, fire.center, fire.emissionCenter, campGround)
            memory = PrimitiveWorldMemoryRuntime(world, model.requireNode("Hourglass"), fieldModel, campGround)
            localEffects = PrimitiveLocalEffects(world, campGround)
            checkpoint("local-effects")
            fauna = PrimitiveFaunaRuntime(world, animalModels, campGround, requireNotNull(localEffects).simulation)
            checkpoint("fauna")
            residentsConsumed = true
            residents = PrimitiveResidentsRuntime(world, loadedResidents, ground, campGround, fieldModel, localEffects)
            checkpoint("residents")
            audio = PrimitiveWorldAudio(world, requireNotNull(localEffects).simulation)
            legacyFlame = world.requireNode("RuntimeHearthFlame")
            legacyParticles = world.requireNode("CampfireParticles")
            listOf("FloodplainTerrain", "ShallowRiver").forEach { name ->
                val target = world.requireNode("TerrainV5").requireNode(name)
                val mesh = requireNotNull(target.components.get<ModelComponent>()).mesh
                target.components.set(CollisionComponent(listOf(ShapeResource.createStaticMesh(mesh)), PhysicsMaterialResource()))
                target.components.set(InteractableComponent())
                inputTargets[target.id] = "ground"
            }
            listOf("Shelter_0", "Shelter_1", "Shelter_2", "RainShelter").forEach { name ->
                val target = world.requireNode(name)
                target.installVisualBoundsTarget()
                inputTargets[target.id] = name
            }
            inputTargets[world.requireNode("Campfire").id] = "ground"
            Log.i("LocalInput", "targets ready terrain=static-mesh houses=4 coordinate=view-local-pixels-to-world-metres")
            fire.rainRoofY = kit.roofY
            checkpoint("input-audio")
            replacePrimitiveTrees(world, naturalTree, campGround)
            treeBakedAppearance = PrimitiveTreeBakedAppearance(world)
            checkpoint("trees")
            propBakedAppearance = PrimitivePropBakedAppearance(world)
            groundAppearance = PrimitiveGroundAppearance(world, groundBitmap)
            checkpoint("materials")
            groundDetail = PrimitiveGroundDetail(world)
            groundDetail!!.load(campGround, loadedResidents.map { it.route }, awaitActive)
            checkpoint("ground-detail")
            livingTrail = PrimitiveLivingTrail(world, campGround, fieldModel)
            appearance = PrimitiveUnlitAppearance(model)
            woodAppearance = PrimitiveWoodAppearance(world)
            checkpoint("appearance")
            villageView = PrimitiveVillagePresentation(world,requireNotNull(localEffects))
            checkpoint("village-view")
            if(saveDirectory!=null && !java.io.File(saveDirectory,"living-village-v11.json").exists())
                arrangeNewVillage()
            villageStore = saveDirectory?.let { LivingVillageStore(it,world,requireNotNull(localEffects).simulation) }
            checkpoint("restore-current-village")
            // Count every potential instance, including source/held wood and all water drops together.
            // Replaced source actors are never rendered; include the readable SGS flame core.
            fun peakTriangles(entity: Entity): Int {
                if (entity.getName() in hiddenNodes || entity.getName() in listOf("FaunaLibrary", "NaturalTreePrototype") || entity.getName() in listOf("Tree_0", "Tree_2", "Tree_5", "Tree_7", "Tree_9", "Tree_11", "Tree_13", "Tree_15")) return 0
                if (entity.getName() == "NativeModel" && entity.getParent()?.getName() == "TerrainSurface") return 0
                val geometry = entity.components.get<ModelComponent>()?.let { if (it.isVisible) it.mesh.getTriangleCount() else 0 } ?: 0
                return geometry + entity.getChildren().sumOf(::peakTriangles)
            }
            // Dynamic cloud lobes are created on demand; reserve a conservative 1024 triangles each.
            val trianglePeak = peakTriangles(model) + 264 + PrimitiveFireParticles.MAX_TRIANGLES + PrimitiveRainParticles.MAX_TRIANGLES + PrimitiveRainParticles.MAX_CLOUD_LOBES * 1024
            check(trianglePeak <= 800_000) { "Prototype triangle cap exceeded: $trianglePeak" }
            Log.i("ShelterPrototype", "peakTriangles=$trianglePeak includesAllMeshInstances=true allParticlePresets=${264 + PrimitiveFireParticles.MAX_TRIANGLES + PrimitiveRainParticles.MAX_TRIANGLES + PrimitiveRainParticles.MAX_CLOUD_LOBES * 1024} cap=800000")
            val staticEnvelope = model.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = true)
            check(!staticEnvelope.isEmpty()) { "New primitive scene has no visible native geometry" }
            val memoryEnvelope = boundsOf(listOf(memory.maxEnvelope.min, memory.maxEnvelope.max).map { world.convertPositionTo(it, fittedSceneRoot) })
            val effects = requireNotNull(localEffects).maxEnvelope
            val effectsEnvelope = boundsOf(listOf(effects.min, effects.max).map { world.convertPositionTo(it, fittedSceneRoot) })
            bodyEnvelope = unionBounds(unionBounds(unionBounds(staticEnvelope, memoryEnvelope), effectsEnvelope), residents.measureRouteEnvelope(fittedSceneRoot))
            Log.i(TAG, "preview loaded residents=${loadedResidents.size} village=primitive development=conditions hourglass=hidden baseEnvelope=$bodyEnvelope")
            checkpoint("envelope")
        } catch (error: Exception) {
            if (!residentsConsumed) loadedResidents.filterNot { it.ownsImportedPlacement }.forEach { it.model.destroy(true) }
            villageStore?.close()
            audio?.close()
            if (::residents.isInitialized) residents.dispose()
            fauna?.close()
            placementOwner.destroy(true)
            if (::fire.isInitialized) fire.closeResources()
            villageView?.close()
            woodAppearance?.close()
            timberGeometry?.close()
            localEffects?.close()
            appearance?.close()
            groundAppearance?.close()
            groundDetail?.close()
            livingTrail?.close()
            treeBakedAppearance?.close()
            propBakedAppearance?.close()
            loadedScenes.distinct().forEach { (bundle, scene) -> bundle.releaseModel(scene) }
            bundles.distinct().forEach { it.close() }
            throw error
        }
    }

    /** A new village opens with gatherers already near real food, before any time advances. */
    private fun arrangeNewVillage() {
        val events=requireNotNull(localEffects).simulation
        val village=requireNotNull(events.village)
        val sites=village.sources.filter { !events.dangerous(it) }
            .sortedBy { it.distance(events.depot) }.toMutableList()
        for(id in listOf("PrimitiveResident_2","PrimitiveResident_7")) {
            val site=sites.firstOrNull { events.navigation.path(it,events.depot)!=null }?:break
            sites.remove(site)
            val at=events.navigation.points.asSequence()
                .filter { it.distance(site) in .007f.. .018f && !events.dangerous(it) && events.wildlife.open(id,it) }
                .minByOrNull { it.distance(events.depot) }?:continue
            events.wildlife.bodies.getValue(id).point=at
            village.residents[id]=at
            world.requireNode(id).transform().position=Vector3(at.x,requireNotNull(events.navigation.height(at.x,at.z)),at.z)
            Log.i("VillageOpening","gatherer=$id at=$at foodSite=$site stock=0 noWorkCompleted=true")
        }
    }

    fun animate(deltaSeconds: Float, active: Boolean) {
        if (disposed) return
        android.os.Trace.beginSection("Village.frame")
        try {
        localEffects?.living?.let { village ->
            village.agriculture=village.agriculture || fireState.fieldEstablished
            village.settled=fireState.knowsFire && fireState.shelter==com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.COMPLETE && shelterOperational
            village.life.reserveHearthBuilders=!village.settled
            val warm=fireState.phase==com.example.cornercivilization.p37f776dc.domain.model.FirePhase.BURNING
            if(warm&&!village.life.hearthWarm)village.life.hearthFuel=maxOf(village.life.hearthFuel,.5f)
            village.life.hearthWarm=warm
            fireState=fireState.copy(fieldEstablished=village.agriculture)
        }
        traced("Village.needs") { if (active && ready) localEffects?.advance(deltaSeconds) }
        traced("Village.residents") { residents.animate(deltaSeconds, active && ready, fireState) }
        traced("Village.fauna") { fauna?.animate(deltaSeconds, active && ready) }
        audio?.update(deltaSeconds, active && ready)
        fire.apply(visibleFireState())
        kit.apply(visibleFireState())
        legacyFlame?.enabled = false
        legacyParticles?.enabled = false
        localEffects?.setActive(active && ready)
        traced("Village.localView") { localEffects?.render() }
        memory.apply(fireState, active)
        traced("Village.consequences") { villageView?.apply(deltaSeconds,active&&ready) }
        traced("Village.save") { villageStore?.tick(if(active&&ready)deltaSeconds else 0f,force=!active) }
        livingTrail?.apply(fireState)
        if (!ready) return
        evidenceSeconds += deltaSeconds
        if (evidenceSeconds < 5f) return
        evidenceSeconds %= 5f
        if (!hitAuditComplete && hitAuditAttempts < 3) {
            hitAuditAttempts++
            try { hitAuditComplete = auditLocalTargets() }
            catch (error: Exception) { Log.e("LocalInput", "Read-only target audit failed", error) }
        }
        val visual = fittedSceneRoot.getVisualBounds(placementOwner, recursive = true, enabledOnly = true)
        val safe = requireNotNull(safeHalfExtent)
        val contained = !visual.isEmpty() && insideWindow(visual, safe)
        residents.logDiagnostics()
        appearance?.verify()
        Log.i(TAG, "live-bounds ready=$ready nativeModelOwners=${countNativeModelOwners(model)} owner=PrimitivePlacementOwner visual=$visual safeHalf=$safe fitScale=${fittedSceneRoot.transform().scaleVector.x} numericStatus=${if (contained) "PASS" else "FAIL"} visualStatus=UNOBSERVED")
        check(contained) { "Animated primitive scene exceeds Volume: $visual safeHalf=$safe" }
        } finally { android.os.Trace.endSection() }
    }

    private inline fun traced(name: String, action: () -> Unit) {
        android.os.Trace.beginSection(name)
        try { action() } finally { android.os.Trace.endSection() }
    }

    fun refit(content: SpatialViewContent, viewSize: IntSize, footerSize: IntSize, windowSize: IntSize): Boolean {
        if (disposed) return false
        lastViewSize = viewSize
        lastFooterSize = footerSize
        lastWindowSize = windowSize
        if (viewSize.width <= 0 || viewSize.height <= 0 || footerSize.width <= 0 || footerSize.height <= 0 || windowSize.width <= 0 || windowSize.height <= 0) {
            ready = false
            fitKey = null
            return false
        }
        // Global pixels start at the WindowContainer's top-left-back corner.
        // Use all eight corners of the same cubic depth contract as Main. SDK 6.2.8
        // can retain the previous SpatialView center after a native depth resize;
        // converting the window region accounts for that offset without moving
        // the evidence owner or assuming the view center is the window center.
        val windowCorners = buildList {
            for (x in listOf(0f, windowSize.width.toFloat()))
                for (y in listOf(0f, windowSize.height.toFloat()))
                    for (z in listOf(0f, windowSize.width.toFloat()))
                        add(content.convertPosition(Vector3(x, y, z), ViewCoordinateSpace.Global, content.localSpatialCoordinateSpace))
        }
        val region = boundsOf(windowCorners)
        val safe = region.halfExtent
        if (!safe.finitePositive()) return false
        val key = "$viewSize|$footerSize|$windowSize|$region"
        if (fitKey == key) return ready
        ready = false
        safeCenter = region.center
        val nativeSize = content.convertSize(Vector3(footerSize.width.toFloat(), footerSize.height.toFloat(), 0f), ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val width = abs(nativeSize.x)
        val height = abs(nativeSize.y)
        if (!width.isFinite() || !height.isFinite() || !nativeSize.z.isFinite() || width <= 0f || height <= 0f) return false
        val corners = listOf(Vector3(-width / 2, -height / 2, 0f), Vector3(-width / 2, height / 2, 0f), Vector3(width / 2, -height / 2, 0f), Vector3(width / 2, height / 2, 0f))
        val uiBase = boundsOf(corners.map { panel.convertPositionTo(it, toolbarOwner) })
        check(uiBase.size.x > 0f && uiBase.size.y > 0f)
        val uiScale = minOf(1f, safe.x * 1.96f / uiBase.size.x)
        val uiHeight = uiBase.size.y * uiScale
        val factor = minOf(safe.x * 1.96f / bodyEnvelope.size.x, safe.z * 1.96f / bodyEnvelope.size.z)
        check(factor.isFinite() && factor > 0f)
        fittedSceneRoot.transform().apply {
            scaleVector = Vector3(factor)
            position = safeCenter + Vector3(-bodyEnvelope.center.x * factor,
                -safe.y + BOTTOM_MARGIN_METRES + uiHeight + BODY_GAP_METRES - bodyEnvelope.min.y * factor,
                -bodyEnvelope.center.z * factor)
        }
        val measuredWorld = fittedSceneRoot.getVisualBounds(placementOwner, recursive = true, enabledOnly = true)
        check(!measuredWorld.isEmpty() && insideWindow(measuredWorld, safe)) { "Primitive native world exceeds Volume after explicit common fit: $measuredWorld safeHalf=$safe center=$safeCenter" }
        val finalBody = boundsOf(listOf(bodyEnvelope.min, bodyEnvelope.max).map { fittedSceneRoot.convertPositionTo(it, placementOwner) })
        val target = Vector3(finalBody.center.x, finalBody.min.y - BODY_GAP_METRES - uiHeight / 2f, finalBody.max.z + FRONT_GAP_METRES)
        toolbarOwner.transform().apply {
            scaleVector = Vector3(uiScale)
            position = target - uiBase.center * uiScale
        }
        val finalPanel = boundsOf(corners.map { panel.convertPositionTo(it, placementOwner) })
        // Both attachments use the same dp density. Keep the scroll behind the far bank,
        // facing the player, with enough floor clearance for trees and roof silhouettes.
        val scrollWidth = uiBase.size.x * 790f / 640f
        val scrollHeight = uiBase.size.y * 292f / 64f
        val scrollScale = minOf(safe.x * 1.55f / scrollWidth, safe.y * .78f / scrollHeight)
        val extent = Vector3(scrollWidth * scrollScale / 2f, scrollHeight * scrollScale / 2f, 0f)
        val center = Vector3(finalBody.center.x,
            minOf(safeCenter.y + safe.y - extent.y - .005f, finalBody.max.y + extent.y + safe.y * .06f),
            finalBody.min.z + finalBody.size.z * .08f)
        scrollOwner.transform().apply { scaleVector = Vector3(scrollScale); position = center }
        check(insideWindow(BoundingBox(center, extent), safe)) { "Village scroll outside Volume" }
        val combined = unionBounds(finalBody, finalPanel)
        val contained = insideWindow(combined, safe)
        check(contained) { "Primitive preview body/toolbar exceeds Volume: $combined safeHalf=$safe" }
        safeHalfExtent = safe
        ready = true
        fitKey = key
        Log.i(TAG, "fit revision=$key epsilon=.001 owner=PrimitivePlacementOwner worldScale=${fittedSceneRoot.transform().scaleVector.x} baseEnvelope=$bodyEnvelope finalPlannedBody=$finalBody finalVisualBody=$measuredWorld finalPanel=$finalPanel safeHalf=$safe windowCenter=$safeCenter numericStatus=PASS visualStatus=UNOBSERVED")
        return true
    }

    // Native depth can arrive after the Compose width/height callback. The fit key
    // keeps this cheap when nothing changed, including while residents are paused.
    fun refreshLayout() = refit(content, lastViewSize, lastFooterSize, lastWindowSize)

    val fireParticipantsReady: Boolean get() = residents.fireParticipantsReady
    val shelterOperational: Boolean get() = localEffects?.living?.houses?.get("RainShelter")?.let{!it.ruined&&it.health>35f} ?: true
    val fieldEstablished: Boolean get() = localEffects?.living?.agriculture == true || fireState.fieldEstablished
    private fun visibleFireState() = if(shelterOperational)fireState else fireState.copy(shelter=com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.BUILDING)
    fun applyFire(state: FireSliceState) {
        if (fireBaseline && fireState.phase != state.phase) {
            val line = when (state.phase) {
                com.example.cornercivilization.p37f776dc.domain.model.FirePhase.APPROACH -> "有人向火塘走去。"
                com.example.cornercivilization.p37f776dc.domain.model.FirePhase.KINDLING -> "火塘边的人俯下身，开始引火。"
                com.example.cornercivilization.p37f776dc.domain.model.FirePhase.BURNING -> "火塘亮了起来。"
                com.example.cornercivilization.p37f776dc.domain.model.FirePhase.WET -> "火塘浸湿，火光暗了下去。"
                else -> null
            }
            line?.let { localEffects?.simulation?.tell(it) }
        }
        if (fireBaseline && fireState.shelter != state.shelter && state.shelter == com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.COMPLETE)
            localEffects?.simulation?.tell("最后一根棚杆绑紧，火塘有了遮雨棚。")
        if (fireBaseline && !fireState.fieldEstablished && state.fieldEstablished)
            localEffects?.simulation?.tell("村人开出了第一块田。")
        fireBaseline = true
        fireState = state.copy(fieldEstablished=state.fieldEstablished || fieldEstablished)
        localEffects?.living?.houses?.get("RainShelter")?.let { it.available=state.shelter==com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.COMPLETE || it.rebuilds>0 }
        fire.apply(visibleFireState());kit.apply(visibleFireState())
    }
    val shelterWorkersReady: Boolean get() = residents.shelterWorkersReady
    val shelterTask get() = fireState.shelter
    val resourceWorkersReady: Boolean get() = ready && residents.resourceWorkersReady
    val resourceTask get() = fireState.resourcePhase
    fun castAtTap(hit: Entity?, pixels: Vector3, tool: FireTool): Boolean {
        if (!ready || disposed || tool == FireTool.OBSERVE || hit == null) return false
        val target = hit.logicalTarget(inputTargets) ?: if (fire.matches(hit)) "ground" else return false
        // SDK 6.2.8 does not apply the destination Entity transform here.
        // Convert pixels to view metres first, then explicitly invert the fitted hierarchy.
        val viewPoint = content.convertPosition(pixels, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val point = world.convertPositionFrom(viewPoint, placementOwner)
        Log.i("LocalInput", "tap target=$target pixels=$pixels world=$point source=spatial-gesture")
        return localEffects?.cast(tool, point, target.takeUnless { it == "ground" }) == true
    }

    /** Pointer ownership prevents a release from creating a second rain patch. */
    fun rainPointer(pointer: String, hit: Entity?, pixels: Vector3, pressed: Boolean, down: Boolean): Boolean {
        if (!ready || disposed) return false
        val target = hit?.logicalTarget(inputTargets) ?: if (fire.matches(hit)) "ground" else null
        val viewPoint = content.convertPosition(pixels, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val point = world.convertPositionFrom(viewPoint, placementOwner)
        val house = target?.takeUnless { it == "ground" }
        if (down && pressed && pointer !in rainPointers) {
            if (target == null) return false // Do not capture toolbar or background presses.
            val id = localEffects?.placeHeldRain(point, house=house) ?: return false
            rainPointers[pointer] = id
            Log.i("RainDrag", "begin pointer=$pointer rain=$id world=$point")
        }
        val id = rainPointers[pointer] ?: return false
        localEffects?.placeHeldRain(point, id, house)
        if (!pressed) {
            localEffects?.endHeldRain(id)
            rainPointers.remove(pointer)
            Log.i("RainDrag", "release pointer=$pointer rain=$id world=$point lifetime=15s")
        }
        return true
    }

    fun cancelRainPointers() {
        rainPointers.values.forEach { localEffects?.endHeldRain(it, cancelled=true) }
        if (rainPointers.isNotEmpty()) Log.i("RainDrag", "cancel pending=${rainPointers.size}")
        rainPointers.clear()
    }

    fun castAtHearth(tool: FireTool): Boolean = ready && localEffects?.cast(tool, fire.center) == true

    private fun auditLocalTargets(): Boolean {
        val scene = world.scene ?: return false
        val probes = listOf(Vector3(-.20f,.20f,.12f), Vector3(.20f,.20f,.12f)) +
            listOf("Shelter_0", "Shelter_1", "Shelter_2").map {
                val b = world.requireNode(it).getVisualBounds(world, recursive=true, enabledOnly=false)
                Vector3(b.center.x,.20f,b.center.z)
            }
        val targets = probes.map { origin ->
            val hits = scene.rayCast(origin, Vector3(0f,-1f,0f), .3f,
                com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL,
                com.pico.spatial.core.ecs.simulation.CollisionGroup(com.pico.spatial.core.ecs.simulation.CollisionGroup.COLLISION_GROUP_DEFAULT), world).results
            hits.minByOrNull { it.distance }?.entity?.logicalTarget(inputTargets)
        }
        val passed = targets.take(2).all { it == "ground" } && targets.drop(2) == listOf("Shelter_0","Shelter_1","Shelter_2")
        Log.i("LocalInput", "collisionAudit=${if(passed) "PASS" else "FAIL"} nearest=$targets source=programmatic userGesture=UNOBSERVED")
        return passed
    }

    private fun insideWindow(bounds: BoundingBox, half: Vector3) =
        BoundingBox(center = bounds.center - safeCenter, halfExtent = bounds.halfExtent)
            .insideHalfExtents(half, .001f)

    private fun countNativeModelOwners(entity: Entity): Int =
        (if (entity.getName() == "NativeModel") 1 else 0) + entity.getChildren().sumOf(::countNativeModelOwners)

    fun dispose() {
        if (disposed) return
        cancelRainPointers()
        disposed = true
        if (acceptanceInstance.get() === this) acceptanceInstance.clear()
        villageStore?.close()
        villageStore = null
        audio?.close()
        audio = null
        residents.dispose()
        fauna?.close()
        fauna = null
        placementOwner.destroy(true)
        villageView?.close()
        villageView = null
        fire.closeResources()
        woodAppearance?.close()
        woodAppearance = null
        timberGeometry?.close()
        timberGeometry = null
        localEffects?.close()
        localEffects = null
        appearance?.close()
        appearance = null
        groundAppearance?.close()
        groundAppearance = null
        groundDetail?.close()
        groundDetail = null
        livingTrail?.close()
        livingTrail = null
        treeBakedAppearance?.close()
        treeBakedAppearance = null
        propBakedAppearance?.close()
        propBakedAppearance = null
        loadedScenes.distinct().forEach { (bundle, scene) -> bundle.releaseModel(scene) }
        bundles.distinct().forEach { it.close() }
    }

    companion object {
        /** Return only a fully initialized runtime; all SDK mutations remain on main. */
        suspend fun create(
            bundles: List<AssetBundle>, loadedScenes: List<Pair<AssetBundle, String>>,
            model: Entity, loadedResidents: List<PrimitiveLoadedResident>,
            ground: PrimitiveGroundSurface, content: SpatialViewContent,
            attachments: SpatialViewAttachments, hiddenNodes: List<String>,
            fireCentroids: JSONObject, kitModel: Entity, campGround: PrimitiveTerrain,
            fieldModel: Entity, groundBitmap: android.graphics.Bitmap, naturalTree: Entity,
            animalModels: List<Entity>, saveDirectory: java.io.File? = null,
            awaitActive: suspend (String) -> Unit,
        ): PrimitivePreviewRuntime = PrimitivePreviewRuntime(bundles, loadedScenes, model, content).also {
            it.initialize(loadedResidents, ground, attachments, hiddenNodes, fireCentroids,
                kitModel, campGround, fieldModel, groundBitmap, naturalTree, animalModels, saveDirectory, awaitActive)
        }

        // Populated only by the separate acceptance package; never by the playable app.
        internal var acceptanceInstance = java.lang.ref.WeakReference<PrimitivePreviewRuntime>(null)
        const val SCROLL_PANEL = "village-chronicle-scroll"
        const val FOOTER_PANEL = "primitive-preview-footer"
        private const val TAG = "PrimitivePreview"
        private const val BOTTOM_MARGIN_METRES = .02f
        private const val BODY_GAP_METRES = .004f
        private const val FRONT_GAP_METRES = .002f
    }
}
