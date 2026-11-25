package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.atan2

/** Three bounded visitors share the public camp corridor, not work/resource ownership. */
internal class PrimitiveCampWalk(
    val slot: Int,
    source: PrimitiveGroundPoint,
    val camp: PrimitiveGroundPoint,
) {
    init { require(slot in 0..2) }
    val anchors = listOf(source, PrimitiveGroundPoint(.005f, source.z),
        PrimitiveGroundPoint(.005f, .240f), PrimitiveGroundPoint(-.10f, .240f),
        PrimitiveGroundPoint(-.15f, .240f), camp)
    private var position = source
    private var waypoint = 0
    private var reverse = false
    private var dwell = slot * 7f
    private var pausedPose: PrimitiveResidentPose? = null

    fun sample(delta: Float, recentWater: Boolean, hearth: PrimitiveGroundPoint): PrimitiveResidentPose {
        require(delta.isFinite() && delta >= 0f)
        // Local reaction never moves a person, advances their route clock or affects remote visitors.
        if (recentWater && position.distanceTo(hearth) <= .075f) {
            return PrimitiveResidentPose(position.x, position.z,
                heading(position, hearth), PrimitiveActivity.IDLE, 0f).also { pausedPose = it }
        }
        if (delta == 0f && pausedPose != null) return requireNotNull(pausedPose)
        pausedPose = null
        val goal = anchors[waypoint]
        val distance = position.distanceTo(goal)
        val speed = .0075f
        val moving = distance > .00005f
        if (moving) {
            val step = minOf(distance, speed * delta)
            val before = position
            position = PrimitiveGroundPoint(before.x + (goal.x-before.x)*step/distance,
                before.z + (goal.z-before.z)*step/distance)
            return PrimitiveResidentPose(position.x, position.z, heading(before, goal),
                PrimitiveActivity.WALK, speed)
        }
        val activity = if (waypoint == 0 && dwell < 18f+slot*3f) PrimitiveActivity.GATHER else PrimitiveActivity.IDLE
        dwell += delta
        val hold = when (waypoint) {
            0 -> 25f+slot*3f
            anchors.lastIndex -> 16f+slot*2f
            else -> 0f
        }
        if (delta > 0f && dwell >= hold) {
            if (waypoint == anchors.lastIndex) reverse = true
            if (waypoint == 0) reverse = false
            waypoint += if (reverse) -1 else 1
            dwell = 0f
        }
        val facing = if (position.distanceTo(camp) < .001f) heading(position, hearth) else 0f
        return PrimitiveResidentPose(position.x, position.z, facing, activity, 0f)
    }

    private fun heading(from: PrimitiveGroundPoint, to: PrimitiveGroundPoint) =
        atan2(to.x-from.x, to.z-from.z)*180f/Math.PI.toFloat()
}
