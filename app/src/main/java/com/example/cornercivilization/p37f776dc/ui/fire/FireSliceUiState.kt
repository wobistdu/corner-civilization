package com.example.cornercivilization.p37f776dc.ui.fire

import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.FireTool

data class FireSliceUiState(val world: FireSliceState? = null, val tool: FireTool = FireTool.OBSERVE, val error: String? = null,
    val firstToolHint: FireTool? = null)
sealed interface FireSliceEvent {
    data class Platform(val active: Boolean, val atMillis: Long) : FireSliceEvent
    data class Pulse(val atMillis: Long, val participantsReady: Boolean, val workersReady: Boolean = false, val task: com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase? = null,
        val resourceReady: Boolean = false, val resourceTask: com.example.cornercivilization.p37f776dc.domain.model.ResourcePhase? = null,
        val shelterOperational: Boolean = true, val fieldEstablished: Boolean = false) : FireSliceEvent
    data class Select(val tool: FireTool) : FireSliceEvent
    data object HearthTap : FireSliceEvent
    data class DismissHint(val tool: FireTool) : FireSliceEvent
}
