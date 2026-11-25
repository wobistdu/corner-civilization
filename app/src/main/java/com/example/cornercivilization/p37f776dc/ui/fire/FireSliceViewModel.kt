package com.example.cornercivilization.p37f776dc.ui.fire

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceStore
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.FireSliceUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


class FireSliceViewModel(
    private val store: FireSliceStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val mutable = MutableStateFlow(FireSliceUiState())
    val state = mutable.asStateFlow()
    private val events = Channel<FireSliceEvent>(Channel.UNLIMITED)
    private val rules = FireSliceUseCase()
    private var active = false
    private var previous: Long? = null
    private var shelterOperational = true
    private var checkpoint = 0L

    init {
        viewModelScope.launch {
            try { mutable.value = mutable.value.copy(world = withContext(dispatcher) { store.load() }) }
            catch (_: Exception) { mutable.value = mutable.value.copy(error = "营地记录暂时无法读取，请重新打开。") }
            for (event in events) {
                val ui = mutable.value
                val before = ui.world ?: continue
                if (ui.error != null) continue
                var next = before
                var forceSave = false
                var nextTool = ui.tool
                var nextHint = ui.firstToolHint
                when (event) {
                    is FireSliceEvent.Platform -> {
                        if (active) {
                            val delta = elapsed(event.atMillis)
                            next = rules.evolve(rules.advance(before, delta, false), delta, shelterOperational)
                        }
                        active = event.active; previous = event.atMillis; forceSave = !active
                    }
                    is FireSliceEvent.Pulse -> {
                        shelterOperational = event.shelterOperational
                        // Stable scene results can reconcile after restore even while simulation is paused.
                        if (event.fieldEstablished) next = next.copy(fieldEstablished = true)
                        val delta = elapsed(event.atMillis)
                        if (active) {
                            next = rules.demonstrateRain(rules.observeFire(rules.evolve(rules.advance(next, delta, event.participantsReady), delta, shelterOperational), event.participantsReady),event.shelterOperational)
                            // Readiness from a stale frame cannot satisfy a newly assigned destination.
                            next = rules.advanceShelter(next, delta.coerceAtMost(250), event.workersReady && event.task == next.shelter)
                            next = rules.advanceResources(next, event.resourceReady && event.resourceTask == next.resourcePhase)
                        }
                    }
                    is FireSliceEvent.Select -> {
                        nextTool = if (ui.tool == event.tool) FireTool.OBSERVE else event.tool
                        val bit = 1 shl event.tool.ordinal
                        val first = before.seenTools and bit == 0
                        nextHint = if (first) event.tool else null
                        next = next.copy(seenTools = before.seenTools or bit)
                        forceSave = first
                    }
                    is FireSliceEvent.DismissHint -> if (ui.firstToolHint == event.tool) nextHint = null
                    FireSliceEvent.HearthTap -> if (active) next = rules.intervene(before, ui.tool, shelterOperational)
                }
                forceSave = forceSave || next.phase != before.phase || next.waterSequence != before.waterSequence || next.knowsFire != before.knowsFire
                forceSave = forceSave || next.shelter != before.shelter || next.buildMillis / 2_000 != before.buildMillis / 2_000 || next.rainDemonstrated != before.rainDemonstrated
                forceSave = forceSave || next.agricultural != before.agricultural || next.resourcePhase != before.resourcePhase
                forceSave = forceSave || next.suppliedWood != before.suppliedWood || next.deliveries != before.deliveries || next.usedWood != before.usedWood
                try {
                    if (forceSave || next.activeMillis - checkpoint >= 5_000) {
                        withContext(dispatcher) { store.save(next) }; checkpoint = next.activeMillis
                    }
                    mutable.value = mutable.value.copy(world = next, tool = nextTool, firstToolHint = nextHint)
                } catch (_: Exception) {
                    active = false
                    mutable.value = mutable.value.copy(error = "营地记录暂时无法保存，活动已暂停。")
                }
            }
        }
    }

    fun onEvent(event: FireSliceEvent) { events.trySend(event) }
    private fun elapsed(now: Long): Long = (previous?.let { (now - it).coerceAtLeast(0) } ?: 0).also { previous = now }
}
