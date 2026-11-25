package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.Era
import com.example.cornercivilization.p37f776dc.domain.model.WorldState

/** Explicit committed presentation event; ordinary snapshot restores carry none. */
data class SceneEvolution(val id: String, val fromEra: Era, val toEra: Era)

/** Disposable presentation state only; never contributes elapsed time to world rules. */
internal class SceneFeedback {
    enum class Kind { EVOLVE, SAFE_RESET }
    data class EraChange(val kind: Kind, val from: Era, val to: Era, var seconds: Float = 0f, var eventId: String? = null)
    var eraChange: EraChange? = null
        private set
    var victorySeconds: Float? = null
        private set
    private val acceptedIds = mutableSetOf<String>()
    private var lastEvolution: EraChange? = null
    private var completedNotification: String? = null

    fun accept(previous: WorldState?, next: WorldState, pending: SceneEvolution? = null) {
        when {
            previous != null && previous.civilizationId != next.civilizationId -> {
                eraChange = EraChange(Kind.SAFE_RESET, previous.era, next.era)
                victorySeconds = null
                lastEvolution = null
                completedNotification = null
                return
            }
            previous != null && previous.era != next.era -> {
                eraChange = EraChange(Kind.EVOLVE, previous.era, next.era)
                lastEvolution = eraChange
            }
            previous?.invasion != null && next.invasion == null -> victorySeconds = 0f
        }
        // No explicit event means a restored era never replays old history.
        if (pending == null || pending.toEra != next.era || pending.fromEra == pending.toEra || !acceptedIds.add(pending.id)) return
        val existing = lastEvolution?.takeIf { it.from == pending.fromEra && it.to == pending.toEra }
        if (existing != null) {
            // Adopt a same-process animation without resetting its progress.
            existing.eventId = pending.id
            if (existing.seconds >= EVOLVE_SECONDS) completedNotification = pending.id
        } else {
            eraChange = EraChange(Kind.EVOLVE, pending.fromEra, pending.toEra, eventId = pending.id)
            lastEvolution = eraChange
        }
    }

    /** Returns an acknowledgement only after active presentation has finished. */
    fun advance(delta: Float, motionActive: Boolean = true): String? {
        if (!motionActive) return null
        eraChange?.let {
            it.seconds += delta.coerceAtLeast(0f)
            if (it.seconds >= if (it.kind == Kind.SAFE_RESET) RESET_SECONDS else EVOLVE_SECONDS) {
                completedNotification = it.eventId ?: completedNotification
                eraChange = null
            }
        }
        victorySeconds = victorySeconds?.plus(delta)?.takeIf { it < VICTORY_SECONDS }
        return completedNotification.also { completedNotification = null }
    }

    companion object {
        const val RESET_SECONDS = 1.2f
        const val EVOLVE_SECONDS = 1f
        const val VICTORY_SECONDS = .65f
    }
}
