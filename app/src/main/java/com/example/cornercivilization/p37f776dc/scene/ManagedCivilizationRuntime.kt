package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import androidx.compose.ui.unit.IntSize
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialViewAttachments
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.PI

/** Owns presentation only. Rules, time accumulation and persistence stay in the UI actor. */
internal class ManagedCivilizationRuntime(
    private val bundle: AssetBundle,
    private val model: Entity,
    content: SpatialViewContent,
    attachments: SpatialViewAttachments,
    private val onEvolutionPresented: (String) -> Unit = {},
) {
    val placementOwner = Entity().apply { setName("placementOwner") }
    private val fittedSceneRoot = Entity().apply { setName("fittedSceneRoot") }
    private val toolbarOwner = Entity().apply { setName("RuntimeToolbarOwner") }
    private val world = model.requireNode("World")
    private val eras = Era.entries.associateWith { world.requireNode(eraNode(it)) }
    private val eraOpacity = eras.mapValues { (_, entity) -> OpacityControllerComponent(1f).also { entity.components.set(it) } }
    private val feedback = SceneFeedback()
    private val residents = List(6) { world.requireNode("Resident_$it") }
    private val residentBase = residents.map { it.transform().position }
    private val residentRotation = residents.map { it.transform().eulerAngles }
    private val plots = List(9) { world.requireNode("Plot_$it") }
    private val invader = world.requireNode("InvaderOwner")
    private val invaderBase = invader.transform().position
    private val invaderScale = invader.transform().scaleVector
    private val invaderRotation = invader.transform().eulerAngles
    private val invaderOpacity = OpacityControllerComponent(1f).also { invader.components.set(it) }
    private val buildings = (listOf("Tent", "Windmill", "House", "Factory", "Tower", "SpaceBuilding").flatMap { prefix ->
        (0 until if (prefix == "Windmill") 2 else 3).map { "${prefix}_$it" }
    } + listOf("EnergyTower_0")).map(world::requireNode)
    private val trafficGroup = world.requireNode("TrafficActors")
    private val traffic = List(8) { world.requireNode("Traffic_$it") }
    private val trafficBase = traffic.map(Entity::ownerPose)
    private val train = world.requireNode("Train_0")
    private val trainBase = train.ownerPose()
    private val cars = List(3) { world.requireNode("Car_$it") }
    private val carBase = cars.map(Entity::ownerPose)
    private lateinit var effects: SceneEffects
    private val buildingBase = buildings.associate { it.id to it.transform().position }
    private val buildingRotation = buildings.associate { it.id to it.transform().eulerAngles }
    private val buildingScale = buildings.associate { it.id to it.transform().scaleVector }
    private val targetOwners = mutableMapOf<Long, String>()
    private var current: WorldState? = null
    private var selected: InterventionKind? = null
    private var motionSeconds = 0f
    private var battleSeconds = 0f
    private var lastHits = 0
    private var hitPulse = 0f
    private var constructionPulse = 0f
    private var loggedShownEra: Era? = null
    private var civilizationId: String? = null
    private var disposed = false
    private var fitKey: String? = null
    private val panels = mutableMapOf<String, Entity>()
    var ready: Boolean = false
        private set
    private val bodyMotionBounds: BoundingBox
    private val islandBaseWidth: Float

    init {
        content.addEntity(placementOwner)
        placementOwner.addChild(fittedSceneRoot)
        placementOwner.addChild(toolbarOwner)
        fittedSceneRoot.addChild(model)
        verifyManagedOwners(world)
        val managedModels = world.modelRootCount()
        check(managedModels == 53) { "Full managed package must contain 53 Model roots, found $managedModels" }
        world.enableTree()
        world.requireNode("Effects").enabled = false
        plots.forEachIndexed { index, plot ->
            // Actual reviewed full-package grid: 110mm x 42mm spacing; target
            // covers the genuine floating marker within the grass support.
            plot.installBoxTarget(Vector3(.10f, .030f, .037f), Vector3(0f, .015f, 0f))
            plot.components.set(HoverEffectComponent())
            targetOwners[plot.id] = "plot-$index"
        }
        invader.installVisualBoundsTarget()
        invader.components.set(HoverEffectComponent())
        targetOwners[invader.id] = INVADER_TARGET
        attach(attachments, BOTTOM_PANEL, "ToolPanel")
        attach(attachments, MODAL_PANEL, "ModalPanel")
        effects = SceneEffects(world, plots)
        islandBaseWidth = world.requireNode("Terrain").getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false).size.x
        check(islandBaseWidth.isFinite() && islandBaseWidth > 0f)
        bodyMotionBounds = measureIndependentBodyEnvelope()
        Log.i(TAG, "body-motion boundsOwner=fittedSceneRoot excluding=UIAnchors bounds=$bodyMotionBounds")
        Log.i(TAG, "managed loaded scene=$SCENE_NAME runtimeOwners=71 managedModels=$managedModels nativeModels=${world.modelRootCount()} residents=6 plots=9 traffic=8 effectsClones=33 eras=5")
    }

    private fun attach(attachments: SpatialViewAttachments, id: String, anchor: String) {
        val panel = requireNotNull(attachments.entity(id)) { "Missing SpatialUI attachment $id" }
        val sourceAnchor = world.requireNode(anchor)
        if (id == BOTTOM_PANEL) toolbarOwner.addChild(panel) else sourceAnchor.addChild(panel)
        panels[id] = panel
    }

    /** Physical UI pixels are converted once and never inherit world scale. */
    fun refit(content: SpatialViewContent, size: IntSize, footerSize: IntSize): Boolean {
        if (disposed) return false
        val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val safe = Vector3(abs(corner.x), abs(corner.y), abs(corner.z))
        if (size.width <= 0 || size.height <= 0 || footerSize.width <= 0 || footerSize.height <= 0 || !safe.finitePositive()) {
            ready = false
            return false
        }
        val key = "$size|$footerSize|$safe"
        if (fitKey == key) return ready
        ready = false
        val nativeSize = content.convertSize(Vector3(footerSize.width.toFloat(), footerSize.height.toFloat(), 0f), ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
        val width = abs(nativeSize.x)
        val height = abs(nativeSize.y)
        if (!width.isFinite() || !height.isFinite() || !nativeSize.z.isFinite() || width <= 0f || height <= 0f) return false
        val corners = listOf(Vector3(-width / 2, -height / 2, 0f), Vector3(-width / 2, height / 2, 0f), Vector3(width / 2, -height / 2, 0f), Vector3(width / 2, height / 2, 0f))
        val panel = requireNotNull(panels[BOTTOM_PANEL])
        // Relative conversion cancels any previous toolbar owner transform.
        val uiBase = boundsOf(corners.map { panel.convertPositionTo(it, toolbarOwner) })
        if (!uiBase.size.x.isFinite() || uiBase.size.x <= 0f || !uiBase.size.y.isFinite() || uiBase.size.y <= 0f) return false
        val uiScale = minOf(1f, safe.x * 1.96f / uiBase.size.x)
        val uiHeight = uiBase.size.y * uiScale
        if (!fitManagedScene(content, placementOwner, fittedSceneRoot, bodyMotionBounds, uiHeight + PANEL_BODY_GAP_METERS)) return false
        val bodyFinal = boundsOf(listOf(bodyMotionBounds.min, bodyMotionBounds.max).map { fittedSceneRoot.convertPositionTo(it, placementOwner) })
        val target = Vector3(bodyFinal.center.x, bodyFinal.min.y - PANEL_BODY_GAP_METERS - uiHeight / 2f, bodyFinal.max.z + PANEL_FRONT_GAP_METERS)
        toolbarOwner.transform().apply {
            scaleVector = Vector3(uiScale)
            position = target - uiBase.center * uiScale
        }
        val panelFinal = boundsOf(corners.map { panel.convertPositionTo(it, placementOwner) })
        val belowGap = bodyFinal.min.y - panelFinal.max.y
        val frontGap = panelFinal.min.z - bodyFinal.max.z
        val combined = unionBounds(bodyFinal, panelFinal)
        val contained = combined.insideHalfExtents(safe, PANEL_EPSILON_METERS)
        check(belowGap + PANEL_EPSILON_METERS >= PANEL_BODY_GAP_METERS && frontGap + PANEL_EPSILON_METERS >= PANEL_FRONT_GAP_METERS) { "Independent toolbar separation failed: below=$belowGap front=$frontGap" }
        val worldScale = fittedSceneRoot.transform().scaleVector.x
        Log.i(TAG, "composition-layout owner=placementOwner worldScale=$worldScale uiScale=$uiScale pixels=$footerSize nativeMeters=$nativeSize uiBase=$uiBase bodyFinal=$bodyFinal panelFinal=$panelFinal combined=$combined safeHalf=$safe islandWidthMeters=${islandBaseWidth * worldScale} volumeWidthFraction=${islandBaseWidth * worldScale / (safe.x * 2)} gapFinalMeters=$belowGap frontGapFinalMeters=$frontGap contained=$contained visual=UNOBSERVED")
        Log.i(TAG, "panel-final id=$BOTTOM_PANEL boundsOwner=placementOwner bounds=$panelFinal safeHalf=$safe worldScale=$worldScale uiScale=$uiScale epsilon=$PANEL_EPSILON_METERS contained=${panelFinal.insideHalfExtents(safe, PANEL_EPSILON_METERS)} sizeKey=$key visual=UNOBSERVED")
        ready = contained
        if (contained) fitKey = key else Log.e(TAG, "Independent toolbar/body exceeds Volume; world scale preserved and scene unavailable")
        return ready
    }

    /** Native extrema of real geometry, independent of all attachment panels. */
    private fun measureIndependentBodyEnvelope(): BoundingBox {
        val groups = listOf("Terrain", "EraSettlement", "ResidentActors", "Invasion", "TrafficActors").map(world::requireNode)
        var envelope = groups.map { it.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false) }.filterNot { it.isEmpty() }.reduce(::unionBounds)
        var samples = 0
        fun include(entity: Entity) {
            val measured = entity.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false)
            check(!measured.isEmpty()) { "Motion owner has no genuine visual bounds: ${entity.getName()}" }
            envelope = unionBounds(envelope, measured)
            samples++
        }
        fun sweep(entity: Entity, operation: (OwnerPose) -> Unit) {
            val base = entity.ownerPose()
            try { operation(base) } finally { base.restore(entity) }
        }
        // Exact transformed source geometry at hit-pulse, wobble and victory extrema.
        var invaderEnvelope = invader.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false)
        sweep(invader) { base ->
            for (x in listOf(-.025f, .025f)) for (y in listOf(-.004f, .004f)) for (yaw in listOf(-8f, 0f, 8f)) for (roll in listOf(0f, 6f, 12f)) {
                invader.transform().apply { position = base.position + Vector3(x, y, 0f); scaleVector = base.scale * 1.084f; eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + yaw, base.rotation.roll + roll) }
                include(invader)
                invaderEnvelope = unionBounds(invaderEnvelope, invader.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false))
            }
            for (sample in 0..24) {
                val gone = sample / 24f
                invader.transform().apply { position = base.position + Vector3(0f, .008f * gone, 0f); scaleVector = base.scale * (1f - gone * .85f); eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + 90f * gone, base.rotation.roll + 20f * gone) }
                include(invader)
            }
        }
        residents.forEach { entity -> sweep(entity) { base ->
            // ResidentMotion bounds plus independent retreat, reset hop and victory.
            for (x in listOf(-.004f, .004f)) for (y in listOf(0f, .0083f)) for (z in listOf(-.026f, .0045f)) for (yaw in listOf(-15f, 0f, 15f)) {
                entity.transform().apply { position = base.position + Vector3(x, y, z); eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + yaw, base.rotation.roll) }
                include(entity)
            }
        } }
        buildings.forEach { entity -> sweep(entity) { base ->
            for (fire in listOf(0f, .5f, 1f)) for (growth in listOf(.92f, 1f)) for (height in listOf(0f, .0025f)) {
                entity.transform().apply { position = base.position + Vector3(0f, height, 0f); scaleVector = base.scale * ((1f - fire * .12f) * growth); eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw, base.rotation.roll + fire * 20f) }
                include(entity)
            }
        } }
        var trafficEnvelope: BoundingBox? = null
        traffic.forEach { entity -> sweep(entity) { base ->
            for (x in listOf(-.020f, .020f)) for (y in listOf(-.0035f, .0035f)) for (z in listOf(-.012f, .012f)) for (yaw in 0 until 360 step 15) {
                entity.transform().apply { position = base.position + Vector3(x, y, z); eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + yaw, base.rotation.roll) }
                include(entity)
                val bounds = entity.getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false)
                trafficEnvelope = trafficEnvelope?.let { unionBounds(it, bounds) } ?: bounds
            }
        } }
        (listOf(train) + cars).forEach { entity -> sweep(entity) { base ->
            val reach = if (entity == train) .075f else .018f
            for (x in listOf(-reach, reach)) for (yaw in listOf(0f, 180f)) {
                entity.transform().apply { position = base.position + Vector3(x, 0f, 0f); eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + yaw, base.rotation.roll) }
                include(entity)
            }
        } }
        envelope = unionBounds(envelope, effects.measureEnvelope(fittedSceneRoot))
        // Finite rotation-grid bounds need a conservative residual. At 15deg
        // heading intervals the taxi radius <.012m leaves <.00011m unsampled;
        // 0.5mm covers that and the denser pulse/rotation sweeps above.
        envelope = boundsOf(listOf(envelope.min - Vector3(.0005f), envelope.max + Vector3(.0005f)))
        val trafficBounds = requireNotNull(trafficEnvelope)
        val groundTop = world.requireNode("EraSettlement").getVisualBounds(fittedSceneRoot, recursive = true, enabledOnly = false).max.y + .0025f
        Log.i(TAG, "motion-envelope sourceModels=53 nativeClones=33 samples=$samples bounds=$envelope taxiMinY=${trafficBounds.min.y} taxiMaxY=${trafficBounds.max.y} groundMaxWithConstruction=$groundTop groundTaxiGap=${trafficBounds.min.y - groundTop} invaderMinY=${invaderEnvelope.min.y} invaderTaxiGap=${invaderEnvelope.min.y - trafficBounds.max.y} continuousAllowanceMeters=.0005")
        return envelope
    }

    fun apply(state: WorldState, selectedTool: InterventionKind?, pendingEvolution: SceneEvolution? = null) {
        if (disposed) return
        if (civilizationId != state.civilizationId) {
            civilizationId = state.civilizationId
            motionSeconds = 0f; battleSeconds = 0f; hitPulse = 0f
            constructionPulse = 0f
            lastHits = state.invasion?.hits ?: 0
        }
        if (current?.civilizationId == state.civilizationId && state.city.constructionProgress > (current?.city?.constructionProgress ?: 0)) constructionPulse = .6f
        feedback.accept(current, state, pendingEvolution)
        val invasion = state.invasion
        if (invasion != null && current?.invasion?.eventId != invasion.eventId) { battleSeconds = 0f; lastHits = invasion.hits; hitPulse = 0f }
        if (invasion != null && invasion.hits > lastHits) hitPulse = .24f
        lastHits = invasion?.hits ?: 0
        if (selected != selectedTool || current?.city?.plots != state.city.plots) Log.i(TAG, "plot-effects selected=$selectedTool plots=${state.city.plots.map { "${it.id}:water=${it.moisture},fire=${it.fire},resources=${it.resourceUnits}" }} markers=9 interaction=$selectedTool persistent=domain-snapshot")
        current = state
        selected = selectedTool
        val interactive = selectedTool != null && invasion == null
        plots.forEach {
            it.enabled = true // Persistent SGS effects remain visible after selection ends.
            if (interactive && !it.components.has<InteractableComponent>()) it.components.set(InteractableComponent())
            else if (!interactive) it.components.remove(InteractableComponent::class.java)
        }
        if (invasion?.phase == InvasionPhase.FIGHTING) invader.components.set(InteractableComponent())
        else invader.components.remove(InteractableComponent::class.java)
        animate(0f, motionActive = false, battleActive = false)
    }

    fun animate(deltaSeconds: Float, motionActive: Boolean, battleActive: Boolean) {
        if (disposed) return
        val state = current ?: return
        val elapsed = deltaSeconds.coerceIn(0f, .05f)
        if (motionActive) { motionSeconds += elapsed; constructionPulse = (constructionPulse - elapsed).coerceAtLeast(0f) }
        val completedEvolution = feedback.advance(elapsed, motionActive)
        if (battleActive) { battleSeconds += elapsed; hitPulse = (hitPulse - elapsed).coerceAtLeast(0f) }
        val eraChange = feedback.eraChange
        val duration = if (eraChange?.kind == SceneFeedback.Kind.SAFE_RESET) SceneFeedback.RESET_SECONDS else SceneFeedback.EVOLVE_SECONDS
        val progress = (eraChange?.seconds ?: duration) / duration
        val changingEra = eraChange?.let { if (progress < .5f) it.from else it.to }
        eras.forEach { (era, entity) ->
            entity.enabled = era == (changingEra ?: state.era)
            requireNotNull(eraOpacity[era]).opacity = if (eraChange == null) 1f else abs(progress * 2f - 1f).coerceAtLeast(.08f)
        }
        residents.forEachIndexed { index, entity ->
            val action = if (eraChange?.kind == SceneFeedback.Kind.EVOLVE) ResidentAction.BUILD else state.city.residents.getOrNull(index)?.action ?: ResidentAction.REST
            val base = residentBase[index]
            val pose = residentMotion(state.era, action, motionSeconds, index)
            val movement = Vector3(pose.offsetX, pose.offsetY, pose.offsetZ)
            val retreat = if (eraChange?.kind == SceneFeedback.Kind.SAFE_RESET) {
                // Walk toward safety, then return to the new primitive camp.
                // Every resident stays visible and alive throughout the handoff.
                val distance = if (progress < .5f) progress * 2f else (1f - progress) * 2f
                Vector3(0f, abs(sin(progress * PI.toFloat() * 4f + index)) * .0015f, -.018f * distance)
            } else Vector3.ZERO
            val celebrate = feedback.victorySeconds?.let { abs(sin(it * 15f + index)) * .0025f } ?: 0f
            entity.transform().apply {
                position = base + movement + retreat + Vector3(0f, celebrate, 0f)
                val rotation = residentRotation[index]
                eulerAngles = EulerAngles(rotation.pitch, rotation.yaw + pose.yawDegrees, rotation.roll)
            }
        }
        // Persistent fire is read from the domain snapshot. Residents remain
        // alive; buildings visibly lean and rebuild as the domain fire decays.
        buildings.forEach { entity ->
            val position = requireNotNull(buildingBase[entity.id])
            val nearest = plots.indices.minByOrNull { index ->
                val p = plots[index].transform().position
                val dx = p.x - position.x; val dz = p.z - position.z
                dx * dx + dz * dz
            } ?: 0
            val fire = (state.city.plots.getOrNull(nearest)?.fire ?: 0) / 100f
            val rotation = requireNotNull(buildingRotation[entity.id])
            entity.transform().apply {
                val built = state.city.constructionProgress.coerceIn(0, 20) / 20f
                val hop = if (constructionPulse > 0f) sin(constructionPulse / .6f * PI.toFloat()) * .0025f else 0f
                this.position = position + Vector3(0f, hop, 0f)
                eulerAngles = EulerAngles(rotation.pitch, rotation.yaw, rotation.roll + fire * 20f)
                scaleVector = requireNotNull(buildingScale[entity.id]) * ((1f - fire * .12f) * (.92f + built * .08f))
            }
        }
        val shownEra = changingEra ?: state.era
        world.requireNode("IndustrialTransport").enabled = shownEra == Era.INDUSTRIAL
        world.requireNode("ModernTransport").enabled = shownEra == Era.MODERN
        trafficGroup.enabled = shownEra == Era.SPACE
        if (loggedShownEra != shownEra) {
            loggedShownEra = shownEra
            Log.i(TAG, "era-visibility shown=$shownEra groups=${eras.filterValues { it.enabled }.keys} traffic=${trafficGroup.enabled} taxiModels=8 trainModels=1 carModels=3 sourceModelTransforms=preserved")
        }
        traffic.forEachIndexed { index, entity ->
            val base = trafficBase[index]
            val pose = airTrafficMotion(motionSeconds, index)
            entity.transform().apply {
                position = base.position + Vector3(pose.x, pose.y, pose.z)
                eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + pose.yaw, base.rotation.roll)
            }
        }
        animateGroundVehicle(train, trainBase, .075f, motionSeconds * .65f)
        cars.forEachIndexed { index, entity -> animateGroundVehicle(entity, carBase[index], .018f, motionSeconds + index * 2.1f) }
        val victory = feedback.victorySeconds
        world.requireNode("Invasion").enabled = state.invasion != null || victory != null
        invader.enabled = state.invasion != null || victory != null
        if (victory != null) {
            // The genuine imported saucer recoils and dissolves. No synthetic
            // fragment geometry or shared-material mutation is introduced.
            val gone = (victory / SceneFeedback.VICTORY_SECONDS).coerceIn(0f, 1f)
            invaderOpacity.opacity = 1f - gone
            invader.transform().apply {
                position = invaderBase + Vector3(0f, .008f * gone, 0f)
                scaleVector = invaderScale * (1f - gone * .85f)
                eulerAngles = EulerAngles(invaderRotation.pitch, invaderRotation.yaw + 90f * gone, invaderRotation.roll + 20f * gone)
            }
        } else {
            invaderOpacity.opacity = 1f
            if (state.invasion != null) invader.transform().apply {
                position = invaderBase + Vector3(sin(battleSeconds * .65f) * .025f, sin(battleSeconds * 1.4f) * .004f, 0f)
                val pulse = if (hitPulse > 0) 1f + hitPulse * .35f else 1f
                scaleVector = invaderScale * pulse
                eulerAngles = EulerAngles(invaderRotation.pitch, invaderRotation.yaw + sin(battleSeconds) * 8f, invaderRotation.roll + if (hitPulse > 0) 12f else 0f)
            }
        }
        effects.update(state, selected, motionSeconds, residents, victory, effects.carrierRoot.convertPositionFrom(invaderBase, requireNotNull(invader.getParent())))
        completedEvolution?.let {
            Log.i(TAG, "evolution-presented id=$it")
            onEvolutionPresented(it)
        }
    }

    private fun animateGroundVehicle(entity: Entity, base: OwnerPose, reach: Float, phase: Float) {
        val pose = groundTransportMotion(reach, phase)
        entity.transform().apply {
            position = base.position + Vector3(pose.x, pose.y, pose.z)
            eulerAngles = EulerAngles(base.rotation.pitch, base.rotation.yaw + pose.yaw, base.rotation.roll)
        }
    }

    fun tap(hit: Entity?, onPlotTap: (String) -> Unit, onInvaderTap: (String, String) -> Unit) {
        if (disposed) return
        if (hit == null) { Log.i(TAG, "tap rejected reason=no-target"); return }
        val state = current ?: return
        val logical = hit.logicalTarget(targetOwners)
        when {
            logical == INVADER_TARGET && state.invasion?.phase == InvasionPhase.FIGHTING -> {
                val event = requireNotNull(state.invasion)
                Log.i(TAG, "tap entity=${hit.id} logical=invader event=${event.eventId} target=${event.targetId} hits=${event.hits}")
                onInvaderTap(event.eventId, event.targetId)
            }
            logical?.startsWith("plot-") == true && selected != null && state.invasion == null -> {
                Log.i(TAG, "tap entity=${hit.id} logical=$logical tool=$selected")
                onPlotTap(logical)
            }
            else -> Log.i(TAG, "tap rejected entity=${hit.id} logical=$logical invasion=${state.invasion?.phase}")
        }
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        placementOwner.destroy(true)
        bundle.releaseModel(SCENE_NAME)
        bundle.close()
        targetOwners.clear()
    }

    companion object {
        const val BUNDLE_URI = "asset://managed/CornerCivilizationScene.bundle"
        // Exact scene identifier from verified bundle AssetInfo index (scene id 69).
        // The current Editor 6.3 exporter retains Scenes/, unlike the 6.2 doc example.
        const val SCENE_NAME = "Scenes/CornerCivilization"
        const val BOTTOM_PANEL = "civilization-tools"
        const val MODAL_PANEL = "civilization-modal"
        private const val INVADER_TARGET = "current-invader"
        private const val TAG = "CivilizationScene"
        private const val PANEL_EPSILON_METERS = .001f
        private const val PANEL_BODY_GAP_METERS = .01f
        private const val PANEL_FRONT_GAP_METERS = .002f
        private fun eraNode(era: Era) = when (era) { Era.PRIMITIVE -> "Primitive"; Era.AGRICULTURAL -> "Agricultural"; Era.INDUSTRIAL -> "Industrial"; Era.MODERN -> "Modern"; Era.SPACE -> "Space" }
    }
}
