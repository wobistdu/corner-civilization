package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.UnlitMaterial

/** Scene-lifetime appearance conversion. Geometry, UVs, skeletons and asset materials stay intact. */
internal class PrimitiveUnlitAppearance(root: Entity) {
    private val replacements = mutableMapOf<Material, UnlitMaterial>()
    private val retainedOriginals = mutableSetOf<Material>()
    private val ownedReplacements = mutableListOf<UnlitMaterial>()
    private val models = mutableListOf<ModelComponent>()

    init {
        try {
            fun visit(entity: Entity) {
                entity.components.get<ModelComponent>()?.let { model ->
                    for (index in 0 until model.materials.size) {
                        val original = model.materials[index]
                        model.materials[index] = when (original) {
                            is UnlitMaterial -> original
                            is PhysicallyBasedMaterial -> replacements.getOrPut(original) {
                                // Retain the source while all of its slots are transferred.
                                original.toGlobal()
                                retainedOriginals += original
                                UnlitMaterial.create(original.getBlendingMode()).apply {
                                    ownedReplacements += this
                                    toGlobal()
                                    setBaseColor(original.getBaseColor())
                                    setOpacity(original.getOpacity())
                                    setOpacityThreshold(original.getOpacityThreshold())
                                    setCullingMode(original.getCullingMode())
                                    setDepthTest(original.getDepthTest())
                                    setDepthWrite(original.getDepthWrite())
                                    setPolygonFillMode(original.getPolygonFillMode())
                                    // The public getter can return a parameter with an invalid empty
                                    // texture; non-null is not evidence that a texture can be bound.
                                    val texture = original.getBaseColorTextureParameter()
                                        .takeIf { it.texture.valid }
                                    if (texture != null) setBaseColorTextureParameter(texture)
                                    setApplyToneMapping(false)
                                    Log.i("PrimitiveAppearance", "unlit node=${entity.getName()} slot=$index textured=${texture != null} blending=${original.getBlendingMode()} source=${original.getName()}")
                                }
                            }
                            else -> error("Unsupported imported appearance ${original.javaClass.name} at ${entity.getName()} slot=$index")
                        }
                    }
                    models += model
                }
                entity.getChildren().forEach(::visit)
            }
            // Include disabled state variants before they become visible, once per scene.
            visit(root)
            check(models.isNotEmpty())
            verify()
            Log.i("PrimitiveAppearance", "ready models=${models.size} sharedReplacements=${replacements.size} realtimeLighting=false")
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    fun verify() = check(models.all { it.materials.all { material -> material is UnlitMaterial } }) {
        "A scene model regained a lit material"
    }

    /** Call only after scene Entities are destroyed; imported textures remain bundle-owned. */
    fun close() {
        ownedReplacements.forEach { it.close() }
        ownedReplacements.clear()
        retainedOriginals.forEach { it.close() }
        retainedOriginals.clear()
        replacements.clear()
        models.clear()
    }
}
