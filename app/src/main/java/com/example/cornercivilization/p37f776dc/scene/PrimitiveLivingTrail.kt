package com.example.cornercivilization.p37f776dc.scene

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

/** Feathered surface details on existing route supports. No light/collider or per-frame allocation. */
internal class PrimitiveLivingTrail(world: Entity, ground: PrimitiveGroundSurface, field: Entity) {
    private val meshes=mutableListOf<MeshResource>()
    private val materials=mutableListOf<UnlitMaterial>()
    private var texture: TextureResource?=null
    private val root=Entity().apply { setName("LivingTrailMemory");world.addChild(this) }
    private val wood=mutableListOf<Entity>()
    private val fieldTrail: Entity
    private var last: PrimitiveTrailPresentation?=null

    init {
        try {
            val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
            try {
                for (y in 0 until 64) for (x in 0 until 64) {
                    val edge=(1f-abs((x-31.5f)/31.5f)).coerceAtLeast(0f).pow(.8f)
                    val grain=((x*17+y*13)%11)/10f
                    bitmap.setPixel(x,y,Color.argb((edge*(180+grain*55)).toInt(),116+(grain*12).toInt(),90+(grain*9).toInt(),53+(grain*7).toInt()))
                }
                texture=TextureResource.create(bitmap).apply { toGlobal() }
            } finally { bitmap.recycle() }
            fun material(opacity: Float)=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
                toGlobal();materials+=this
                setBaseColor(Color4.WHITE);setApplyToneMapping(false)
                setBaseColorTextureParameter(MaterialTextureParameter(requireNotNull(texture)))
                setOpacity(opacity);setDepthTest(true);setDepthWrite(false);setCullingMode(MaterialCullingMode.NONE)
            }
            val routes=(0..1).map { lane ->
                val z=if(lane==0) .1861f else .2239f
                listOf(PrimitiveGroundPoint(-.18f,z),PrimitiveGroundPoint(-.13f,z),PrimitiveGroundPoint(-.088f,.227f+if(lane==0) -.0042f else .0042f))
            }
            fun ribbon(name: String,paths: List<List<PrimitiveGroundPoint>>,halfWidth: Float,mat: UnlitMaterial): Entity {
                val vertices=mutableListOf<Vector3>();val indices=mutableListOf<Int>();val uv=mutableListOf<Vector2>()
                paths.forEach { path ->
                    path.zipWithNext().forEach { (a,b) ->
                        val length=a.distanceTo(b);require(length>.0001f)
                        val nx=-(b.z-a.z)/length;val nz=(b.x-a.x)/length
                        val count=ceil(length/.002f).toInt().coerceAtLeast(1)
                        val start=vertices.size
                        for (step in 0..count) {
                            val t=step.toFloat()/count
                            val cx=a.x+(b.x-a.x)*t;val cz=a.z+(b.z-a.z)*t
                            for ((side,u) in listOf(-1f to 0f,1f to 1f)) {
                                val x=cx+nx*halfWidth*side;val z=cz+nz*halfWidth*side
                                val y=ground.heightAt(x,z)
                                check(y.isFinite())
                                vertices+=Vector3(x,y+.00010f,z);uv+=Vector2(u,t)
                            }
                            if(step>0) {
                                val i=start+(step-1)*2
                                indices+=listOf(i,i+2,i+1,i+1,i+2,i+3)
                            }
                        }
                    }
                }
                check(vertices.size==uv.size && indices.size%3==0 && indices.all { it in vertices.indices })
                val mesh=MeshResource.createWithMeshModel(MeshModel(vertices,indices,uv0=uv),bounds=boundsOf(vertices),name=name).apply { toGlobal();meshes+=this }
                return ModelEntity(mesh,mat).apply { setName(name);root.addChild(this);enabled=false }
            }
            for ((index,opacity) in listOf(.16f,.28f,.40f).withIndex()) {
                wood+=ribbon("WoodTrailWear${index+1}",routes,.0015f+index*.00065f,material(opacity))
            }
            val bounds=field.getVisualBounds(world,recursive=true,enabledOnly=false)
            check(!bounds.isEmpty())
            val left=PrimitiveGroundPoint(bounds.center.x-bounds.halfExtent.x*.45f,bounds.max.z+.004f)
            val right=PrimitiveGroundPoint(bounds.center.x+bounds.halfExtent.x*.45f,bounds.max.z+.004f)
            // Actual fixed farmer corridor only: neither the earlier variable entry nor field interior.
            fieldTrail=ribbon("FieldTrail",listOf(listOf(PrimitiveGroundPoint(.005f,.13f),PrimitiveGroundPoint(.005f,.23f),left,right)),.0018f,materials[1])
            Log.i("LivingTrail","ready woodStages=3 fieldStages=1 triangles=${meshes.sumOf { it.getTriangleCount() }} support=actualDrySamples surfaceOffset=.00010 pathMemory=deliveries realtimeLighting=false")
        } catch (error: Exception) {
            root.destroy(true);close();throw error
        }
    }

    fun apply(state: FireSliceState) {
        val p=PrimitiveTrailPresentation.from(state)
        if(p==last) return
        wood.forEachIndexed { i,e -> e.enabled=p.woodWear==i+1 }
        fieldTrail.enabled=p.fieldVisible
        last=p
        Log.i("LivingTrail","wear=${p.woodWear} deliveries=${state.deliveries} field=${p.fieldVisible} enabledWood=${wood.count { it.enabled }} allocation=none")
    }

    /** Entities are destroyed by the owning scene first. */
    fun close() {
        meshes.forEach { it.close() };meshes.clear()
        materials.forEach { it.close() };materials.clear()
        texture?.close();texture=null
    }
}
