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

/** A single shared color-only mesh derivation. Original SGS file and resource stay untouched. */
internal class PrimitiveTreeBakedAppearance(world: Entity) {
    private val sources=mutableListOf<MeshResource>()
    private val derived=mutableListOf<MeshResource>()
    init {
        try {
            fun models(entity: Entity): List<ModelComponent> = listOfNotNull(entity.components.get<ModelComponent>())+entity.getChildren().flatMap(::models)
            val targets=(listOf("NaturalTreePrototype")+listOf(0,2,5,7,9,11,13,15).map { "NaturalTree_$it" }).flatMap { models(world.requireNode(it)) }
            check(targets.size==9)
            val source=targets.first().mesh
            check(targets.all { it.mesh==source && it.materials.size==1 })
            check(!source.hasSkeleton() && source.getBlendShapeNames().isEmpty() && source.getTriangleCount()==14732)
            source.toGlobal();sources+=source
            val positions=source.getPositions().toList().chunked(3).map { Vector3(it[0],it[1],it[2]) }
            val normals=requireNotNull(source.getNormals()).toList().chunked(3).map { Vector3(it[0],it[1],it[2]) }
            val indices=source.getTriangleIndices().toList()
            val uv=source.getUv0()?.toList()?.chunked(2)?.map { Vector2(it[0],it[1]) }
            val uv1=source.getUv1()?.toList()?.chunked(2)?.map { Vector2(it[0],it[1]) }
            val tangents=source.getTangents()?.toList()?.chunked(4)?.map { Vector4(it[0],it[1],it[2],it[3]) }
            val originalColors=source.getColors()?.toList()?.chunked(4)?.map { c -> Color4((c[0].toInt() and 255)/255f,(c[1].toInt() and 255)/255f,(c[2].toInt() and 255)/255f,(c[3].toInt() and 255)/255f) }
            check(positions.size==normals.size && (originalColors==null || originalColors.size==positions.size))
            val colors=normals.mapIndexed { i,n ->
                val multiplier=PrimitiveBakedShade.factor(n.x,n.y,n.z)
                (originalColors?.get(i) ?: Color4.WHITE).adjustBrightness(multiplier)
            }
            val mesh=MeshResource.createWithMeshModel(MeshModel(positions,indices,normals,tangents,uv,uv1,colors),bounds=source.getBoundingBox(),name="NaturalTreeFixedDepth").apply { toGlobal();derived+=this }
            check(mesh.getTriangleCount()==source.getTriangleCount() && mesh.getVertexCount()==source.getVertexCount() && mesh.hasColors())
            targets.forEach { it.mesh=mesh }
            Log.i("TreeBakedDepth","ready instances=${targets.size} derivedMeshes=1 sourceTriangles=14732 geometryPreserved=true texturePreserved=true fixedMultiplier=.53..1 realtimeLighting=false")
        } catch(error: Exception) { close();throw error }
    }
    /** Scene destroys all instances before releasing globals. */
    fun close() { derived.forEach { it.close() };derived.clear();sources.forEach { it.close() };sources.clear() }
}
