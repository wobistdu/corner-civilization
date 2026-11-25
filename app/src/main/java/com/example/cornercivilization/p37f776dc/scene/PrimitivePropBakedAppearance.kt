package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.core.math.Vector4

/** Fixed surface-color bake for existing props only. No lights, animation or material replacement. */
internal class PrimitivePropBakedAppearance(world: Entity) {
    private val sources = mutableListOf<MeshResource>()
    private val derived = mutableListOf<MeshResource>()
    private val assignments = mutableListOf<Pair<ModelComponent, MeshResource>>()

    init {
        try {
            fun models(entity: Entity): List<ModelComponent> =
                listOfNotNull(entity.components.get<ModelComponent>()) + entity.getChildren().flatMap(::models)
            val roots = listOf("Shelter_0", "Shelter_1", "Shelter_2", "RainShelter",
                "Resources_0", "Resources_1", "FaunaLibrary")
            val seedMeshes = roots.flatMap { models(world.requireNode(it)) }.map { it.mesh }.distinct()
            // Include disabled stock and carried instances, but never choose resident geometry.
            val allModels = models(world)
            var changed = 0
            for (source in seedMeshes) {
                val targets = allModels.filter { it.mesh == source }
                val reason = when {
                    source.hasSkeleton() -> "skinned"
                    source.getBlendShapeNames().isNotEmpty() -> "blendshape"
                    targets.any { it.materials.size != 1 } -> "material-slot-mapping-unverified"
                    source.getVertexCount() <= 0 || source.getTriangleCount() <= 0 -> "empty-geometry"
                    else -> null
                }
                if (reason != null) {
                    Log.w(TAG, "preserved instances=${targets.size} reason=$reason slots=${targets.map { it.materials.size }.distinct()}")
                    continue
                }
                val count = source.getVertexCount()
                val normalData = source.getNormals()
                if (normalData == null || normalData.size != count * 3) {
                    Log.w(TAG, "preserved instances=${targets.size} reason=normal-data-unavailable")
                    continue
                }
                source.toGlobal()
                sources += source
                val positions = source.getPositions().toList().chunked(3).map { Vector3(it[0], it[1], it[2]) }
                val normals = normalData.toList().chunked(3).map { Vector3(it[0], it[1], it[2]) }
                val indices = source.getTriangleIndices().toList()
                val uv0 = source.getUv0()?.toList()?.chunked(2)?.map { Vector2(it[0], it[1]) }
                val uv1 = source.getUv1()?.toList()?.chunked(2)?.map { Vector2(it[0], it[1]) }
                val tangents = source.getTangents()?.toList()?.chunked(4)?.map { Vector4(it[0], it[1], it[2], it[3]) }
                val originalColors = source.getColors()?.toList()?.chunked(4)?.map {
                    Color4((it[0].toInt() and 255) / 255f, (it[1].toInt() and 255) / 255f,
                        (it[2].toInt() and 255) / 255f, (it[3].toInt() and 255) / 255f)
                }
                check(positions.size == count && indices.size == source.getTriangleCount() * 3)
                check(originalColors == null || originalColors.size == count)
                val colors = normals.mapIndexed { index, n ->
                    (originalColors?.get(index) ?: Color4.WHITE)
                        .adjustBrightness(PrimitiveBakedShade.factor(n.x, n.y, n.z))
                }
                val data = MeshModel(positions, indices, normals, tangents, uv0, uv1, colors)
                MeshModel.validateOrThrow(data)
                val mesh = MeshResource.createWithMeshModel(data, source.getBoundingBox(), "CampPropFixedDepth_${derived.size}")
                mesh.toGlobal()
                derived += mesh
                check(mesh.getTriangleCount() == source.getTriangleCount() && mesh.getVertexCount() == count)
                check(mesh.getPositions().contentEquals(source.getPositions()) && mesh.getTriangleIndices().contentEquals(source.getTriangleIndices()))
                targets.forEach {
                    assignments += it to source
                    it.mesh = mesh
                }
                changed += targets.size
                Log.i(TAG, "mesh=${derived.size} instances=${targets.size} vertices=$count triangles=${mesh.getTriangleCount()} geometryPreserved=true materialSlotsPreserved=true")
            }
            Log.i(TAG, "status=${if (changed > 0) "APPLIED" else "UNCHANGED"} instances=$changed derivedMeshes=${derived.size} sourceFilesUntouched=true texturesPreserved=true realtimeLighting=false")
        } catch (error: Exception) {
            // Constructor failure must not leave any entity holding a released derivative.
            assignments.asReversed().forEach { (component, original) -> component.mesh = original }
            close()
            throw error
        }
    }

    /** The owning runtime destroys all entities first, then releases explicit globals. */
    fun close() {
        assignments.clear()
        derived.forEach { it.close() }
        derived.clear()
        sources.forEach { it.close() }
        sources.clear()
    }

    companion object { private const val TAG = "PropBakedDepth" }
}
