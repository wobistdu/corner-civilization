package com.example.cornercivilization.p37f776dc.ui.world

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldEngine
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class WorldViewModel internal constructor(
    private val store: WorldStore,
    private val engine: WorldEngine = WorldEngine(),
    private val uptime: () -> Long = SystemClock::uptimeMillis,
    private val now: () -> Instant = Instant::now,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val trace: (String) -> Unit = { Log.i("CivilizationClock", it) },
) : ViewModel() {
    private val mutableState = MutableStateFlow(WorldUiState())
    val state: StateFlow<WorldUiState> = mutableState.asStateFlow()
    private data class Input(val event: WorldUiEvent, val uptime: Long, val wall: Instant)
    private val inputs = Channel<Input>(Channel.UNLIMITED)
    private val clock = WorldClock()
    private var started = false
    private var everStarted = false
    private var manuallyPaused = false
    private var helpReturnPresentation = WorldPresentation.OBSERVING
    private var pauseReturnPresentation = WorldPresentation.OBSERVING
    private var detailsReturnPresentation = WorldPresentation.OBSERVING
    private var recoveryPresentation: WorldPresentation? = null
    private var deferredOpenDate: LocalDate? = null
    private var pending: Transition? = null
    private var dirty = false
    private var lastSave = 0L
    private var sequence = 0L
    private val sessionId = java.util.UUID.randomUUID().toString()

    private val workerScope = CoroutineScope(SupervisorJob() + dispatcher)
    private var readyUptime = 0L

    init {
        workerScope.launch {
            load()
            for (input in inputs) handle(input)
            checkpoint(uptime(), force = true)
            workerScope.cancel()
        }
    }

    fun onEvent(event: WorldUiEvent) { inputs.trySend(Input(event, uptime(), now())) }

    private fun today() = now().atZone(ZoneId.systemDefault()).toLocalDate()
    private fun update(block: (WorldUiState) -> WorldUiState) { mutableState.value = block(mutableState.value) }
    private fun defaultPresentation(world: WorldState) = when (world.invasion?.phase) {
        InvasionPhase.WARNING -> WorldPresentation.WARNING
        InvasionPhase.FIGHTING -> WorldPresentation.BATTLE
        null -> if (world.qualification != null) WorldPresentation.QUALIFIED else WorldPresentation.OBSERVING
    }

    private suspend fun load() {
        recoveryPresentation = null
        when (val loaded = store.load()) {
            is WorldLoad.Loaded -> {
                sequence = maxOf(loaded.state.city.lastInterventionSequence, loaded.state.invasion?.lastAcceptedInputSequence ?: -1) + 1
                update { it.copy(world = loaded.state, presentation = defaultPresentation(loaded.state), error = null, saveFailed = false) }
                apply(WorldCommand.Open(today(), sessionId))
            }
            WorldLoad.Empty -> {
                val fresh = WorldFactory.create(today())
                val error = store.save(fresh)
                if (error == null) update { it.copy(world = fresh, presentation = WorldPresentation.FIRST_OPEN, error = null) }
                else update { it.copy(error = error, presentation = WorldPresentation.RECOVERY_ERROR) }
            }
            is WorldLoad.Failed -> update { it.copy(error = loaded.reason, presentation = WorldPresentation.RECOVERY_ERROR, canRebuild = loaded.canRebuild) }
        }
        readyUptime = uptime()
        clock.rebase(readyUptime, now())
        refreshGates()
    }

    private suspend fun handle(queued: Input) {
        // Inputs arriving while load performs IO cannot backdate the active clock.
        val input = if (queued.uptime < readyUptime) queued.copy(uptime = readyUptime, wall = queued.wall.plusMillis(readyUptime - queued.uptime)) else queued
        val before = state.value
        if (before.world != null && before.error == null) {
            for (slice in clock.settle(input.uptime, input.wall, before.growthActive, before.battleActive)) {
                if (!apply(WorldCommand.Tick(slice))) break
            }
        } else clock.rebase(input.uptime, input.wall)
        val settledCriticalPresentation = state.value.presentation != before.presentation &&
            state.value.presentation in setOf(WorldPresentation.EVOLVING, WorldPresentation.VICTORY, WorldPresentation.RESET)
        when (val event = input.event) {
            is WorldUiEvent.SceneAvailability -> {
                if (event.available != state.value.sceneAvailable) trace("SCENE_AVAILABLE=${event.available}")
                update { it.copy(sceneAvailable = event.available) }
            }
            is WorldUiEvent.Platform -> {
                val wasStarted = started
                started = event.started
                update { it.copy(isSighted = event.sighted) }
                trace("${event.event} started=${event.started} sighted=${event.sighted} focused=${event.focused} onstage=${event.onstage}")
                if (!wasStarted && started && everStarted && state.value.world != null) {
                    val openedOn = input.wall.atZone(ZoneId.systemDefault()).toLocalDate()
                    if (state.value.error == null) apply(WorldCommand.Open(openedOn, sessionId))
                    else deferredOpenDate = deferredOpenDate?.let { maxOf(it, openedOn) } ?: openedOn
                    clock.rebase(input.uptime, input.wall)
                }
                if (started) everStarted = true
                if (!started) checkpoint(input.uptime, force = true)
            }
            WorldUiEvent.Pulse -> checkpoint(input.uptime)
            WorldUiEvent.Start -> update { it.copy(presentation = it.world?.let(::defaultPresentation) ?: WorldPresentation.LOADING) }
            WorldUiEvent.Pause -> if (state.value.error == null && state.value.presentation != WorldPresentation.PAUSED && !settledCriticalPresentation) {
                pauseReturnPresentation = state.value.presentation
                manuallyPaused = true
                update { it.copy(presentation = WorldPresentation.PAUSED) }
                checkpoint(input.uptime, force = true)
            }
            WorldUiEvent.Resume -> if (state.value.presentation == WorldPresentation.PAUSED) {
                manuallyPaused = false
                update { it.copy(presentation = restoredPresentation(pauseReturnPresentation)) }
            }
            WorldUiEvent.Help -> if (state.value.error == null && state.value.presentation != WorldPresentation.HELP && !settledCriticalPresentation) {
                helpReturnPresentation = state.value.presentation
                update { it.copy(presentation = WorldPresentation.HELP) }
            }
            WorldUiEvent.CloseHelp -> if (state.value.presentation == WorldPresentation.HELP) {
                update { it.copy(presentation = restoredPresentation(helpReturnPresentation)) }
            }
            WorldUiEvent.Details -> if (state.value.error == null && !settledCriticalPresentation && state.value.presentation in setOf(
                WorldPresentation.OBSERVING, WorldPresentation.SELECTING, WorldPresentation.INTERVENTION_FEEDBACK,
                WorldPresentation.QUALIFIED, WorldPresentation.EVOLVING, WorldPresentation.WARNING,
                WorldPresentation.BATTLE, WorldPresentation.VICTORY, WorldPresentation.RESET,
            )) {
                detailsReturnPresentation = state.value.presentation
                update { it.copy(presentation = WorldPresentation.DETAILS, detailsPresentation = detailsReturnPresentation) }
            }
            WorldUiEvent.CloseDetails -> if (state.value.presentation == WorldPresentation.DETAILS) {
                update { it.copy(presentation = restoredPresentation(detailsReturnPresentation)) }
            }
            WorldUiEvent.Acknowledge, WorldUiEvent.CancelTool -> update { it.copy(selectedTool = null, presentation = it.world?.let(::defaultPresentation) ?: it.presentation) }
            is WorldUiEvent.SelectTool -> if (state.value.world?.invasion == null && state.value.growthActive) {
                update { it.copy(selectedTool = event.kind, presentation = WorldPresentation.SELECTING) }
            }
            is WorldUiEvent.PlotTap -> {
                val kind = state.value.selectedTool
                if (kind != null && state.value.presentation == WorldPresentation.SELECTING && state.value.growthActive) {
                    apply(WorldCommand.Intervene(kind, event.plotId, sequence++))
                }
            }
            is WorldUiEvent.InvaderTap -> if (state.value.error == null) {
                apply(WorldCommand.TargetHit(event.eventId, event.targetId, sequence++, state.value.growthActive, state.value.battleActive))
            }
            is WorldUiEvent.EvolutionPresented -> if (state.value.pendingEvolution?.id == event.id) {
                update { it.copy(pendingEvolution = null) }
            }
            WorldUiEvent.Retry -> retry()
            WorldUiEvent.AskReset -> if (state.value.canRebuild && state.value.world?.invasion == null) update { it.copy(presentation = WorldPresentation.RESTORE_CONFIRM) }
            WorldUiEvent.CancelReset -> update { it.copy(presentation = WorldPresentation.RECOVERY_ERROR) }
            WorldUiEvent.ConfirmReset -> if (state.value.canRebuild && state.value.world?.invasion == null) {
                update { it.copy(error = null) }
                if (state.value.world != null) apply(WorldCommand.Reset(today(), ResetReason.RECOVERY_NEW_GAME))
                else {
                    val fresh = WorldFactory.create(today(), lineageId = java.util.UUID.randomUUID().toString())
                    val error = store.save(fresh)
                    if (error == null) update { it.copy(world = fresh, presentation = WorldPresentation.RESET, error = null, canRebuild = false) }
                    else fail(error, false)
                }
            }
        }
        refreshGates()
    }

    private fun restoredPresentation(returnTo: WorldPresentation): WorldPresentation {
        if (returnTo in setOf(WorldPresentation.DETAILS, WorldPresentation.HELP, WorldPresentation.PAUSED)) return returnTo
        val world = state.value.world ?: return returnTo
        return if (world.invasion != null || returnTo in setOf(WorldPresentation.OBSERVING, WorldPresentation.QUALIFIED)) defaultPresentation(world) else returnTo
    }

    private suspend fun apply(command: WorldCommand): Boolean {
        if (state.value.error != null) return false
        val old = state.value.world ?: return false
        val transition = engine.dispatch(old, command)
        if (transition.persistence == Persistence.CRITICAL) {
            val error = store.save(transition.state)
            if (error != null) {
                pending = transition
                fail(error, canRebuild = false)
                return false
            }
            dirty = false
            lastSave = uptime()
        } else if (transition.state != old) dirty = true
        publish(transition)
        return true
    }

    private fun publish(transition: Transition, presentationContext: WorldPresentation = state.value.presentation) {
        val oldUi = state.value
        var presentation = if (presentationContext in setOf(WorldPresentation.HELP, WorldPresentation.DETAILS, WorldPresentation.PAUSED, WorldPresentation.FIRST_OPEN)) presentationContext else when {
            transition.state.invasion != null -> defaultPresentation(transition.state)
            presentationContext in setOf(WorldPresentation.SELECTING, WorldPresentation.INTERVENTION_FEEDBACK, WorldPresentation.EVOLVING, WorldPresentation.VICTORY, WorldPresentation.RESET) -> presentationContext
            else -> defaultPresentation(transition.state)
        }
        var effect = oldUi.lastEffect
        var evolution = oldUi.pendingEvolution
        for (event in transition.events) when (event.type) {
            WorldEventType.ERA_ADVANCED -> {
                presentation = WorldPresentation.EVOLVING
                val from = event.fromEra
                val to = event.toEra
                if (from != null && to != null) {
                    evolution = PendingEvolution("${event.civilizationId}:${event.revision}:${from.name}:${to.name}", from, to)
                }
            }
            WorldEventType.INVASION_DEFEATED -> presentation = WorldPresentation.VICTORY
            WorldEventType.CIVILIZATION_RESET -> { presentation = WorldPresentation.RESET; evolution = null }
            WorldEventType.INTERVENTION_APPLIED -> { presentation = WorldPresentation.INTERVENTION_FEEDBACK; effect = event.intervention }
            WorldEventType.INVASION_STARTED, WorldEventType.BATTLE_STARTED -> presentation = if (presentationContext in setOf(WorldPresentation.HELP, WorldPresentation.DETAILS)) presentationContext else defaultPresentation(transition.state)
            else -> Unit
        }
        val readingDetails = presentation == WorldPresentation.DETAILS ||
            (presentation == WorldPresentation.HELP && helpReturnPresentation == WorldPresentation.DETAILS)
        val detailsPresentation = when {
            readingDetails && transition.state.invasion != null -> defaultPresentation(transition.state)
            readingDetails && transition.events.any { it.type == WorldEventType.GROWTH_QUALIFIED } -> WorldPresentation.QUALIFIED
            else -> oldUi.detailsPresentation
        }
        update { it.copy(world = transition.state, presentation = presentation, detailsPresentation = detailsPresentation, pendingEvolution = evolution, lastEffect = effect, selectedTool = if (transition.state.invasion != null) null else it.selectedTool, error = null, saveFailed = false, canRebuild = false) }
    }

    private suspend fun checkpoint(at: Long, force: Boolean = false) {
        if (!dirty || state.value.error != null || (!force && at - lastSave < CHECKPOINT_MILLIS)) return
        val world = state.value.world ?: return
        val error = store.save(world)
        if (error != null) fail(error, canRebuild = false)
        else { dirty = false; lastSave = at }
    }

    private fun fail(reason: String, canRebuild: Boolean) {
        if (recoveryPresentation == null && state.value.error == null) recoveryPresentation = state.value.presentation
        update { it.copy(presentation = WorldPresentation.RECOVERY_ERROR, error = reason, saveFailed = true, canRebuild = canRebuild) }
    }

    private suspend fun retry() {
        if (state.value.error == null && pending == null) return
        val transition = pending
        val current = state.value.world
        if (transition != null) {
            val error = store.save(transition.state)
            if (error == null) {
                pending = null
                dirty = false
                lastSave = uptime()
                publish(transition, recoveryPresentation ?: defaultPresentation(transition.state))
                recoveryPresentation = null
            }
            else fail(error, false)
        } else if (current != null && state.value.saveFailed) {
            val error = store.save(current)
            if (error == null) {
                dirty = false
                lastSave = uptime()
                val presentation = recoveryPresentation ?: defaultPresentation(current)
                update { it.copy(error = null, saveFailed = false, presentation = presentation) }
                recoveryPresentation = null
            }
            else fail(error, false)
        } else load()
        if (state.value.error == null) {
            val openedOn = deferredOpenDate
            // Only a real stopped -> started transition authorizes this Open. Keep it if
            // its own critical save fails; retrying that pending transition is idempotent.
            if (openedOn != null && apply(WorldCommand.Open(openedOn, sessionId))) deferredOpenDate = null
        }
        clock.rebase(uptime(), now())
    }

    private fun refreshGates() {
        val s = state.value
        val activePresentation = s.presentation in setOf(WorldPresentation.OBSERVING, WorldPresentation.SELECTING, WorldPresentation.INTERVENTION_FEEDBACK, WorldPresentation.QUALIFIED, WorldPresentation.EVOLVING, WorldPresentation.WARNING, WorldPresentation.BATTLE, WorldPresentation.VICTORY, WorldPresentation.HELP, WorldPresentation.DETAILS)
        val growth = started && s.sceneAvailable && !manuallyPaused && s.error == null && s.world != null && activePresentation
        val presentationMotion = started && s.sceneAvailable && !manuallyPaused && s.error == null && s.world != null &&
            (activePresentation || s.presentation == WorldPresentation.RESET)
        // Reading either modal blocks the target without stopping daily growth.
        val battle = growth && s.isSighted && s.presentation !in setOf(WorldPresentation.HELP, WorldPresentation.DETAILS)
        if (growth != s.growthActive || battle != s.battleActive) trace("gates growthActive=$growth battleActive=$battle manualPause=$manuallyPaused")
        update { it.copy(growthActive = growth, battleActive = battle, presentationMotionActive = presentationMotion) }
    }

    override fun onCleared() {
        onEvent(WorldUiEvent.Platform(false, false, false, false, "VIEWMODEL_CLEAR"))
        inputs.close()
        super.onCleared()
    }

    companion object { internal const val CHECKPOINT_MILLIS = 10_000L }
}
