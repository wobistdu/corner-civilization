package com.example.cornercivilization.p37f776dc.scene

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.particle.*
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Three independently owned native particle patches; no shared emission timer or index-based relocation. */
internal class PrimitiveFireParticles(private val root:Entity) {
    private fun texture(flame:Boolean):TextureResource {
        val bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888)
        for(y in 0..95)for(x in 0..95) {
            val nx=(x-47.5f)/47.5f;val ny=(y-47.5f)/47.5f
            val width=if(flame) .48f+.46f*((ny+1f)/2f) else 1f
            val bent=if(flame)nx+.12f*sin(ny*5f)else nx
            val d=sqrt(bent*bent/(width*width)+ny*ny)
            val alpha=((1f-d).coerceIn(0f,1f).pow(if(flame)1.15f else 1.9f)*255).toInt()
            bitmap.setPixel(x,y,Color.argb(alpha,255,255,255))
        }
        return try{TextureResource.create(bitmap).apply{toGlobal()}}finally{bitmap.recycle()}
    }
    private val flameTexture=texture(true)
    private val softTexture=texture(false)
    private data class Layer(val entity:Entity,val component:ParticleComponent,val rate:Float,val smoke:Boolean)
    private data class Patch(val entity:Entity,val layers:List<Layer>)
    private val patches=mutableMapOf<Int,Patch>()
    private var active=true
    private fun random(a:Float,b:Float)=ParticleFloatVaryingProperty(ParticleVaryingPropertyType.RANDOM,a,b)
    private fun layer(owner:Entity,name:String,index:Int,spread:Float):Layer {
        val smoke=index==2;val spark=index==1
        val e=Entity().apply{setName(name);owner.addChild(this)}
        val pc=ParticleComponent();e.components.set(pc)
        val rate=if(smoke)12f else if(spark)12f else 85f
        pc.apply {
            isEmitting=true;isLoop=true;isMainSimulationEnabled=true;isSubSimulationEnabled=false
            birthRateFull=ParticleFloatVaryingProperty(value=rate)
            emissionDuration=ParticleFloatProperty(value=30f);idleDuration=ParticleFloatProperty(value=0f)
            warmupDuration=0f
            emitterShape=ParticleEmitterShape(ParticleEmitterShapeType.BOX,Vector3(spread,.001f,spread),
                ParticleEmissionLocation.VOLUME,Int3(1,1,1),ParticleEmissionSpace.LOCAL,Vector3(0f,1f,0f),360f,0f)
        }
        pc.mainSimulation.apply {
            maxParticleCount=if(smoke)24 else if(spark)16 else 96
            simulationSpace=ParticleSpace.LOCAL;descriptionSpace=ParticleSpace.LOCAL
            cullingBehaviorMode=ParticleCullingBehaviorMode.ALWAYS_SIMULATE
            particleType=ParticleType.BILLBOARD;facingMode=ParticleFacingMode.CAMERA_POSITION
            orientationMode=ParticleOrientationMode.DEFAULT;sortOrder=ParticleSortOrder.INCREASING_AGE
            isVisible=true;isMotionEnabled=true;isCameraFadeEnabled=false;isCollisionEnabled=false
            isNoiseEnabled=true;noiseFrequency=2;noiseOctaves=2;noiseScale=.018f
            noiseStrength=if(smoke).0015f else .0007f;noiseAnimationSpeed=.8f;noiseSeed=index+17
            lifetime=if(smoke)random(1f,1.6f)else if(spark)random(.4f,.75f)else random(.45f,.85f)
            startSize=if(smoke)random(.006f,.011f)else if(spark)random(.00055f,.001f)else random(.022f,.032f)
            startSpeed=if(smoke)random(.012f,.022f)else if(spark)random(.035f,.055f)else random(.014f,.026f)
            acceleration=Vector3(0f,if(spark)-.018f else .004f,0f)
            emitSpreadingAngle=if(spark)20f else 8f
            startRotation=if(smoke)random(0f,360f)else random(-12f,12f)
            isSizeCurveEnabled=true
            listOf(sizeCurveX,sizeCurveY,sizeCurveZ).forEach {it.curveType=ParticleCurveType.LINEAR;it.start=if(smoke).7f else 1f;it.end=if(smoke)1.8f else .15f}
            isColorGradientEnabled=true
            colorGradient=ParticleColorGradient(
                if(smoke)Color4(.24f,.23f,.21f,.22f)else if(spark)Color4(1f,.65f,.12f,1f)else Color4(1f,.40f,.035f,.95f),
                if(smoke)Color4(.39f,.37f,.34f,0f)else Color4(.65f,.025f,.002f,0f))
            material.apply {
                materialType=ParticleMaterialType.DEFAULT_UNLIT
                blendingMode=if(smoke)ParticleBlendingMode.ALPHA else ParticleBlendingMode.ADDITIVE
                colorMode=ParticleColorMode.MULTIPLY;baseColor=Color4.WHITE
                baseColorMap=if(index==0)flameTexture else softTexture
            }
        }
        return Layer(e,pc,rate,smoke)
    }
    fun render(fires:List<LocalInterventionWorld.Hazard>, buildings:Map<String,BoundingBox>) {
        check(fires.size<=3)
        val ids=fires.map{it.id}.toSet()
        patches.keys.filter{it !in ids}.toList().forEach{patches.remove(it)?.entity?.destroy(true)}
        for(f in fires) {
            val patch=patches.getOrPut(f.id) {
                val owner=Entity().apply{setName("NativeFire_${f.id}");root.addChild(this)}
                val building=f.house?.let{buildings[it]}
                val spread=building?.let{minOf(.045f,maxOf(it.size.x,it.size.z)*.7f)}?:.028f
                owner.transform().position=Vector3(f.point.x,if(building!=null)building.max.y-.001f else f.y+.001f,f.point.z)
                Log.i("NativeFire","created id=${f.id} house=${f.house} emitters=3 maxParticles=136 local=true unlit=true")
                Patch(owner,listOf(layer(owner,"FireTongues",0,spread),layer(owner,"FireSparks",1,spread*.8f),layer(owner,"FireSmoke",2,spread*.6f)))
            }
            val currentRoof=f.house?.let{buildings[it]}
            patch.entity.transform().position=Vector3(f.point.x,if(currentRoof!=null)currentRoof.max.y-.001f else f.y+.001f,f.point.z)
            patch.layers.forEach {l->
                // Stop emission early enough that the last particle dies before the slot is retired at 18s.
                val fade=if(l.smoke)((18f-f.age-1.6f)/1.4f).coerceIn(0f,1f)else ((18f-f.age-.85f)/2.15f).coerceIn(0f,1f)
                l.component.isEmitting=active && fade>0f
                if(l.smoke)l.component.mainSimulation.colorGradient=if(f.heat<.8f)ParticleColorGradient(Color4(.74f,.78f,.79f,.35f),Color4(.8f,.82f,.83f,0f)) else ParticleColorGradient(Color4(.24f,.23f,.21f,.22f),Color4(.39f,.37f,.34f,0f))
                l.component.birthRateFull=ParticleFloatVaryingProperty(value=l.rate*fade*(if(l.smoke&&f.heat<.8f)1f else f.heat))
            }
        }
    }
    fun setActive(value:Boolean){active=value;patches.values.forEach{p->p.entity.enabled=value}}
    fun close(){flameTexture.close();softTexture.close()}
    companion object {const val MAX_TRIANGLES=3*(96+16+24)*2}
}
