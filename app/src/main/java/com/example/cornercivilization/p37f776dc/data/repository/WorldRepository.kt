package com.example.cornercivilization.p37f776dc.data.repository

import com.example.cornercivilization.p37f776dc.domain.model.*

data class WorldSnapshot(
    val state: WorldState,
    val timezoneId: String,
    val ruleConfigVersion: Int = 1,
    val schemaVersion: Int = 1,
)

sealed class LoadResult {
    data class Loaded(val snapshot: WorldSnapshot, val recoveredFromBackup: Boolean = false) : LoadResult()
    data object Empty : LoadResult()
    data class Corrupt(val reason: String) : LoadResult()
    data class UnsupportedVersion(val version: Int) : LoadResult()
}

sealed class SaveResult {
    data class Saved(val revision: Long) : SaveResult()
    data class Failed(val reason: String) : SaveResult()
}

interface WorldRepository {
    fun load(): LoadResult
    fun save(snapshot: WorldSnapshot): SaveResult
}

sealed class CommitResult {
    data class Applied(val transition: Transition) : CommitResult()
    data class Failed(val previousState: WorldState, val reason: String) : CommitResult()
}

/** Synchronous application-layer helper; the UI coordinator invokes it on its serial IO worker. */
class WorldCommitter(
    private val repository: WorldRepository,
    private val timezoneId: String,
    private val ruleConfigVersion: Int = 1,
) {
    fun commit(previous: WorldState, transition: Transition): CommitResult {
        if (transition.persistence == Persistence.NONE) return CommitResult.Applied(transition)
        val saved = try {
            repository.save(WorldSnapshot(transition.state, timezoneId, ruleConfigVersion))
        } catch (error: Exception) {
            SaveResult.Failed(error.message ?: error.javaClass.simpleName)
        }
        return when (saved) {
            is SaveResult.Saved -> if (saved.revision == transition.state.revision)
                CommitResult.Applied(transition) else CommitResult.Failed(previous, "SAVED_REVISION_MISMATCH")
            is SaveResult.Failed -> CommitResult.Failed(previous, saved.reason)
        }
    }
}
