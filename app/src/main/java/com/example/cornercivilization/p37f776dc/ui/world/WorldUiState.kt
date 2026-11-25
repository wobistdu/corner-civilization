package com.example.cornercivilization.p37f776dc.ui.world

import com.example.cornercivilization.p37f776dc.domain.model.InterventionKind
import com.example.cornercivilization.p37f776dc.domain.model.Era
import com.example.cornercivilization.p37f776dc.domain.model.WorldState

enum class WorldPresentation { LOADING, FIRST_OPEN, OBSERVING, SELECTING, INTERVENTION_FEEDBACK, QUALIFIED, EVOLVING, WARNING, BATTLE, VICTORY, RESET, PAUSED, RECOVERY_ERROR, RESTORE_CONFIRM, HELP, DETAILS }

data class PendingEvolution(val id: String, val fromEra: Era, val toEra: Era)

data class WorldUiState(
    val world: WorldState? = null,
    val presentation: WorldPresentation = WorldPresentation.LOADING,
    val detailsPresentation: WorldPresentation = WorldPresentation.OBSERVING,
    val pendingEvolution: PendingEvolution? = null,
    val selectedTool: InterventionKind? = null,
    val lastEffect: InterventionKind? = null,
    val isSighted: Boolean = false,
    val sceneAvailable: Boolean = false,
    val growthActive: Boolean = false,
    val battleActive: Boolean = false,
    val presentationMotionActive: Boolean = false,
    val error: String? = null,
    val saveFailed: Boolean = false,
    val canRebuild: Boolean = false,
)

sealed interface WorldUiEvent {
    data object Start : WorldUiEvent
    data object Pause : WorldUiEvent
    data object Resume : WorldUiEvent
    data object Help : WorldUiEvent
    data object CloseHelp : WorldUiEvent
    data object Details : WorldUiEvent
    data object CloseDetails : WorldUiEvent
    data object Acknowledge : WorldUiEvent
    data object CancelTool : WorldUiEvent
    data object Retry : WorldUiEvent
    data object AskReset : WorldUiEvent
    data object CancelReset : WorldUiEvent
    data object ConfirmReset : WorldUiEvent
    data class SelectTool(val kind: InterventionKind) : WorldUiEvent
    data class PlotTap(val plotId: String) : WorldUiEvent
    data class InvaderTap(val eventId: String, val targetId: String) : WorldUiEvent
    data class EvolutionPresented(val id: String) : WorldUiEvent
    data class SceneAvailability(val available: Boolean) : WorldUiEvent
    data class Platform(val started: Boolean, val sighted: Boolean, val focused: Boolean, val onstage: Boolean, val event: String) : WorldUiEvent
    data object Pulse : WorldUiEvent
}

internal sealed interface WorldLoad {
    data class Loaded(val state: WorldState) : WorldLoad
    data object Empty : WorldLoad
    data class Failed(val reason: String, val canRebuild: Boolean = false) : WorldLoad
}

/** Only the Factory binds these operations to the file repository. */
internal interface WorldStore {
    suspend fun load(): WorldLoad
    suspend fun save(state: WorldState): String?
}
