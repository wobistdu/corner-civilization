package com.example.cornercivilization.p37f776dc.scene
import android.graphics.Bitmap
import android.graphics.Color
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.particle.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Soft cloud lobes, translucent native rain streaks and short-lived surface splashes. */
internal class PrimitiveRainParticles(private val root:Entity) {
    private fun cloudMesh():MeshResource {
        // Explicit topology keeps each cloud lobe at 440 triangles, independent of SDK defaults.
        val points=buildList {for(lat in 0..12)for(lon in 0..20){
            val theta=PI.toFloat()*lat/12f;val phi=2f*PI.toFloat()*lon/20f
            add(Vector3(.5f*sin(theta)*cos(phi),.5f*cos(theta),.5f*sin(theta)*sin(phi)))
        }}
        val indices=buildList {for(lat in 0 until 12)for(lon in 0 until 20){
            val a=lat*21+lon;val b=a+21
            if(lat>0)addAll(listOf(a,a+1,b))
            if(lat<11)addAll(listOf(b,a+1,b+1))
        }}
        val colors=points.map{p->val shade=(.70f+p.y*.50f-p.x*.15f+p.z*.08f).coerceIn(.37f,1f);Color4(shade,shade,shade,1f)}
        val mesh=MeshResource.createWithMeshModel(MeshModel(points,indices,colors=colors),bounds=boundsOf(points),name="CloudSoftFixedShade").apply{toGlobal()}
        check(mesh.getTriangleCount()==440);return mesh
    }
    private val sphere=cloudMesh()
    private val cloudMaterial=UnlitMaterial.create().apply {toGlobal();setBaseColor(Color4(.68f,.71f,.75f,1f));setApplyToneMapping(false)}
    private fun texture(streak:Boolean):TextureResource {
        val b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        for(y in 0..63)for(x in 0..63){val nx=(x-31.5f)/31.5f;val ny=(y-31.5f)/31.5f
            val alpha=if(streak)(1f-abs(nx)*6f).coerceIn(0f,1f)*(1f-abs(ny)).coerceIn(0f,1f) else (1f-abs(sqrt(nx*nx+ny*ny)-.65f)*8f).coerceIn(0f,1f)
            b.setPixel(x,y,Color.argb((alpha*220).toInt(),220,237,249))}
        return try{TextureResource.create(b).apply{toGlobal()}}finally{b.recycle()}
    }
    private val streak=texture(true);private val splash=texture(false)
    private data class Patch(val owner:Entity,val cloud:Entity,val rain:ParticleComponent,val splash:ParticleComponent)
    private val patches=mutableMapOf<Int,Patch>();private var active=true
    private fun random(a:Float,b:Float)=ParticleFloatVaryingProperty(ParticleVaryingPropertyType.RANDOM,a,b)
    private fun emitter(owner:Entity,splashes:Boolean):ParticleComponent {
        val e=Entity().apply{setName(if(splashes)"RainSplashes" else "RainStreaks");owner.addChild(this);transform().position=Vector3(0f,if(splashes).001f else .122f,0f)}
        val pc=ParticleComponent();e.components.set(pc)
        pc.isLoop=true;pc.isEmitting=true;pc.isSubSimulationEnabled=false
        pc.birthRateFull=ParticleFloatVaryingProperty(value=if(splashes)70f else 190f)
        pc.emissionDuration=ParticleFloatProperty(value=60f);pc.idleDuration=ParticleFloatProperty(value=0f)
        pc.emitterShape=ParticleEmitterShape(ParticleEmitterShapeType.CYLINDER,Vector3(.112f,.001f,.112f),ParticleEmissionLocation.VOLUME,Int3(1,1,1),ParticleEmissionSpace.LOCAL,Vector3(0f,if(splashes)1f else -1f,0f),360f,0f)
        pc.mainSimulation.apply {
            maxParticleCount=if(splashes)24 else 96;isVisible=true;isMotionEnabled=true;isCameraFadeEnabled=false
            simulationSpace=ParticleSpace.LOCAL;descriptionSpace=ParticleSpace.LOCAL;particleType=ParticleType.BILLBOARD
            facingMode=ParticleFacingMode.CAMERA_POSITION;orientationMode=ParticleOrientationMode.DEFAULT
            cullingBehaviorMode=ParticleCullingBehaviorMode.ALWAYS_SIMULATE
            lifetime=if(splashes)random(.12f,.25f)else random(.36f,.45f)
            startSpeed=if(splashes)random(.008f,.018f)else random(.27f,.30f)
            startSize=if(splashes)random(.0015f,.003f)else random(.015f,.023f)
            acceleration=Vector3(0f,if(splashes)-.10f else -.06f,0f);emitSpreadingAngle=if(splashes)35f else 0f
            isSizeCurveEnabled=splashes
            listOf(sizeCurveX,sizeCurveY,sizeCurveZ).forEach{it.curveType=ParticleCurveType.LINEAR;it.start=.3f;it.end=1.3f}
            isColorGradientEnabled=true;colorGradient=ParticleColorGradient(Color4(.82f,.91f,1f,.90f),Color4(.74f,.86f,.96f,if(splashes)0f else .18f))
            material.apply{materialType=ParticleMaterialType.DEFAULT_UNLIT;blendingMode=if(splashes)ParticleBlendingMode.ALPHA else ParticleBlendingMode.ADDITIVE;baseColor=Color4.WHITE;colorMode=ParticleColorMode.MULTIPLY;baseColorMap=if(splashes)splash else streak}
            isCollisionEnabled=!splashes
            if(!splashes)addCollisionPlane(Vector3(0f,-.121f,0f),Vector3(0f,1f,0f),ParticleFloatProperty(value=1f),ParticleFloatProperty(value=0f))
        }
        return pc
    }
    fun render(rains:List<LocalInterventionWorld.Hazard>) {
        val ids=rains.map{it.id}.toSet();patches.keys.filter{it !in ids}.toList().forEach{patches.remove(it)?.owner?.destroy(true)}
        rains.forEach {r->val p=patches.getOrPut(r.id){
            val owner=Entity().apply{setName("NativeRain_${r.id}");root.addChild(this)}
            val cloud=Entity().apply{setName("RainCloud_${r.id}");owner.addChild(this);transform().position=Vector3(0f,.134f,0f)}
            repeat(6){i->ModelEntity(sphere,cloudMaterial).apply{setName("CloudLobe_$i");cloud.addChild(this);val angle=i*2.399963f;transform().position=Vector3(cos(angle)*.023f,if(i==0).004f else sin(i*1.9f)*.002f,sin(angle)*.018f);transform().scaleVector=Vector3(.048f,.023f+(i%3)*.004f,.041f)}}
            Patch(owner,cloud,emitter(owner,false),emitter(owner,true))}
            p.owner.enabled=active;p.owner.transform().position=Vector3(r.point.x,r.y,r.point.z)
        }
    }
    fun setActive(value:Boolean){active=value;patches.values.forEach{it.owner.enabled=value;it.rain.isEmitting=value;it.splash.isEmitting=value}}
    fun close(){sphere.close();cloudMaterial.close();streak.close();splash.close()}
    companion object {const val MAX_TRIANGLES=3*(96+24)*2;const val MAX_CLOUD_LOBES=18}
}
