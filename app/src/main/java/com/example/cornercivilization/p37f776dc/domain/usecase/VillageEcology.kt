package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point

/** Stable crop and settlement history. Visitor positions belong to the running scene. */
internal class VillageEcology {
    val field = Point(-.055f, .285f)
    var bank = Point(-.22f, .19f)
    val refuge = Point(-.155f, .235f)
    val sites = linkedMapOf("FieldFence" to Point(-.056f,.270f),
        "Granary" to Point(-.104f,.261f), "Longhouse" to Point(-.139f,.311f))
    var crop = .64f
    var food = 0
    var foodCapacity = 8
    var cultivation = 0f
    var raids = 0
    var recoveryUntil = 0f
    var mammoth: Point? = null
    var tiger: Point? = null
    var guard: Point? = null
    var scorch: Point? = null
    var scarAge = 0f
    fun grow(dt:Float, wet:Float) { crop = (crop + dt * (.0015f + wet * .016f)).coerceAtMost(1f) }
    fun graze(dt:Float) { crop = (crop - dt * .052f).coerceAtLeast(.05f) }
    fun harvest() {
        if(crop >= .55f && food < foodCapacity) { food++; crop -= .19f }
    }
}
