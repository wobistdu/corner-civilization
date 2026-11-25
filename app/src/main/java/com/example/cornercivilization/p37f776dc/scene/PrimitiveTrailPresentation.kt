package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState

/** A saved-result projection, never a new clock or a fabricated traversal counter. */
internal data class PrimitiveTrailPresentation(val woodWear: Int, val fieldVisible: Boolean) {
    companion object {
        fun from(state: FireSliceState) = PrimitiveTrailPresentation(
            when { state.deliveries >= 6 -> 3; state.deliveries >= 3 -> 2; state.deliveries >= 1 -> 1; else -> 0 },
            state.agricultural,
        )
    }
}
