package com.example.cornercivilization.p37f776dc.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import kotlin.random.Random

/** A single scene-owned painted albedo. No lights, mesh changes, or per-frame texture work. */
internal class PrimitiveGroundAppearance(world: Entity, bitmap: Bitmap) {
    private val texture: TextureResource
    private val material: UnlitMaterial

    init {
        val canvas = Canvas(bitmap)
        val brush = Paint(Paint.ANTI_ALIAS_FLAG)
        fun contact(name: String) {
            val bounds = world.requireNode(if (name.startsWith("Tree_")) name.replace("Tree_", "NaturalTree_") else name).getVisualBounds(world, recursive = true, enabledOnly = true)
            check(!bounds.isEmpty())
            val cx = px(bounds.center.x, bitmap.width)
            val cy = py(bounds.center.z, bitmap.height)
            val tree=name.startsWith("Tree_")
            val footprint=if(tree).12f else .65f
            val rx = (bounds.halfExtent.x * footprint / WIDTH * bitmap.width).coerceAtLeast(2f)
            val ry = (bounds.halfExtent.z * footprint / DEPTH * bitmap.height).coerceAtLeast(2f)
            val save = canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(rx, ry)
            brush.shader = RadialGradient(0f, 0f, 1.45f,
                intArrayOf(Color.argb(32, 86, 94, 48), Color.argb(12, 96, 104, 55), Color.TRANSPARENT),
                floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            canvas.drawCircle(0f, 0f, 1.45f, brush)
            canvas.restoreToCount(save)
            brush.shader = null
        }
        listOf("Shelter_0", "Shelter_1", "Shelter_2", "Resources_1", "Campfire",
            "Tree_0", "Tree_2", "Tree_5", "Tree_7", "Tree_9", "Tree_11", "Tree_13", "Tree_15").forEach(::contact)
        material = UnlitMaterial.create().apply { toGlobal(); setBaseColor(Color4.WHITE); setApplyToneMapping(false) }
        var allocatedTexture: TextureResource? = null
        try {
            texture = TextureResource.create(bitmap).apply { toGlobal(); allocatedTexture = this }
            material.setBaseColorTextureParameter(MaterialTextureParameter(texture))
            val ground = requireNotNull(world.requireNode("FloodplainTerrain").components.get<ModelComponent>())
            for (index in 0 until ground.materials.size) ground.materials[index] = material
            Log.i("PrimitiveGround", "paintedAlbedo ready size=${bitmap.width}x${bitmap.height} contactMarks=13 meshUnchanged=true realtimeLighting=false")
        } catch (error: Exception) {
            material.close()
            allocatedTexture?.close()
            throw error
        } finally {
            bitmap.recycle()
        }
    }

    fun close() { material.close(); texture.close() }

    companion object {
        // Verified V5 exporter world-plan UV: u=(x+W/2)/W, v=(-z+D/2)/D.
        private const val WIDTH = .938f
        private const val DEPTH = .704f
        private fun px(x: Float, size: Int) = (x / WIDTH + .5f) * size
        private fun py(z: Float, size: Int) = (z / DEPTH + .5f) * size

        /** Pure image work; call on IO before one-shot Entity initialization. */
        fun prepare(context: Context): Bitmap {
            val original = context.assets.open("gameplay/ground-base.png").use(BitmapFactory::decodeStream)
            check(original != null && original.width == 2048 && original.height == 2048)
            val bitmap = original.copy(Bitmap.Config.ARGB_8888, true)
            original.recycle()
            try {
                val grass = context.assets.open("gameplay/grass-surface-v6.png").use(BitmapFactory::decodeStream)
                check(grass != null)
                try {
                    val surface = IntArray(grass.width * grass.height)
                    grass.getPixels(surface, 0, grass.width, 0, 0, grass.width, grass.height)
                    val pixels = IntArray(bitmap.width * bitmap.height)
                    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                    // Mirrored sampling avoids hard tile boundaries; the original color mask protects water/bare soil.
                    fun mirrored(value: Int, limit: Int): Int {
                        val p = value % (limit * 2)
                        return if (p < limit) p else limit * 2 - 1 - p
                    }
                    for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                        val index = y * bitmap.width + x
                        val base = pixels[index]
                        val r = Color.red(base); val g = Color.green(base); val b = Color.blue(base)
                        val greenMask = minOf((g-r*1.04f)/12f, (g-b*1.25f)/16f).coerceIn(0f,1f)
                        if (greenMask <= 0f) continue
                        val sx = mirrored(x * grass.width * 3 / bitmap.width, grass.width)
                        val sy = mirrored(y * grass.height * 3 / bitmap.height, grass.height)
                        val detail = surface[sy * grass.width + sx]
                        val amount = .60f * greenMask
                        fun mix(old: Int, fine: Int) = (old*(1f-amount)+fine*amount).toInt().coerceIn(0,255)
                        pixels[index] = Color.argb(Color.alpha(base), mix(r,Color.red(detail)), mix(g,Color.green(detail)), mix(b,Color.blue(detail)))
                    }
                    bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                } finally { grass.recycle() }
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
            val canvas = Canvas(bitmap)
            val brush = Paint(Paint.ANTI_ALIAS_FLAG)
            val random = Random(30741)
            // Tiny dry/green blades, never painted across the blue/neutral river bank.
            repeat(95000) {
                val x = random.nextInt(bitmap.width)
                val y = random.nextInt(bitmap.height)
                val color = bitmap.getPixel(x, y)
                if (Color.green(color) > Color.red(color) * 1.04f && Color.green(color) > Color.blue(color) * 1.25f) {
                    val warm = random.nextInt(3) == 0
                    brush.color = if (warm) Color.argb(58, 187, 165, 93) else Color.argb(64, 57, 84, 34)
                    brush.strokeWidth = .6f + random.nextFloat() * .7f
                    val h = 1.2f + random.nextFloat() * 3.5f
                    canvas.drawLine(x.toFloat(), y.toFloat(), x + random.nextFloat() * 2f - 1f, y - h, brush)
                    brush.color = Color.argb(30, 215, 200, 136)
                    canvas.drawPoint(x.toFloat(), y-h, brush)
                }
            }
            // Pebbles/leaf litter on exposed earth only: small texture cues, not fake 3D props.
            repeat(7500) {
                val x = random.nextInt(bitmap.width)
                val y = random.nextInt(bitmap.height)
                val color = bitmap.getPixel(x,y)
                if (Color.red(color) > Color.green(color) * 1.05f && Color.red(color) > Color.blue(color) * 1.25f) {
                    val radius = .6f + random.nextFloat() * 1.7f
                    brush.color = Color.argb(55, 57, 47, 30)
                    canvas.drawOval(x-radius, y-radius*.55f, x+radius, y+radius*.55f, brush)
                    brush.color = Color.argb(80, 197, 176, 121)
                    canvas.drawOval(x-radius*.8f, y-radius*.7f, x+radius*.6f, y.toFloat(), brush)
                }
            }
            return bitmap
        }
    }
}
