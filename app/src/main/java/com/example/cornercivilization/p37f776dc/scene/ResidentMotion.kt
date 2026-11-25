package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.Era
import com.example.cornercivilization.p37f776dc.domain.model.ResidentAction
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Meter offsets and degrees; pure values keep animation tests independent of Android. */
data class ResidentPose(val offsetX: Float, val offsetY: Float, val offsetZ: Float, val yawDegrees: Float)

/**
 * Primitive residents hop and turn around their camp; farmers sway slowly;
 * industrial residents twitch in short machine-like bursts; modern residents
 * march in coordinated small steps; space residents float along smooth arcs.
 * The caller owns active time: an unchanged seconds value always holds the pose.
 * Reset retreat and victory celebration remain separate runtime presentations.
 */
fun residentMotion(era: Era, action: ResidentAction, seconds: Float, index: Int): ResidentPose {
    val time = seconds.takeIf { it.isFinite() }?.toDouble() ?: 0.0
    // Modern residents share a cadence; other eras keep individual personalities.
    val phase = time * 2.0 + if (era == Era.MODERN) (index % 2) * .18 else index * .83
    val beat = when (era) {
        Era.PRIMITIVE -> Beat(sin(phase), abs(sin(phase * 1.4)), sin(phase * .5), sin(phase) * .9)
        Era.AGRICULTURAL -> Beat(sin(phase * .65) * .35, (1 - cos(phase * .65)) * .5, sin(phase * .65) * .8, sin(phase * .4) * .45)
        Era.INDUSTRIAL -> {
            val burst = sin(phase * 2.6)
            Beat(burst * burst * burst * .7, abs(burst) * .65, sin(phase * 1.3) * .3, burst)
        }
        Era.MODERN -> Beat(sin(phase) * .7, abs(sin(phase)) * .45, cos(phase) * .7, sin(phase * .5) * .3)
        Era.SPACE -> Beat(sin(phase * .6) * .65, (1 + sin(phase * .7)) * .5, cos(phase * .6) * .7, sin(phase * .4) * .8)
    }
    val pose = when (action) {
        ResidentAction.REST -> ResidentPose((beat.x * .0002).toFloat(), (beat.y * .0005).toFloat(), (beat.z * .0002).toFloat(), (beat.yaw * 2).toFloat())
        ResidentAction.GATHER -> ResidentPose((beat.x * .003).toFloat(), (beat.y * .0035).toFloat(), (beat.z * .0015).toFloat(), (beat.yaw * 12).toFloat())
        ResidentAction.BUILD -> ResidentPose((beat.x * .002).toFloat(), (beat.y * .0018).toFloat(), (.002 + beat.z * .002).toFloat(), (beat.yaw * 9).toFloat())
        ResidentAction.EVADE -> ResidentPose((beat.x * .004).toFloat(), (beat.y * .0043).toFloat(), (-.006 + beat.z * .002).toFloat(), (beat.yaw * 15).toFloat())
        ResidentAction.RESCUE -> ResidentPose((beat.x * .004).toFloat(), (beat.y * .0025).toFloat(), (-.002 + beat.z * .002).toFloat(), (beat.yaw * 12).toFloat())
    }
    // Explicit limits document the measured budget and contain float rounding.
    return pose.copy(offsetX = pose.offsetX.coerceIn(-.004f, .004f), offsetY = pose.offsetY.coerceIn(0f, .0043f), offsetZ = pose.offsetZ.coerceIn(-.008f, .0045f))
}

private data class Beat(val x: Double, val y: Double, val z: Double, val yaw: Double)
