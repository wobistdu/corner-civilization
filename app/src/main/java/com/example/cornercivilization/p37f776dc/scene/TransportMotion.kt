package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.*

internal data class TransportPose(val x: Float, val y: Float, val z: Float, val yaw: Float)

/** A tangent-facing elliptical lane relative to each genuine taxi's source owner. */
internal fun airTrafficMotion(seconds: Float, index: Int): TransportPose {
    val phase = seconds * .65f + index * PI.toFloat() / 4f
    return TransportPose(.020f * cos(phase), .0035f * sin(phase * 2f), .012f * sin(phase), atan2(-.020f * sin(phase), .012f * cos(phase)) * 180f / PI.toFloat())
}

/** Shuttle keeps the authored ground/road support height and changes only X and heading. */
internal fun groundTransportMotion(reach: Float, phase: Float): TransportPose =
    TransportPose(reach * sin(phase), 0f, 0f, if (cos(phase) >= 0f) 0f else 180f)
