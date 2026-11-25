package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.EnvironmentLightingSettingsComponent
import com.pico.spatial.core.ecs.ImageBasedLightComponent
import com.pico.spatial.core.ecs.ImageBasedLightReceiverComponent
import com.pico.spatial.core.ecs.ImageBasedLightSource
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ParticleComponent
import com.pico.spatial.core.ecs.resource.TextureResource

/** One application-owned light map; system environment contributes zero to sandbox geometry. */
internal class PrimitiveFixedLighting(owner: Entity, root: Entity, texture: TextureResource) {
    private val source = Entity().apply {
        setName("PrimitiveFixedDaylight")
        components.set(ImageBasedLightComponent(ImageBasedLightSource.Single(texture), 8f))
        owner.addChild(this)
    }
    private val receivers = mutableListOf<Entity>()

    init {
        fun bind(entity: Entity) {
            if (entity.components.get<ModelComponent>() != null || entity.components.get<ParticleComponent>() != null) {
                entity.components.set(ImageBasedLightReceiverComponent(source))
                entity.components.set(EnvironmentLightingSettingsComponent(0f))
                receivers += entity
            }
            entity.getChildren().forEach(::bind)
        }
        // Bind disabled renderers too: canopy, carried wood, droplets and fire may become visible later.
        bind(root)
        check(receivers.isNotEmpty())
        verify()
        Log.i("PrimitiveLighting", "fixedIBL ready receivers=${receivers.size} environmentScale=0 intensityExponent=8 cubeFace=128 source=miniature-daylight.hdr")
    }

    fun verify() {
        check(receivers.all {
            it.components.get<EnvironmentLightingSettingsComponent>()?.scale == 0f &&
                it.components.get<ImageBasedLightReceiverComponent>()?.source?.id == source.id
        }) { "Sandbox fixed IBL binding changed" }
    }
}
