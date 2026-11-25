package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.animation.AnimationPlaybackController
import com.pico.spatial.core.ecs.resource.AnimationResource

internal data class PrimitiveClipRange(val startSeconds: Float, val endSeconds: Float) {
    init { require(startSeconds.isFinite() && endSeconds.isFinite() && startSeconds >= 0f && endSeconds > startSeconds) }
    val durationSeconds: Float get() = endSeconds - startSeconds
}

/** SDK 6.2.8 public skeletal API; every call belongs to SpatialView's main thread. */
internal class PrimitiveSkeletalPlayer(
    model: Entity,
    private val residentId: String,
    clipNames: Map<PrimitiveActivity, String>,
    private val clipRanges: Map<PrimitiveActivity, PrimitiveClipRange> = emptyMap(),
) {
    private data class Skeleton(
        val entity: Entity,
        val clips: Map<PrimitiveActivity, AnimationResource>,
        var controller: AnimationPlaybackController? = null,
    )

    private val skeletons: List<Skeleton> = model.findSkeletonRootEntity(includeDisabled = true).map { skeleton ->
        val resources = skeleton.getAnimationResources().toList()
        val clips = clipNames.keys.associateWith { activity ->
            val expected = requireNotNull(clipNames[activity]) { "Missing handoff clip name for $activity" }
            val matches = resources.filter { it.getName() == expected }
            check(matches.size == 1) {
                "$residentId skeleton=${skeleton.getName()} requires one clip '$expected'; actual=${resources.map { it.getName() }}"
            }
            matches.single()
        }
        Log.i(TAG, "skeleton resident=$residentId entity=${skeleton.getName()} clips=${resources.map { it.getName() }}")
        Skeleton(skeleton, clips)
    }.also { check(it.isNotEmpty()) { "$residentId has no skeleton roots; static mesh is not an animated resident" } }

    private var activity: PrimitiveActivity? = null
    private var requestedSpeed = 1f
    private var active = true
    private var disposed = false

    /** Clips are authored in-place. Movement changes the parent, never a skeleton transform. */
    fun play(next: PrimitiveActivity, phaseSeconds: Float, speed: Float = 1f) {
        check(!disposed)
        require(phaseSeconds.isFinite() && phaseSeconds >= 0f)
        require(speed.isFinite() && speed >= 0f)
        if (activity == next) {
            if (kotlin.math.abs(requestedSpeed-speed)>.001f) {
                skeletons.forEach { skeleton ->
                    skeleton.controller?.takeIf { it.valid }?.let { controller ->
                        controller.setSpeed(if(speed>0f)speed else 1f)
                        if(active&&speed>0f)controller.resume()else controller.pause()
                    }
                }
                requestedSpeed=speed
            }
            return
        }
        skeletons.forEach { skeleton ->
            skeleton.controller?.let { controller ->
                if (controller.valid) controller.stop()
                controller.close()
            }
            // Imported skeletal resources loop by default and stay owned by the Entity.
            val controller = skeleton.entity.playAnimation(requireNotNull(skeleton.clips[next]))
            skeleton.controller = controller
            check(controller.valid) { "Invalid skeletal controller: $residentId $next" }
            controller.setSpeed(if(speed>0f)speed else 1f)
            val duration = controller.getDuration()
            check(duration.isFinite() && duration > 0f) { "Empty skeletal clip: $residentId $next duration=$duration" }
            val range = clipRanges[next]
            if (range != null) {
                check(range.endSeconds <= duration + .001f) { "Clip segment exceeds delivered animation: $residentId $next range=$range duration=$duration" }
                controller.setTime(range.startSeconds + phaseSeconds % range.durationSeconds)
            } else controller.setTime(phaseSeconds % duration)
            if (!active || speed==0f) controller.pause()
            Log.i(TAG, "clip resident=$residentId activity=$next duration=$duration speed=$speed skeletal=true")
        }
        activity = next
        requestedSpeed = speed
    }

    fun setActive(value: Boolean) {
        if (disposed || active == value) return
        active = value
        skeletons.forEach { skeleton ->
            skeleton.controller?.takeIf { it.valid }?.let { if (value&&requestedSpeed>0f) it.resume() else it.pause() }
        }
    }

    /** Needs and held objects share the work clock, including interruption/pause. */
    fun seekWorkTime(seconds:Float) {
        check(!disposed)
        require(seconds.isFinite()&&seconds>=0f)
        skeletons.forEach { skeleton ->
            skeleton.controller?.takeIf{it.valid}?.let { controller ->
                controller.setTime(seconds.coerceAtMost(controller.getDuration()-.0001f))
            }
        }
    }

    /** Optional single-timeline fallback, used only when the actual Editor handoff needs it. */
    fun enforceSegmentLoop() {
        if (disposed || !active || requestedSpeed==0f) return
        val range = activity?.let(clipRanges::get) ?: return
        skeletons.forEach { skeleton ->
            skeleton.controller?.takeIf { it.valid }?.let { controller ->
                val time = controller.getTime()
                if (time >= range.endSeconds || time < range.startSeconds) {
                    // The final carry segment can cross the model's global loop
                    // before this frame observes its end; then getTime starts at 0.
                    val elapsed = if (time < range.startSeconds) time + controller.getDuration() - range.startSeconds else time - range.startSeconds
                    controller.setTime(range.startSeconds + elapsed % range.durationSeconds)
                }
            }
        }
    }

    val skeletonCount: Int get() = skeletons.size
    val skeletonRootIds: List<Long> get() = skeletons.map { it.entity.id }

    fun diagnostic(): String = "clip=$activity skeletons=${skeletons.size} controllers=" + skeletons.joinToString(prefix = "[", postfix = "]") { skeleton ->
        val controller = skeleton.controller
        if (controller == null || !controller.valid) "${skeleton.entity.getName()}:invalid"
        else "${skeleton.entity.getName()}:time=${controller.getTime()},playing=${controller.isPlaying()},speed=${controller.getSpeed()}"
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        skeletons.forEach { skeleton ->
            skeleton.controller?.let { controller ->
                if (controller.valid) controller.stop()
                controller.close()
            }
            skeleton.controller = null
        }
    }

    companion object { private const val TAG = "PrimitivePreview" }
}
