package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.sqrt

/** One-time art bake, not a dynamic lighting system. Values are linear multipliers. */
internal object PrimitiveBakedShade {
    fun factor(nx: Float, ny: Float, nz: Float): Float {
        require(nx.isFinite() && ny.isFinite() && nz.isFinite())
        val length=sqrt(nx*nx+ny*ny+nz*nz)
        if(length<.00001f) return .78f
        val diffuse=((nx*-.38f+ny*.86f+nz*.34f)/length).coerceIn(0f,1f)
        return (.53f+.47f*diffuse).coerceIn(.53f,1f)
    }
}
