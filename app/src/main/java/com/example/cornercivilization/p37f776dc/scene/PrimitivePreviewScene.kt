package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.ui.input.pointer.pointerInput
import com.example.cornercivilization.p37f776dc.ui.fire.*
import com.example.cornercivilization.p37f776dc.ui.fire.components.VillageScroll
import com.example.cornercivilization.p37f776dc.domain.usecase.VillageChronicle
import com.example.cornercivilization.p37f776dc.ui.fire.components.FireSliceToolbar
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialPointerEvent
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.withStateAtLeast
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.ButtonDefaults
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONObject

/** First fire playtest; isolated slice save, no old daily rules or era progression. */
@Composable
internal fun PrimitivePreviewScene(windowSizePx: IntSize, ui: FireSliceUiState, onEvent: (FireSliceEvent) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val manager = LocalSpatialContainerStateManager.current
    val onstage by manager.isOnstage
    val sighted by manager.isSighted
    val focused by manager.isFocused
    var started by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val latestUi by rememberUpdatedState(ui)
    var runtime by remember { mutableStateOf<PrimitivePreviewRuntime?>(null) }
    val scrollOpen = runtime?.scrollOpen == true
    var scrollLines by remember { mutableStateOf(emptyList<VillageChronicle.Line>()) }
    LaunchedEffect(runtime, scrollOpen, started) {
        if (!scrollOpen || !started) return@LaunchedEffect
        val journal = runtime?.chronicle ?: return@LaunchedEffect
        var revision = -1L
        while (isActive) {
            if (revision != journal.revision) {
                scrollLines = journal.snapshot()
                revision = journal.revision
            }
            delay(160)
        }
    }
    var failure by remember { mutableStateOf<String?>(null) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var footerSize by remember { mutableStateOf(IntSize.Zero) }
    // SDK WindowContainer ON_STOP covers minimize/close. Onstage and sighted
    // describe occlusion/view direction and must not stop the living world.
    val active = started && failure == null && ui.error == null && ui.world != null
    LaunchedEffect(runtime, active, windowSizePx, footerSize) {
        onEvent(FireSliceEvent.Platform(active && runtime?.ready == true, SystemClock.elapsedRealtime()))
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(100)
            onEvent(FireSliceEvent.Pulse(SystemClock.elapsedRealtime(), runtime?.fireParticipantsReady == true, runtime?.shelterWorkersReady == true, runtime?.shelterTask, runtime?.resourceWorkersReady == true, runtime?.resourceTask, runtime?.shelterOperational != false, runtime?.fieldEstablished == true))
        }
    }

    LaunchedEffect(ui.firstToolHint) {
        ui.firstToolHint?.let { tool -> delay(6_000);onEvent(FireSliceEvent.DismissHint(tool)) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            started = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            Log.i("VillageLifecycle", "event=$event started=$started")
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(Unit) {
        onDispose { onEvent(FireSliceEvent.Platform(false, SystemClock.elapsedRealtime())); runtime?.dispose(); runtime = null }
    }
    LaunchedEffect(active, started, onstage, sighted, focused) {
        Log.i("VillageLifecycle", "active=$active started=$started onstage=$onstage sighted=$sighted focused=$focused")
    }
    LaunchedEffect(runtime, started) {
        val scene = runtime ?: return@LaunchedEffect
        if (!started) return@LaunchedEffect
        while (isActive) {
            try { scene.refreshLayout() }
            catch (error: Exception) {
                failure = "场景空间尺寸暂时无法确认，请重新打开。"
                Log.e("PrimitivePreview", "Deferred Volume fit failed", error)
            }
            if (failure != null) break
            // Native size/coordinate propagation may arrive after Compose's
            // size callback. One bounded retry loop replaces two per-frame fits.
            delay(100)
        }
    }
    LaunchedEffect(runtime, active) {
        val scene = runtime ?: return@LaunchedEffect
        scene.animate(0f, active)
        onEvent(FireSliceEvent.Platform(active && scene.ready, SystemClock.elapsedRealtime()))
        Log.i("PrimitivePreview", "motion active=$active started=$started onstage=$onstage")
        if (!active) return@LaunchedEffect
        var previousFrame: Long? = null
        var previousReady = scene.ready
        while (isActive) {
            val frame = withFrameNanos { it }
            if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) break
            val now = SystemClock.elapsedRealtime()
            val delta = previousFrame?.let { ((frame-it)/1_000_000_000f).coerceIn(0f,.1f) }?:0f
            previousFrame = frame
            try {
                latestUi.world?.let(scene::applyFire)
                if (scene.ready != previousReady) {
                    previousReady = scene.ready
                    onEvent(FireSliceEvent.Platform(active && scene.ready, now))
                }
                scene.animate(delta, true)
            } catch (error: Exception) {
                scene.animate(0f, false)
                failure = "场景暂时无法继续显示，请重新打开。"
                Log.e("PrimitivePreview", "Animated scene validation failed", error)
            }
            if (failure != null) break
        }
    }

    PicoTheme {
        Box {
            SpatialView(
                modifier = Modifier.fillMaxSize().onSizeChanged { viewSize = it }.pointerInput(context, ui.tool, active) {
                    if (ui.tool == FireTool.WATER) {
                        try {
                            detectSpatialPointerEvent(context = context) { events ->
                                var consumed = false
                                if (active) events.forEach { event ->
                                    consumed = runtime?.rainPointer(event.pointerId.toString(), event.targetedEntity,
                                        Vector3(event.x, event.y, event.z), event.pressed, event.isDownEvent()) == true || consumed
                                }
                                consumed
                            }
                        } finally { runtime?.cancelRainPointers() }
                    } else {
                        detectSpatialTapGesture(context, TargetEntity.any()) { tap ->
                            if (active) runtime?.castAtTap(tap.targetEntity, Vector3(tap.position.x, tap.position.y, tap.position.z), latestUi.tool)
                        }
                    }
                },
                attachments = {
                    AttachmentPanel(PrimitivePreviewRuntime.SCROLL_PANEL) {
                        VillageScroll(scrollLines, scrollOpen)
                    }
                    AttachmentPanel(PrimitivePreviewRuntime.FOOTER_PANEL) {
                        SceneFooterPanel(onMeasured = { footerSize = it }, content = {
                            FireSliceToolbar(ui, active && runtime?.ready == true, scrollOpen, { runtime?.toggleScroll() }, { runtime?.castAtHearth(latestUi.tool) }) { onEvent(FireSliceEvent.Select(it)) }
                        })
                    }
                },
                initial = { content, attachments ->
                    val bundles = mutableListOf<AssetBundle>()
                    val loadedScenes = mutableListOf<Pair<AssetBundle, String>>()
                    val residents = mutableListOf<PrimitiveLoadedResident>()
                    val animalModels = mutableListOf<Entity>()
                    var model: Entity? = null
                    var kit: Entity? = null
                    var groundBitmap: Bitmap? = null
                    var consumedByRuntime = false
                    // Asset IO may complete while the container is minimized.
                    // Native enabled bounds are only usable after it starts again.
                    val awaitActive: suspend (String) -> Unit = { stage ->
                        val lifecycle = lifecycleOwner.lifecycle
                        val waiting = !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                        if (waiting) Log.i("VillageStartup", "waiting=$stage state=${lifecycle.currentState}")
                        do {
                            lifecycle.withStateAtLeast(Lifecycle.State.STARTED) {}
                            // Let native container enablement propagate, then recheck:
                            // ON_STOP may have arrived during this suspension.
                            delay(16)
                        } while (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                        if (waiting) Log.i("VillageStartup", "resumed=$stage state=${lifecycle.currentState}")
                    }
                    try {
                        val contract = withContext(Dispatchers.IO) {
                            PrimitivePreviewContract.fromJson(JSONObject(context.assets.open(PrimitivePreviewContract.MANIFEST_ASSET).bufferedReader().use { it.readText() }))
                        }
                        val terrain = withContext(Dispatchers.IO) {
                            PrimitiveTerrain.fromJson(JSONObject(context.assets.open(contract.heightfieldAsset).bufferedReader().use { it.readText() }))
                        }
                        val campTerrain = withContext(Dispatchers.IO) {
                            PrimitiveTerrain.fromJson(JSONObject(context.assets.open("managed/primitive-camp-support.json").bufferedReader().use { it.readText() }))
                        }
                        val bundle = withContext(Dispatchers.IO) { AssetBundle.load(contract.bundleUri) }.also { bundles += it }
                        val residentBundle = if (contract.residentBundleUri == contract.bundleUri) bundle else {
                            withContext(Dispatchers.IO) { AssetBundle.load(contract.residentBundleUri) }.also { bundles += it }
                        }
                        model = bundle.loadModelSuspend(contract.sceneName)
                        loadedScenes += bundle to contract.sceneName
                        val terrainV5 = Entity.loadSuspend("asset://gameplay/terrain-v5.glb")
                        terrainV5.setName("TerrainV5")
                        requireNotNull(model).requireNode("TerrainSurface").addChild(terrainV5)
                        val faunaBundle = withContext(Dispatchers.IO) { AssetBundle.load("asset://managed/PrimitiveFaunaV5.bundle") }.also { bundles += it }
                        val fauna = faunaBundle.loadModelSuspend("Scenes/Hi")
                        loadedScenes += faunaBundle to "Scenes/Hi"
                        fauna.setName("FaunaLibrary")
                        requireNotNull(model).requireNode("PrimitiveWorld").addChild(fauna)
                        val kitBundle = withContext(Dispatchers.IO) { AssetBundle.load("asset://managed/GodSandboxAssetsV3.bundle") }.also { bundles += it }
                        kit = kitBundle.loadModelSuspend("Scenes/GodSandboxAssetsV3")
                        loadedScenes += kitBundle to "Scenes/GodSandboxAssetsV3"
                        val prehistoric=withContext(Dispatchers.IO) {
                            JSONObject(context.assets.open("gameplay/prehistoric-v17-needs/manifest.json").bufferedReader().use{it.readText()})
                        }
                        val bodies=listOf("gatherer","HideElder","carrier","gatherer","HideGatherer","carrier","scout","HideGatherer","scout")
                        val heights=listOf(.94f,1.02f,.98f,1.02f,1f,1.07f,.96f,.92f,1.04f)
                        // Loading independent instances may overlap; their models
                        // and skeletons remain distinct and scene mutations stay on
                        // this main coroutine context. The cleanup list owns every
                        // completed load even when a sibling fails.
                        coroutineScope { contract.residents.map { resident -> async {
                            val number=resident.id.substringAfterLast('_').toInt()
                            val variant=bodies[number]
                            run {
                                requireNotNull(model).requireNode(requireNotNull(resident.sourceOwnerName)).enabled = false
                                val actor = Entity.loadSuspend("asset://gameplay/prehistoric-v17-needs/$variant.glb")
                                val metrics=prehistoric.getJSONObject(variant)
                                val height=resident.asset.targetHeightMetres*heights[number]
                                val motionScale=height/metrics.getDouble("sourceHeight").toFloat()
                                val walk=metrics.getDouble("walkMetresPerSecond").toFloat()*motionScale
                                val carry=metrics.getDouble("carryMetresPerSecond").toFloat()*motionScale
                                val tracks=metrics.getJSONObject("propTracks")
                                val asset = resident.asset.copy(
                                    sourceFootAnchor=Vector3.ZERO,sourceForwardYawDegrees=0f,targetHeightMetres=height,
                                    clipNames = PrimitiveActivity.entries.associateWith { it.clip }, clipRanges = emptyMap(),
                                    gripTracks=listOf(PrimitiveActivity.EAT,PrimitiveActivity.DRINK).associateWith { activity->
                                        val track=tracks.getJSONObject(activity.clip)
                                        val samples=track.getJSONArray("samples")
                                        PrimitiveGripTrack(track.getDouble("seconds").toFloat(),(0 until samples.length()).map { i->
                                            val sample=samples.getJSONArray(i)
                                            Vector3(sample.getDouble(0).toFloat(),sample.getDouble(1).toFloat(),sample.getDouble(2).toFloat()) to sample.getDouble(3).toFloat()
                                        })
                                    },
                                    authoredMotionMetresPerSecond = mapOf(PrimitiveActivity.WALK to walk,PrimitiveActivity.CARRY to carry,PrimitiveActivity.CARRY_WOOD to carry))
                                residents += PrimitiveLoadedResident(resident.id, actor, asset, resident.route)
                                Log.i("ResidentVariety","id=${resident.id} body=$variant height=$height triangles=${metrics.getInt("triangles")} skeleton=independent")
                            }
                        } }.awaitAll() }
                        residents.sortBy { it.id.substringAfterLast('_').toInt() }
                        val orderedAnimals=coroutineScope { primitiveFaunaPlacements.map { p -> async {
                            val animal = Entity.loadSuspend("asset://gameplay/${p.species}-animated.glb")
                            animalModels += animal
                            requireNotNull(model).requireNode("PrimitiveWorld").addChild(animal)
                            animal
                        } }.awaitAll() }
                        animalModels.clear();animalModels.addAll(orderedAnimals)
                        val fireCentroids = withContext(Dispatchers.IO) {
                            JSONObject(context.assets.open("managed/fire-flame-centroids.json").bufferedReader().use { it.readText() })
                        }
                        val field = Entity.loadSuspend("asset://gameplay/field.glb")
                        requireNotNull(model).requireNode("PrimitiveWorld").addChild(field)
                        val naturalTree = Entity.loadSuspend("asset://sgs/d07b6a14aed7dec9e0a9883aad3f766d1ad9459a7df479fd156e1441805f0ca2.glb")
                        requireNotNull(model).requireNode("PrimitiveWorld").addChild(naturalTree)
                        val waterPrototype=PrehistoricSourcesV17.loadWaterVessel().apply{setName("PrehistoricWaterPrototype")}
                        requireNotNull(model).requireNode("PrimitiveWorld").addChild(waterPrototype)
                        groundBitmap = withContext(Dispatchers.IO) { PrimitiveGroundAppearance.prepare(context) }
                        awaitActive("assets-loaded")
                        consumedByRuntime = true
                        runtime = PrimitivePreviewRuntime.create(bundles, loadedScenes, requireNotNull(model), residents, terrain, content, attachments, contract.hiddenNodes + contract.residents.mapNotNull{it.sourceOwnerName} + "PrehistoricWaterPrototype", fireCentroids, requireNotNull(kit), campTerrain, field, requireNotNull(groundBitmap), naturalTree, animalModels, context.filesDir, awaitActive)
                        if (context.packageName.endsWith(".acceptance")) PrimitivePreviewRuntime.acceptanceInstance = java.lang.ref.WeakReference(runtime)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        failure = "场景暂时未能载入，请重新打开。"
                        Log.e("PrimitivePreview", "New managed primitive scene could not load", error)
                    } finally {
                        groundBitmap?.let { if (!it.isRecycled) it.recycle() }
                        if (!consumedByRuntime) {
                            residents.filterNot { it.ownsImportedPlacement }.forEach { it.model.destroy(true) }
                            kit?.destroy(true)
                            model?.destroy(true)
                            loadedScenes.distinct().forEach { (bundle, scene) -> bundle.releaseModel(scene) }
                            bundles.distinct().forEach { it.close() }
                        }
                    }
                },
                update = { content, _ ->
                    try {
                        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
                            runtime?.refit(content, viewSize, footerSize, windowSizePx)
                    }
                    catch (error: Exception) {
                        runtime?.animate(0f, false)
                        failure = "场景空间尺寸暂时无法确认，请重新打开。"
                        Log.e("PrimitivePreview", "Primitive Volume fit failed", error)
                    }
                },
            )
            (failure ?: ui.error)?.let { Text(it, color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.bodyMedium) }
        }
    }
}
