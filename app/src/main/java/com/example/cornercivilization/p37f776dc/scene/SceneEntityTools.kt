package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs
import kotlin.math.min

/** Public SDK API helpers; all callers run on the SpatialView/Compose main thread. */
internal fun Entity.requireNode(name: String): Entity =
    requireNotNull(findEntity(name)) { "Managed scene is missing runtime node: $name" }

internal fun Entity.transform(): TransformComponent =
    requireNotNull(components.get<TransformComponent>()) { "Missing transform on ${getName()}" }

internal fun Entity.installBoxTarget(size: Vector3, center: Vector3) {
    require(size.x > 0 && size.y > 0 && size.z > 0)
    val shape = ShapeResource.createBox(size).offsetByTranslation(center)
    components.set(CollisionComponent(listOf(shape), PhysicsMaterialResource()))
    components.set(InteractableComponent())
}

internal fun Entity.installVisualBoundsTarget() {
    val bounds = getVisualBounds(this, recursive = true, enabledOnly = false)
    check(!bounds.isEmpty()) { "Cannot target an empty managed model: ${getName()}" }
    installBoxTarget(bounds.size, bounds.center)
}

/** Finds a registered logical owner from an actual hit, including imported mesh children. */
internal fun Entity.logicalTarget(registered: Map<Long, String>): String? {
    var current: Entity? = this
    while (current != null) {
        registered[current.id]?.let { return it }
        current = current.getParent()
    }
    return null
}

/** World scale uses only body X/Z. Independently measured UI only affects Y centering. */
internal fun fitManagedScene(
    content: SpatialViewContent,
    placementOwner: Entity,
    fittedRoot: Entity,
    bodyBounds: BoundingBox,
    requiredBelowMeters: Float,
): Boolean {
    val corner = content.convertPosition(Vector3.ZERO, ViewCoordinateSpace.Local, content.localSpatialCoordinateSpace)
    val safe = Vector3(abs(corner.x), abs(corner.y), abs(corner.z))
    if (!safe.finitePositive() || bodyBounds.isEmpty() || !requiredBelowMeters.isFinite() || requiredBelowMeters < 0f) return false
    val available = safe * 1.96f
    val size = bodyBounds.size
    if (!size.finitePositive()) return false
    val factor = min(available.x / size.x, available.z / size.z)
    check(factor.isFinite() && factor > 0) { "Managed body cannot fit the current Volume" }
    val compositionCenterY = bodyBounds.center.y * factor - requiredBelowMeters / 2f
    fittedRoot.transform().apply {
        scaleVector = Vector3(factor)
        position = Vector3(-bodyBounds.center.x * factor, -compositionCenterY, -bodyBounds.center.z * factor)
    }
    val bodyFinal = boundsOf(listOf(bodyBounds.min, bodyBounds.max).map { fittedRoot.convertPositionTo(it, placementOwner) })
    check(bodyFinal.insideHalfExtents(safe, .001f)) { "World body exceeds Volume; world scale is preserved: $bodyFinal" }
    val measured = fittedRoot.getVisualBounds(placementOwner, recursive = true, enabledOnly = false)
    val valid = !measured.isEmpty() && measured.insideHalfExtents(safe, .001f)
    Log.i("CivilizationScene", "fit worldScale=$factor safeHalf=$safe bodyBase=$bodyBounds bodyFinal=$bodyFinal requiredBelowMeters=$requiredBelowMeters measured=$measured contained=$valid")
    check(valid) { "Managed scene visual bounds exceed the Volume after uniform world fit" }
    return true
}

internal fun Vector3.finitePositive(): Boolean = x.isFinite() && y.isFinite() && z.isFinite() && x > 0 && y > 0 && z > 0

internal fun boundsOf(points: List<Vector3>): BoundingBox {
    require(points.isNotEmpty() && points.all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() })
    val low = Vector3(points.minOf { it.x }, points.minOf { it.y }, points.minOf { it.z })
    val high = Vector3(points.maxOf { it.x }, points.maxOf { it.y }, points.maxOf { it.z })
    return BoundingBox((low + high) * .5f, (high - low) * .5f)
}

internal fun unionBounds(first: BoundingBox, second: BoundingBox): BoundingBox = boundsOf(listOf(first.min, first.max, second.min, second.max))

internal fun BoundingBox.insideHalfExtents(safe: Vector3, epsilon: Float): Boolean =
    min.x >= -safe.x - epsilon && max.x <= safe.x + epsilon &&
        min.y >= -safe.y - epsilon && max.y <= safe.y + epsilon &&
        min.z >= -safe.z - epsilon && max.z <= safe.z + epsilon

/** Normalize imported local visibility once; era/group owners gate presentation. */
internal fun Entity.enableTree() {
    enabled = true
    getChildren().forEach { it.enableTree() }
}

internal fun Entity.modelRootCount(): Int = (if (getName() == "Model") 1 else 0) + getChildren().sumOf { it.modelRootCount() }

internal data class OwnerPose(val position: Vector3, val rotation: com.pico.spatial.core.math.EulerAngles, val scale: Vector3) {
    fun restore(entity: Entity) = entity.transform().apply { position = this@OwnerPose.position; eulerAngles = this@OwnerPose.rotation; scaleVector = this@OwnerPose.scale }
}
internal fun Entity.ownerPose() = transform().let { OwnerPose(it.position, it.eulerAngles, it.scaleVector) }
