package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4
import org.json.JSONObject
import kotlin.math.*

/** App-owned presentation of the already loaded SGS mesh. The managed bundle is immutable. */
internal class PrimitiveFireRuntime(private val world: Entity, centroidJson: JSONObject) {
    private val camp = world.requireNode("Campfire")
    private val native = camp.requireNode("NativeModel")
    private val original = requireNotNull(native.components.get<ModelComponent>())
    private val meshes = mutableListOf<MeshResource>()
    private val flame: Entity
    private val flameBottom: Float
    private val waterMaterial = UnlitMaterial.create().apply { setBaseColor(Color4(.25f, .65f, .95f, 1f)) }
    private val waterMesh = MeshResource.createSphere(.001f).also { meshes += it }
    private val drops = List(6) { index ->
        ModelEntity(waterMesh, waterMaterial).apply { setName("FireWater_$index"); world.addChild(this) }
    }
    private val target = Entity().apply { setName("FireInteraction"); world.addChild(this) }
    val center: Vector3
    val emissionCenter: Vector3
    private var lastSequence = -1L
    private var splashAt = -10_000L
    private var lastPhase: FirePhase? = null
    var rainRoofY: Float = 0f

    init {
        val source = original.mesh
        check(source.getTriangleCount() == centroidJson.getInt("sourceTriangles")) { "Fire source topology changed" }
        // Pinned USD binds /Mesh/Materials/Material_0 both to /Mesh and its
        // 994-face Mesh_0 subset. The importer exposes both identical bindings.
        check(original.materials.size in 1..2 && !source.hasSkeleton()) {
            "Fire source unsupported: materials=${original.materials.size}, skeleton=${source.hasSkeleton()}"
        }
        val positions = source.getPositions().toList().chunked(3).map { Vector3(it[0], it[1], it[2]) }
        val normals = source.getNormals()?.toList()?.chunked(3)?.map { Vector3(it[0], it[1], it[2]) }
        val tangents = source.getTangents()?.toList()?.chunked(4)?.map { Vector4(it[0], it[1], it[2], it[3]) }
        val uv = source.getUv0()?.toList()?.chunked(2)?.map { Vector2(it[0], it[1]) }
        val uv1 = source.getUv1()?.toList()?.chunked(2)?.map { Vector2(it[0], it[1]) }
        val colors = source.getColors()?.toList()?.chunked(4)?.map {
            Color4((it[0].toInt() and 255)/255f, (it[1].toInt() and 255)/255f, (it[2].toInt() and 255)/255f, (it[3].toInt() and 255)/255f)
        }
        val list = centroidJson.getJSONArray("centroids")
        val centroids = List(list.length()) { index -> list.getJSONArray(index).let { Vector3(it.getDouble(0).toFloat(), it.getDouble(1).toFloat(), it.getDouble(2).toFloat()) } }
        val flameIndices = mutableListOf<Int>()
        val baseIndices = mutableListOf<Int>()
        for (triangle in source.getTriangleIndices().toList().chunked(3)) {
            val c = (positions[triangle[0]] + positions[triangle[1]] + positions[triangle[2]]) * (1f / 3f)
            val isFlame = centroids.any { abs(c.x-it.x) < .000002f && abs(c.y-it.y) < .000002f && abs(c.z-it.z) < .000002f }
            (if (isFlame) flameIndices else baseIndices).addAll(triangle)
        }
        check(flameIndices.size / 3 == centroidJson.getInt("flameTriangles")) { "Native fire mesh does not match source segmentation: ${flameIndices.size / 3}" }
        fun part(name: String, indices: List<Int>): Entity {
            val used = indices.distinct().sorted()
            val remap = used.withIndex().associate { it.value to it.index }
            val model = MeshModel(used.map { positions[it] }, indices.map { remap.getValue(it) },
                normals?.let { n -> used.map { n[it] } }, tangents?.let { t -> used.map { t[it] } }, uv?.let { u -> used.map { u[it] } }, uv1?.let { u -> used.map { u[it] } }, colors?.let { c -> used.map { c[it] } })
            val mesh = MeshResource.createWithMeshModel(model, name = name).also { meshes += it }
            return ModelEntity(mesh, original.materials[0]).apply { setName(name); native.addChild(this) }
        }
        part("RuntimeHearthBase", baseIndices)
        flame = part("RuntimeHearthFlame", flameIndices)
        flameBottom = flameIndices.minOf { positions[it].y }
        original.isVisible = false
        val bounds = camp.getVisualBounds(world, recursive = true, enabledOnly = true)
        center = Vector3(bounds.center.x, bounds.min.y, bounds.center.z)
        // Newly attached generated mesh bounds can lag their native commit. Use the
        // verified source vertices and the imported owner's coordinate conversion.
        val flamePoints = flameIndices.distinct().map { native.convertPositionTo(positions[it], world) }
        emissionCenter = Vector3(center.x, flamePoints.minOf { it.y }, center.z)
        target.transform().position = center + Vector3(0f, .01f, 0f)
        target.installBoxTarget(TARGET_SIZE, Vector3.ZERO)
        // Place all effect instances at their maximum height for the initial common fit.
        drops.forEachIndexed { i, drop -> drop.transform().position = center + Vector3((i-2.5f)*.002f, .03f, 0f) }
        Log.i("PrimitiveFire", "native-partition base=${baseIndices.size/3} flame=${flameIndices.size/3} source=${source.getTriangleCount()} materialSlots=${original.materials.size} colors=${source.hasColors()} waterPeak=${waterMesh.getTriangleCount()*6} peakSceneTriangles=${309682+waterMesh.getTriangleCount()*6} center=$center")
    }

    /** Read-only physics audit; never invokes a tool or mutates the world state. */
    fun auditHitTargets(): Boolean {
        val scene = world.scene ?: return false
        val collider = target.components.get<com.pico.spatial.core.ecs.CollisionComponent>()
        val interactable = target.components.get<com.pico.spatial.core.ecs.InteractableComponent>() != null
        val center = target.transform().position
        val axes = listOf(Vector3(1f,0f,0f),Vector3(-1f,0f,0f),Vector3(0f,1f,0f),Vector3(0f,-1f,0f),Vector3(0f,0f,1f),Vector3(0f,0f,-1f))
        var centerHits = 0
        var nearestHits = 0
        for ((index, axis) in axes.withIndex()) {
            val hits = scene.rayCast(center + axis * .06f, axis * -1f, .12f,
                com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL,
                com.pico.spatial.core.ecs.simulation.CollisionGroup(com.pico.spatial.core.ecs.simulation.CollisionGroup.COLLISION_GROUP_DEFAULT), world).results
            val hit = hits.firstOrNull { it.entity == target }
            if (hit != null) centerHits++
            val nearest = hits.minByOrNull { it.distance }
            if (nearest?.entity == target) nearestHits++
            Log.i("HearthHitAudit", "axis=$index targetHit=${hit != null} distance=${hit?.distance} nearest=${nearest?.entity?.getName()} reference=PrimitiveWorld input=programmatic stateMutation=false")
        }
        var outsideMisses = 0
        for (side in listOf(-1f,1f)) {
            val origin = center + Vector3(side * (TARGET_SIZE.x/2f+.005f), .06f, 0f)
            val hits = scene.rayCast(origin, Vector3(0f,-1f,0f), .12f,
                com.pico.spatial.core.ecs.simulation.CollisionCastHitMode.ALL,
                com.pico.spatial.core.ecs.simulation.CollisionGroup(com.pico.spatial.core.ecs.simulation.CollisionGroup.COLLISION_GROUP_DEFAULT), world).results
            if (hits.none { it.entity == target }) outsideMisses++
        }
        Log.i("HearthHitAudit", "status=${if(centerHits==6 && outsideMisses==2 && collider != null && interactable) "PASS" else "FAIL"} centers=$centerHits/6 outsideMisses=$outsideMisses/2 nearest=$nearestHits/6 collisionShapes=${collider?.collisionShape?.size} interactable=$interactable center=$center targetSize=$TARGET_SIZE reference=PrimitiveWorld epsilon=.005 physicalResponse=not-tested userInput=UNOBSERVED")
        return true
    }

    fun matches(hit: Entity?): Boolean = hit?.logicalTarget(mapOf(target.id to "hearth", camp.id to "hearth")) == "hearth"

    fun apply(state: FireSliceState) {
        if (lastSequence < 0) lastSequence = state.waterSequence // Never replay an old splash after loading.
        if (state.waterSequence != lastSequence) { splashAt = state.activeMillis; lastSequence = state.waterSequence }
        // Keep the existing 104-triangle SGS core readable while native particle
        // visibility remains an open emulator finding. Both follow the same state.
        flame.enabled = state.strength > 0
        val pulse = .92f + .06f * sin(state.activeMillis / 137f) + .02f * sin(state.activeMillis / 53f)
        val strength = (if (state.strength > 55) 1f else .45f) * pulse
        flame.transform().apply {
            scaleVector = Vector3(1f, strength, 1f)
            position = Vector3(0f, flameBottom * (1f-strength), 0f)
        }
        val elapsed = (state.activeMillis - splashAt) / 1000f
        drops.forEachIndexed { i, drop ->
            val t = elapsed - i * .055f
            drop.enabled = t in 0f.. .55f
            val floor = if (state.shelter == ShelterPhase.COMPLETE) rainRoofY-center.y else .002f
            drop.transform().position = center + Vector3((i-2.5f)*.0018f, floor + (.025f*(1f-t/.55f)).coerceIn(0f,.025f), (i%2-.5f)*.004f)
        }
        if (lastPhase != state.phase) {
            Log.i("PrimitiveFire", "phase=${state.phase} strength=${state.strength} learned=${state.knowsFire} ignitions=${state.ignitions} activeMillis=${state.activeMillis}")
            lastPhase = state.phase
        }
    }

    /** Call after the owning entity tree has been destroyed. Imported material belongs to its bundle. */
    fun closeResources() { meshes.forEach { it.close() }; waterMaterial.close() }

    companion object { private val TARGET_SIZE = Vector3(.035f,.025f,.035f) }
}

/** Focused app-side size policy, grounded around measured bottoms in each immediate parent. */
internal fun miniaturizeProps(world: Entity) {
    for (name in listOf("Shelter_0", "Shelter_1", "Shelter_2", "Campfire", "Resources_0", "Resources_1")) {
        scaleGrounded(world.requireNode(name), .35f)
    }
    for (index in 0..15) scaleGrounded(world.requireNode("Tree_$index"), .7f)
}

internal fun scaleGrounded(entity: Entity, factor: Float) {
        val owner = requireNotNull(entity.getParent())
        val before = entity.getVisualBounds(owner, recursive = true, enabledOnly = true)
        check(!before.isEmpty() && before.size.finitePositive()) { "Cannot scale missing native bounds: ${entity.getName()} bounds=$before" }
        entity.transform().scaleVector = entity.transform().scaleVector * factor
        val scaled = entity.getVisualBounds(owner, recursive = true, enabledOnly = true)
        check(!scaled.isEmpty() && scaled.size.finitePositive()) { "Scaled native bounds unavailable: ${entity.getName()} bounds=$scaled" }
        entity.transform().position += Vector3(before.center.x-scaled.center.x, before.min.y-scaled.min.y, before.center.z-scaled.center.z)
        val after = entity.getVisualBounds(owner, recursive = true, enabledOnly = true)
        check(!after.isEmpty() && after.size.finitePositive() && abs(after.min.y-before.min.y) < .00001f)
        Log.i("PrimitiveFire", "miniature-size name=${entity.getName()} factor=$factor before=$before after=$after bottomPreserved=true")
    }
