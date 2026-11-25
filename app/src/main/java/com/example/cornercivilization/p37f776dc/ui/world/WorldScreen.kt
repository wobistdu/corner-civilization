package com.example.cornercivilization.p37f776dc.ui.world

import androidx.compose.runtime.*
import android.content.Context
import android.content.ContextWrapper
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.cornercivilization.p37f776dc.scene.CivilizationScene
import com.example.cornercivilization.p37f776dc.scene.SceneEvolution
import com.example.cornercivilization.p37f776dc.ui.world.components.*
import com.pico.spatial.ui.platform.LocalSpatialContainerStateManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Container lifecycle controls growth; viewport only controls the battle clock. */
@Composable
fun WorldScreen() {
    val context = LocalContext.current
    val activity = remember(context) { context.requireViewModelStoreOwner() }
    val viewModel: WorldViewModel = viewModel(viewModelStoreOwner = activity, factory = remember(context) { WorldViewModelFactory(context) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    val manager = LocalSpatialContainerStateManager.current
    val sighted by manager.isSighted
    val focused by manager.isFocused
    val onstage by manager.isOnstage
    val latestSighted by rememberUpdatedState(sighted)
    val latestFocused by rememberUpdatedState(focused)
    val latestOnstage by rememberUpdatedState(onstage)
    DisposableEffect(owner, viewModel) {
        fun send(event: String) = viewModel.onEvent(WorldUiEvent.Platform(
            started = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED),
            sighted = latestSighted, focused = latestFocused, onstage = latestOnstage, event = event,
        ))
        val observer = LifecycleEventObserver { _, event -> send(event.name) }
        owner.lifecycle.addObserver(observer)
        send("COMPOSE_ATTACH")
        onDispose {
            owner.lifecycle.removeObserver(observer)
            viewModel.onEvent(WorldUiEvent.Platform(false, false, false, false, "COMPOSE_DISPOSE"))
        }
    }
    LaunchedEffect(sighted, focused, onstage) {
        viewModel.onEvent(WorldUiEvent.Platform(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED), sighted, focused, onstage, "SPATIAL_STATE"))
    }
    LaunchedEffect(viewModel) {
        while (isActive) { delay(250); viewModel.onEvent(WorldUiEvent.Pulse) }
    }
    WorldScreen(state, viewModel::onEvent)
}

/** Stateless surface: all rule and persistent state belongs to the ViewModel. */
@Composable
internal fun WorldScreen(state: WorldUiState, onEvent: (WorldUiEvent) -> Unit) {
    val world = state.world
    if (world != null) {
        CivilizationScene(
            state = world,
            onPlotTap = { onEvent(WorldUiEvent.PlotTap(it)) },
            onInvaderTap = { eventId, targetId -> onEvent(WorldUiEvent.InvaderTap(eventId, targetId)) },
            topContent = {},
            bottomContent = { WorldToolbar(state, onEvent) },
            modalContent = { WorldDialogs(state, onEvent) },
            selectedTool = state.selectedTool,
            motionActive = state.presentationMotionActive,
            battleMotionActive = state.battleActive,
            onAvailabilityChanged = { onEvent(WorldUiEvent.SceneAvailability(it)) },
            pendingEvolution = state.pendingEvolution?.let { SceneEvolution(it.id, it.fromEra, it.toEra) },
            onEvolutionPresented = { onEvent(WorldUiEvent.EvolutionPresented(it)) },
        )
    } else WorldDialogs(state, onEvent)
}

/**
 * DefaultWindowContainer loads its content into SpatialLaunchActivity. Its public
 * superclass chain is SpatialStubActivity -> FragmentActivity -> ComponentActivity
 * (ViewModelStoreOwner). LocalContext can wrap that activity for theming.
 */
private fun Context.requireViewModelStoreOwner(): ViewModelStoreOwner {
    var current = this
    while (true) {
        if (current is ViewModelStoreOwner) return current
        val base = (current as? ContextWrapper)?.baseContext
            ?: error("The spatial container Context has no ViewModelStoreOwner")
        check(base !== current) { "The spatial container ContextWrapper wraps itself" }
        current = base
    }
}
