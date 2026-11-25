package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase

/** Pure projection, not a second world clock or inventory. One pile represents up to four units. */
internal data class PrimitiveMemoryPresentation(val pendingPiles: Int, val storedPiles: Int, val fieldVisible: Boolean) {
    companion object {
        fun from(state: FireSliceState): PrimitiveMemoryPresentation {
            val complete = state.shelter == ShelterPhase.COMPLETE
            return PrimitiveMemoryPresentation(
                if (complete) (state.pendingWood + 3) / 4 else 0,
                if (complete) (state.storedWood + 3) / 4 else 0,
                state.agricultural,
            )
        }
    }
}
