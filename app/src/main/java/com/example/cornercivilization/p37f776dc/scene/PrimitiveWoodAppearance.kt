package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.Color4

/** Explicit scene-owned albedo binding for stock, dropped, carried and stacked wood. */
internal class PrimitiveWoodAppearance(world: Entity) {
    private fun barkAtlas(): TextureResource {
        val bitmap=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888)
        for(y in 0..511)for(x in 0..511) {
            val noise=sin(x*12.9898+y*78.233)*43758.5453;val grain=noise-floor(noise)
            val color=if(x<321) {
                val ridge=sin(x*.26+sin(y*.045)*.8)+.45*sin(x*.91+sin(y*.017))
                val fissure=if(ridge<-.65) .45 else 1.0
                val shade=(.72+ridge*.12+grain*.20)*fissure
                Color.rgb((88*shade).toInt(),(59*shade).toInt(),(34*shade).toInt())
            }else if(x<366) {
                val twist = sin(y * (2.0 * PI * 13.0 / 512.0) + (x-328)*.30)
                val shade = .86 + .12*twist + grain*.05
                Color.rgb((216*shade).toInt(),(174*shade).toInt(),(108*shade).toInt())
            }else {
                val dx=(x-438f)/64f;val dy=(y-256f)/225f;val radius=sqrt(dx*dx+dy*dy)
                val ring=sin(radius*31+sin(atan2(dy,dx)*3)*.8);val shade=.83+.12*ring+grain*.06
                Color.rgb((214*shade).toInt(),(159*shade).toInt(),(101*shade).toInt())
            }
            bitmap.setPixel(x,y,color)
        }
        return try{TextureResource.create(bitmap).apply{toGlobal()}}finally{bitmap.recycle()}
    }
    private val texture = barkAtlas()
    private val material = UnlitMaterial.create().apply { toGlobal(); setBaseColor(Color4.WHITE); setApplyToneMapping(false) }
    init {
        try {
            check(texture.valid) { "Wood color texture failed to load" }
            material.setBaseColorTextureParameter(MaterialTextureParameter(texture))
            var count = 0
            fun bind(e: Entity, woodOwner: Boolean = false) {
                val wood = woodOwner || e.getName() in listOf("Resources_0", "Resources_1") ||
                    e.getName().startsWith("PendingWood_") || e.getName().startsWith("StoredWood_") || e.getName().startsWith("DroppedWood_") || e.getName().endsWith("_CarriedWood")
                if (wood) e.components.get<ModelComponent>()?.let { model ->
                    check(model.mesh.getUv0()?.isNotEmpty() == true) { "Wood lost original UVs: ${e.getName()}" }
                    for (slot in 0 until model.materials.size) model.materials[slot] = material
                    count++
                }
                e.getChildren().forEach { bind(it, wood) }
            }
            bind(world)
            check(count >= 11) { "Missing dropped/carried wood appearances: $count" }
            check(material.getBaseColorTextureParameter().texture.valid)
            Log.i("WoodAppearance", "texturedInstances=$count textureValid=true source=original-procedural-bark-endgrain bundleLength=$BUNDLE_LENGTH_METRES")
        } catch (error: Exception) { close(); throw error }
    }
    fun close() { material.close(); texture.close() }
    companion object {
        // A portable three-log bundle; shared by ground, hand and depot instances.
        const val BUNDLE_LENGTH_METRES = .009f
    }
}
