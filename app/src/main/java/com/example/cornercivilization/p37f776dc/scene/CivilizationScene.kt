package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.example.cornercivilization.p37f776dc.domain.model.InterventionKind
import com.example.cornercivilization.p37f776dc.domain.model.WorldState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/** Loads the verified managed Editor package; the scene never mutates domain state. */
@Composable
fun CivilizationScene(
    state: WorldState,
    onPlotTap: (String) -> Unit,
    onInvaderTap: (String, String) -> Unit,
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    modalContent: @Composable () -> Unit,
    selectedTool: InterventionKind? = null,
    motionActive: Boolean = true,
    battleMotionActive: Boolean = true,
    onAvailabilityChanged: (Boolean) -> Unit = {},
    pendingEvolution: SceneEvolution? = null,
    onEvolutionPresented: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var runtime by remember { mutableStateOf<ManagedCivilizationRuntime?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf(false) }
    val availabilityCallback by rememberUpdatedState(onAvailabilityChanged)
    var measuredSize by remember { mutableStateOf(IntSize.Zero) }
    var footerSize by remember { mutableStateOf(IntSize.Zero) }
    val latestState by rememberUpdatedState(state)
    val latestTool by rememberUpdatedState(selectedTool)
    val latestEvolution by rememberUpdatedState(pendingEvolution)
    val evolutionCallback by rememberUpdatedState(onEvolutionPresented)
    val plotCallback by rememberUpdatedState(onPlotTap)
    val invaderCallback by rememberUpdatedState(onInvaderTap)

    LaunchedEffect(available) { availabilityCallback(available) }
    DisposableEffect(Unit) {
        onDispose { availabilityCallback(false); runtime?.dispose(); runtime = null }
    }
    LaunchedEffect(runtime, motionActive, battleMotionActive) {
        val scene = runtime ?: return@LaunchedEffect
        if (!motionActive && !battleMotionActive) return@LaunchedEffect
        var previous: Long? = null
        while (isActive) {
            withFrameNanos { frame ->
                val delta = previous?.let { (frame - it) / 1_000_000_000f } ?: 0f
                previous = frame
                scene.animate(delta, motionActive, battleMotionActive)
            }
        }
    }

    PicoTheme {
        Box {
            SpatialView(
                modifier = Modifier.fillMaxSize().onSizeChanged { measuredSize = it }.pointerInput(context) {
                    detectSpatialTapGesture(context, TargetEntity.any()) { tap ->
                        runtime?.tap(tap.targetEntity, plotCallback, invaderCallback)
                    }
                },
                attachments = {
                    AttachmentPanel(ManagedCivilizationRuntime.BOTTOM_PANEL) { SceneFooterPanel(bottomContent, onMeasured = { footerSize = it }) }
                    // AlertDialog owns its platform popup. Its empty attachment has
                    // intrinsic zero content size and adds no fullscreen hit plane.
                    AttachmentPanel(ManagedCivilizationRuntime.MODAL_PANEL) { modalContent() }
                },
                initial = { content, attachments ->
                    var bundle: AssetBundle? = null
                    var loaded: Entity? = null
                    try {
                        bundle = withContext(Dispatchers.IO) { AssetBundle.load(ManagedCivilizationRuntime.BUNDLE_URI) }
                        loaded = bundle.loadModelSuspend(ManagedCivilizationRuntime.SCENE_NAME)
                        val scene = ManagedCivilizationRuntime(bundle, loaded, content, attachments) { evolutionCallback(it) }
                        scene.apply(latestState, latestTool, latestEvolution)
                        runtime = scene
                        available = scene.ready
                        bundle = null; loaded = null // runtime owns both lifetimes now.
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        Log.e("CivilizationScene", "Managed scene could not be loaded", error)
                        available = false
                        failure = "三维家园暂时未能载入，请重新打开。原存档会保留。"
                    } finally {
                        loaded?.destroy(true)
                        bundle?.close()
                    }
                },
                update = { content, _ ->
                    runtime?.apply(state, selectedTool, pendingEvolution)
                    // Capturing measuredSize makes resize an explicit idempotent fit.
                    runtime?.let {
                        try {
                            available = it.refit(content, measuredSize, footerSize)
                        } catch (error: Exception) {
                            available = false
                            failure = "界面空间尺寸暂时无法确认，请重新打开。原存档会保留。"
                            Log.e("CivilizationScene", "Public attachment bounds probe failed; scene unavailable", error)
                        }
                    }
                },
            )
            failure?.let { Text(it, color = PicoTheme.colorScheme.labelPrimary, style = PicoTheme.typography.bodyMedium) }
        }
    }
}
