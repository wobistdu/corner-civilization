package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.atan2

/** A presentation-only first-field routine, anchored outside the measured field mesh. */
internal class PrimitiveFarmWalk(val left: PrimitiveGroundPoint, val right: PrimitiveGroundPoint) {
    val anchors = listOf(PrimitiveGroundPoint(.005f, .13f), PrimitiveGroundPoint(.005f, .23f), left, right)
    private var position: PrimitiveGroundPoint? = null
    private var waypoint = 0
    private var dwell = 0f
    private var reverse = false

    fun sample(ordinary: PrimitiveResidentPose, agricultural: Boolean, recentWater: Boolean, delta: Float): PrimitiveResidentPose {
        require(delta.isFinite() && delta >= 0f)
        if (!agricultural) return ordinary
        val p = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z).also { position = it }
        val goal = anchors[waypoint]
        if (recentWater) return PrimitiveResidentPose(p.x,p.z,0f,PrimitiveActivity.IDLE,0f)
        val distance = p.distanceTo(goal)
        val speed = .0075f
        val moving = distance > .00005f
        val step = minOf(distance, speed*delta)
        val next = if (moving) PrimitiveGroundPoint(p.x+(goal.x-p.x)*step/distance,p.z+(goal.z-p.z)*step/distance) else p
        position = next
        if (!moving) dwell += delta
        val activity = if (moving) PrimitiveActivity.WALK else if (waypoint >= 2 && dwell < 14f) PrimitiveActivity.GATHER else PrimitiveActivity.IDLE
        if (!moving && dwell >= if (waypoint >= 2) 19f else 3f) {
            if (waypoint == anchors.lastIndex) reverse = true
            if (waypoint == 0) reverse = false
            waypoint += if (reverse) -1 else 1
            dwell = 0f
        }
        val yaw = if (moving) atan2(goal.x-p.x,goal.z-p.z)*180f/Math.PI.toFloat() else 0f
        return PrimitiveResidentPose(next.x,next.z,yaw,activity,if (moving) speed else 0f)
    }
}
