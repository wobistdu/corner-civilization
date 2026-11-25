package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.model.*
import kotlin.math.*

/** Two fixed, terrain-checked approach lanes; only these two actors join the story. */
internal class PrimitiveFireWalk(val lane: Int, private val resourceMotionEnabled: Boolean = false) {
    val points = if (lane == 0) listOf(
        PrimitiveGroundPoint(-.0109921875f, .13566667f), PrimitiveGroundPoint(-.06f, .15f),
        PrimitiveGroundPoint(-.12f, .17f), PrimitiveGroundPoint(-.154f, .19f),
    ) else listOf(
        PrimitiveGroundPoint(-.0109921875f, .20166667f), PrimitiveGroundPoint(-.06f, .19f),
        PrimitiveGroundPoint(-.12f, .19f), PrimitiveGroundPoint(-.154f, .215f),
    )
    private var position: PrimitiveGroundPoint? = null
    private var waypoint = 0
    private var lastPhase: FirePhase? = null
    private var target = points.last()
    var ready = false
        private set
    var workReady = false
        private set
    private var taskKey: String? = null
    private var taskWaypoint = 0
    // Integration stays off by default until Entity readiness and presentation are available.
    private var resourceKey: ResourcePhase? = null
    private var resourceWaypoint = 0
    var resourceReady = false
        private set
    private var restSeconds = 0f

    private fun resourcePose(ordinary: PrimitiveResidentPose, state: FireSliceState, delta: Float): PrimitiveResidentPose {
        val z = if (lane == 0) .1861f else .2239f
        val source = PrimitiveGroundPoint(-.088f, .227f + if (lane == 0) -.0042f else .0042f)
        val storage = PrimitiveGroundPoint(-.13f, z)
        val rest = PrimitiveGroundPoint(-.18f, z)
        if (resourceKey != state.resourcePhase) {
            resourceKey = state.resourcePhase; resourceWaypoint = 0; restSeconds = 0f
        }
        if (state.resourcePhase == ResourcePhase.WAITING || state.resourcePhase == ResourcePhase.IDLE) {
            // Completed builders still live in camp even while resource delivery is gated.
            // Visit only the existing dry lane, never gather/carry invisible materials.
            val goal = if (resourceWaypoint % 2 == 0) rest else storage
            var p = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z)
            val distance = p.distanceTo(goal)
            val moving = distance > .0001f
            val speed = .0075f
            val step = minOf(distance, speed * delta)
            if (moving) {
                p = PrimitiveGroundPoint(p.x + (goal.x-p.x)*step/distance, p.z+(goal.z-p.z)*step/distance)
                restSeconds = 0f
            } else {
                restSeconds += delta
            }
            position = p
            val activity = when {
                moving -> PrimitiveActivity.WALK
                state.activeMillis - state.lastWaterAt in 0..4_000 -> PrimitiveActivity.CATCH_RAIN
                goal == rest && restSeconds < 3f -> PrimitiveActivity.TEND_FIRE
                else -> PrimitiveActivity.IDLE
            }
            val yaw = if (moving) atan2(goal.x-p.x,goal.z-p.z)*180f/PI.toFloat() else if (lane == 0) 0f else 180f
            if (!moving && restSeconds >= if (goal == rest) 8f + lane else 5f) {
                resourceWaypoint = (resourceWaypoint + 1) % 2
                restSeconds = 0f
            }
            ready = false
            return PrimitiveResidentPose(p.x,p.z,yaw,activity,if (moving) speed else 0f)
        }
        val route = when (state.resourcePhase) {
            ResourcePhase.FETCHING -> listOf(rest, storage, source)
            ResourcePhase.CARRYING -> listOf(storage)
            else -> listOf(rest)
        }
        val goal = route[resourceWaypoint]
        var p = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z)
        val distance = p.distanceTo(goal)
        val moving = distance > .0001f
        val speed = .0075f
        val step = minOf(distance, speed * delta)
        if (moving) p = PrimitiveGroundPoint(p.x + (goal.x-p.x)*step/distance, p.z+(goal.z-p.z)*step/distance)
        position = p
        val arrived = p.distanceTo(goal) < .0001f
        val finished = arrived && resourceWaypoint == route.lastIndex
        if (arrived && !finished) resourceWaypoint++
        resourceReady = finished && state.resourcePhase in listOf(ResourcePhase.FETCHING, ResourcePhase.CARRYING)
        if (finished) restSeconds += delta
        // Bounded dwell makes handoff readable; physical readiness must include the dwell.
        if (resourceReady && restSeconds < 1.2f) resourceReady = false
        ready = false
        val activity = when {
            state.resourcePhase == ResourcePhase.CARRYING -> PrimitiveActivity.CARRY_WOOD
            moving -> PrimitiveActivity.WALK
            state.resourcePhase == ResourcePhase.FETCHING -> PrimitiveActivity.GATHER
            state.activeMillis - state.lastWaterAt in 0..4_000 -> PrimitiveActivity.CATCH_RAIN
            else -> PrimitiveActivity.IDLE
        }
        val yaw = if (moving) atan2(goal.x-p.x,goal.z-p.z)*180f/PI.toFloat() else if (lane == 0) 0f else 180f
        return PrimitiveResidentPose(p.x,p.z,yaw,activity,if (moving) speed else 0f)
    }

    fun sample(ordinary: PrimitiveResidentPose, state: FireSliceState, delta: Float): PrimitiveResidentPose {
        workReady = false
        resourceReady = false
        val task = state.shelter
        if (resourceMotionEnabled && task == ShelterPhase.COMPLETE && state.phase == FirePhase.BURNING) {
            taskKey = null
            return resourcePose(ordinary, state, delta)
        }
        resourceKey = null
        if (task in listOf(ShelterPhase.BUILDING, ShelterPhase.COMPLETE) && state.phase != FirePhase.BURNING) {
            taskKey = null
            var p = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z)
            val entry = PrimitiveGroundPoint(-.18f, if (lane == 0) .1861f else .2239f)
            val hearth = PrimitiveGroundPoint(-.18f, .205f + if (lane == 0) -.007f else .007f)
            val goal = if (kotlin.math.abs(p.x + .18f) > .0001f) entry else hearth
            val distance = p.distanceTo(goal)
            val moving = state.phase != FirePhase.WET && distance > .0001f
            val yaw = if (moving) atan2(goal.x-p.x, goal.z-p.z)*180f/PI.toFloat() else if (lane == 0) 0f else 180f
            if (moving) {
                val step = minOf(distance, .0075f*delta)
                p = PrimitiveGroundPoint(p.x+(goal.x-p.x)*step/distance,p.z+(goal.z-p.z)*step/distance)
            }
            position = p
            ready = p.distanceTo(hearth) < .0001f && state.phase != FirePhase.WET
            return PrimitiveResidentPose(p.x,p.z,yaw, when {
                moving -> PrimitiveActivity.WALK
                state.phase == FirePhase.WET -> PrimitiveActivity.CATCH_RAIN
                ready -> PrimitiveActivity.TEND_FIRE
                else -> PrimitiveActivity.IDLE
            }, if (moving) .0075f else 0f)
        }
        if (state.phase == FirePhase.BURNING && task in listOf(ShelterPhase.FETCHING, ShelterPhase.CARRYING, ShelterPhase.BUILDING, ShelterPhase.COMPLETE)) {
            ready = false
            val z = if (lane == 0) .1861f else .2239f
            val key = task.name
            if (taskKey != key) { taskKey = key; taskWaypoint = 0 }
            val route = when (task) {
                ShelterPhase.FETCHING -> listOf(PrimitiveGroundPoint(-.13f, z), PrimitiveGroundPoint(-.088f, .227f + if (lane == 0) -.0042f else .0042f))
                ShelterPhase.CARRYING -> listOf(PrimitiveGroundPoint(-.13f, z), PrimitiveGroundPoint(-.18f, z))
                ShelterPhase.BUILDING -> listOf(PrimitiveGroundPoint(-.18f, z))
                else -> listOf(PrimitiveGroundPoint(-.18f, z), PrimitiveGroundPoint(-.18f, .205f + if (lane == 0) -.007f else .007f))
            }
            val goal = route[taskWaypoint]
            var p = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z)
            val distance = p.distanceTo(goal)
            val speed = .0075f
            val step = minOf(distance, speed * delta)
            if (distance > .00001f) p = PrimitiveGroundPoint(p.x+(goal.x-p.x)*step/distance, p.z+(goal.z-p.z)*step/distance)
            position = p
            val arrived = p.distanceTo(goal) < .0001f
            if (arrived && taskWaypoint < route.lastIndex) taskWaypoint++
            workReady = arrived && taskWaypoint == route.lastIndex && goal == route.last()
            ready = task == ShelterPhase.COMPLETE && workReady
            val activity = when {
                !workReady && task == ShelterPhase.CARRYING -> PrimitiveActivity.CARRY_WOOD
                !workReady -> PrimitiveActivity.WALK
                task == ShelterPhase.BUILDING -> PrimitiveActivity.LASH_ROOF
                task == ShelterPhase.COMPLETE -> PrimitiveActivity.TEND_FIRE
                else -> PrimitiveActivity.IDLE
            }
            val yaw = if (!arrived) atan2(goal.x-p.x, goal.z-p.z)*180f/PI.toFloat() else if (lane == 0) 0f else 180f
            return PrimitiveResidentPose(p.x,p.z,yaw,activity,if (!workReady) speed else 0f)
        }
        taskKey = null
        if (state.phase == FirePhase.QUIET) return ordinary
        var point = position ?: PrimitiveGroundPoint(ordinary.x, ordinary.z).also { position = it }
        val near = point.distanceTo(points.last()) < .04f
        if (lastPhase != state.phase) {
            if (state.phase == FirePhase.WET) target = if (near) PrimitiveGroundPoint(points.last().x+.015f, points.last().z) else point
            else if (lastPhase == FirePhase.WET && near) waypoint = points.lastIndex
            lastPhase = state.phase
        }
        if (state.phase != FirePhase.WET) target = points[waypoint]
        val distance = point.distanceTo(target)
        val speed = .0075f
        val step = minOf(speed*delta, distance)
        val yaw = if (distance > .00001f) atan2(target.x-point.x, target.z-point.z)*180f/PI.toFloat() else -90f
        if (distance > .00001f) point = PrimitiveGroundPoint(point.x+(target.x-point.x)*step/distance, point.z+(target.z-point.z)*step/distance)
        position = point
        val arrived = point.distanceTo(target) < .0001f
        if (arrived && state.phase != FirePhase.WET && waypoint < points.lastIndex) waypoint++
        ready = arrived && waypoint == points.lastIndex && target == points.last() && state.phase != FirePhase.WET
        val activity = when {
            !arrived -> PrimitiveActivity.WALK
            ready && state.phase == FirePhase.KINDLING -> PrimitiveActivity.TEND_FIRE
            arrived && state.phase == FirePhase.WET -> PrimitiveActivity.CATCH_RAIN
            else -> PrimitiveActivity.IDLE
        }
        return PrimitiveResidentPose(point.x,point.z,yaw,activity,if (activity == PrimitiveActivity.WALK) speed else 0f)
    }
}
