package com.example.cornercivilization.p37f776dc.scene

import kotlin.math.atan2
import kotlin.math.sqrt

/** Editor-world metres (+Y up, +Z forward); independent of saved game rules. */
internal data class PrimitiveGroundPoint(val x: Float, val z: Float) {
    init { require(x.isFinite() && z.isFinite()) }
    fun distanceTo(other: PrimitiveGroundPoint): Float {
        val dx = other.x - x
        val dz = other.z - z
        return sqrt(dx * dx + dz * dz)
    }
}

internal enum class PrimitiveActivity(val clip: String) {
    IDLE("idle"), WALK("walk"), GATHER("gather"), CARRY("carry"), CARRY_WOOD("carry_wood"), TEND_FIRE("tend_fire"), LASH_ROOF("lash_roof"), CATCH_RAIN("catch_rain"),
    WAVE("wave"), TALK("talk"), STRETCH("stretch"), EAT("eat"), DRINK("drink"), SIT_REST("sit_rest"),
}

/** The arrival action is held at this point, before travelling to the next point. */
internal data class PrimitiveRouteStop(
    val point: PrimitiveGroundPoint,
    val dwellSeconds: Float = 0f,
    val arrivalActivity: PrimitiveActivity = PrimitiveActivity.IDLE,
    val departingActivity: PrimitiveActivity = PrimitiveActivity.WALK,
) {
    init {
        require(dwellSeconds.isFinite() && dwellSeconds >= 0f)
        require(departingActivity == PrimitiveActivity.WALK || departingActivity == PrimitiveActivity.CARRY)
    }
}

internal data class PrimitiveResidentPose(
    val x: Float,
    val z: Float,
    val yawDegrees: Float,
    val activity: PrimitiveActivity,
    val metresPerSecond: Float,
)

/** Closed, constant-speed presentation route. No domain time, random drift, or root bobbing. */
internal class PrimitiveResidentRoute(
    val stops: List<PrimitiveRouteStop>,
    val speedMetresPerSecond: Float,
    val phaseSeconds: Float = 0f,
) {
    private val travelSeconds: List<Float>
    val durationSeconds: Float

    init {
        require(stops.size >= 2)
        require(speedMetresPerSecond.isFinite() && speedMetresPerSecond > 0f)
        require(phaseSeconds.isFinite() && phaseSeconds >= 0f)
        travelSeconds = stops.indices.map { index ->
            val distance = stops[index].point.distanceTo(stops[(index + 1) % stops.size].point)
            require(distance > .00001f) { "Resident route has coincident consecutive stops at $index" }
            distance / speedMetresPerSecond
        }
        durationSeconds = stops.indices.sumOf { (stops[it].dwellSeconds + travelSeconds[it]).toDouble() }.toFloat()
    }

    fun sample(seconds: Float): PrimitiveResidentPose {
        require(seconds.isFinite() && seconds >= 0f)
        var local = (seconds + phaseSeconds) % durationSeconds
        for (index in stops.indices) {
            val stop = stops[index]
            val next = stops[(index + 1) % stops.size]
            val previous = stops[(index + stops.size - 1) % stops.size]
            if (local < stop.dwellSeconds) {
                return PrimitiveResidentPose(stop.point.x, stop.point.z,
                    heading(previous.point, stop.point), stop.arrivalActivity, 0f)
            }
            local -= stop.dwellSeconds
            if (local < travelSeconds[index] || index == stops.lastIndex) {
                val fraction = local / travelSeconds[index]
                return PrimitiveResidentPose(
                    stop.point.x + (next.point.x - stop.point.x) * fraction,
                    stop.point.z + (next.point.z - stop.point.z) * fraction,
                    heading(stop.point, next.point), stop.departingActivity, speedMetresPerSecond,
                )
            }
            local -= travelSeconds[index]
        }
        error("Resident route traversal failed")
    }

    private fun heading(from: PrimitiveGroundPoint, to: PrimitiveGroundPoint): Float =
        (atan2(to.x - from.x, to.z - from.z) * 180f / Math.PI.toFloat())
}

/** A short physical turn follows the route velocity through the shortest signed angle. */
internal class PrimitiveResidentHeading(initialYawDegrees: Float, private val turnSeconds: Float = .35f) {
    var yawDegrees: Float = initialYawDegrees
        private set

    init { require(initialYawDegrees.isFinite() && turnSeconds.isFinite() && turnSeconds > 0f) }

    fun advance(targetYawDegrees: Float, deltaSeconds: Float): Float {
        require(targetYawDegrees.isFinite() && deltaSeconds.isFinite() && deltaSeconds >= 0f)
        val difference = ((targetYawDegrees - yawDegrees + 540f) % 360f) - 180f
        val limit = 180f * deltaSeconds / turnSeconds
        yawDegrees += difference.coerceIn(-limit, limit)
        yawDegrees = ((yawDegrees + 180f) % 360f + 360f) % 360f - 180f
        return yawDegrees
    }
}
