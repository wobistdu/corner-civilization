package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.sqrt

/** Pure sampling policy for bounded grass islands; all final feet still need actual terrain support. */
internal object PrimitiveVegetationPatch {
    const val MAX_TUFTS = 4_800
    const val BLADES_PER_TUFT = 6
    const val TRIANGLES_PER_BLADE = 4
    const val MAX_GRASS_TRIANGLES = MAX_TUFTS * BLADES_PER_TUFT * TRIANGLES_PER_BLADE

    fun offset(unitRadius: Float, patchRadius: Float): Float {
        require(unitRadius.isFinite() && unitRadius in 0f..1f)
        require(patchRadius.isFinite() && patchRadius > 0f)
        return sqrt(unitRadius) * patchRadius
    }

    fun bladeHeight(unit: Float, bankEdge: Boolean): Float {
        require(unit.isFinite() && unit in 0f..1f)
        return if (bankEdge) .0045f + unit * .002f else .0022f + unit * .0023f
    }
}
