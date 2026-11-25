package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Scene-owned state variants, fixed shading, and bounded rain-fed vegetation. */
internal class PrimitiveVillagePresentation(private val world:Entity,private val effects:PrimitiveLocalEffects) {
    private val village=effects.living
    private val root=Entity().apply{setName("VillageConsequences");world.addChild(this)}
    private val meshes=mutableListOf<MeshResource>()
    private val materials=mutableListOf<UnlitMaterial>()
    private val cube=MeshResource.createBox(Vector3(1f)).apply{toGlobal();meshes+=this}
    private fun material(c:Color4)=UnlitMaterial.create().apply{toGlobal();setBaseColor(c);setApplyToneMapping(false);materials+=this}
    private val char=material(Color4(.12f,.105f,.08f,1f));private val wood=material(Color4(.29f,.19f,.105f,1f));private val thatch=material(Color4(.48f,.37f,.21f,1f))
    private val grass=material(Color4(.38f,.45f,.21f,1f));private val soil=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{toGlobal();materials+=this;setBaseColor(Color4(.14f,.16f,.10f,1f));setOpacity(.35f);setDepthWrite(false);setApplyToneMapping(false)}
    private val scorchMaterial=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply{
        toGlobal();materials+=this;setBaseColor(Color4(.30f,.25f,.14f,1f));setOpacity(.28f)
        setDepthWrite(false);setApplyToneMapping(false)
    }
    private fun model(name:String,mesh:MeshResource,mat:Material,parent:Entity=root)=ModelEntity(mesh,mat).apply{setName(name);parent.addChild(this);enabled=false}
    private fun box(name:String,p:Vector3,size:Vector3,mat:Material)=model(name,cube,mat).apply{transform().position=p;transform().scaleVector=size}
    private val residentOwners=(0..8).associate { i->
        val id="PrimitiveResident_$i"
        id to world.requireNode(id)
    }
    /** Cargo follows the same facing and translation as the resident's cradle. */
    private fun residentProp(name:String,id:String,size:Vector3,mat:Material)=
        model(name,cube,mat,residentOwners.getValue(id)).apply {
            transform().position=Vector3(0f,.00665f,.00245f)
            transform().scaleVector=size
        }
    /** Shared real SGS mesh and every texture slot, fitted without changing source bytes. */
    private fun waterVessel(name:String,parent:Entity,position:Vector3,height:Float):Entity {
        val owner=Entity().apply{setName(name);parent.addChild(this)}
        val fit=Entity().apply{setName("${name}_Fit");owner.addChild(this)}
        fun copy(source:Entity,to:Entity) {
            val component=source.components.get<ModelComponent>()
            val node=if(component==null)Entity() else ModelEntity(component.mesh,component.materials.toList().toTypedArray())
            to.addChild(node)
            node.transform().apply {
                this.position=source.transform().position
                eulerAngles=source.transform().eulerAngles
                scaleVector=source.transform().scaleVector
            }
            source.getChildren().forEach{copy(it,node)}
        }
        copy(world.requireNode("PrehistoricWaterPrototype"),fit)
        val bounds=fit.getVisualBounds(owner,recursive=true,enabledOnly=true)
        check(!bounds.isEmpty()&&bounds.size.y>0f)
        val scale=height/bounds.size.y
        fit.transform().scaleVector=Vector3(scale)
        fit.transform().position=bounds.center*(-scale)
        owner.transform().position=position
        owner.enabled=false
        return owner
    }
    private fun heldWater(name:String,id:String)=waterVessel(name,world.requireNode("${id}_NeedGrip"),
        Vector3.ZERO,.0022f)
    private data class ColorSlot(val material:UnlitMaterial,val color:Color4)
    private data class Building(val original:Entity,val bounds:BoundingBox,val position:Vector3,val scale:Vector3,
        val rotation:EulerAngles,val components:List<ModelComponent>,val colors:List<ColorSlot>,val ruins:List<Entity>,val frame:List<Entity>,
        val roofs:List<Entity>,val roofPositions:List<Vector3>,var collapse:Float=0f,var last:String="",var visualKey:String="")
    private fun components(e:Entity):List<ModelComponent> = listOfNotNull(e.components.get<ModelComponent>())+e.getChildren().flatMap(::components)
    private val buildings=village.houses.values.map { h ->
        val original=world.requireNode(h.id);val b=original.getVisualBounds(world,recursive=true,enabledOnly=false)
        val models=components(original);val slots=mutableListOf<ColorSlot>()
        models.forEach { m->for(i in 0 until m.materials.size) {
            val source=m.materials[i] as UnlitMaterial
            val copy=UnlitMaterial.create(source.getBlendingMode()).apply{
                toGlobal();materials+=this;setBaseColor(source.getBaseColor());setCullingMode(source.getCullingMode());setApplyToneMapping(false)
                source.getBaseColorTextureParameter().takeIf{it.texture.valid}?.let{setBaseColorTextureParameter(it)}
            }
            slots+=ColorSlot(copy,source.getBaseColor());m.materials[i]=copy
        }}
        val ruins=(0..6).map{i->box("${h.id}_Charred_$i",Vector3(b.center.x+sin(i*2.4f)*b.halfExtent.x*.55f,b.min.y+.0007f,b.center.z+cos(i*2.4f)*b.halfExtent.z*.55f),Vector3(b.size.x*.55f,.0012f,.0014f),char).apply{transform().eulerAngles=EulerAngles(0f,i*43f,if(i%2==0)5f else -6f)}}
        val frame=(0..7).map{i->
            val x=b.center.x+(if(i%2==0)-1f else 1f)*b.halfExtent.x*.62f;val z=b.center.z+(if(i%4<2)-1f else 1f)*b.halfExtent.z*.62f
            if(i<4)box("${h.id}_Post_$i",Vector3(x,b.min.y+b.size.y*.37f,z),Vector3(.001f,b.size.y*.74f,.001f),wood)
            else box("${h.id}_Beam_$i",Vector3(b.center.x,b.min.y+b.size.y*.72f,z),Vector3(b.size.x*.78f,.001f,.001f),wood)
        }
        val roofs=(0..5).map{i->box("${h.id}_RoofStrip_$i",Vector3(b.center.x+(i-2.5f)*b.size.x/6f,b.max.y-b.size.y*.15f,b.center.z),Vector3(b.size.x/6.3f,.0007f,b.size.z*.9f),thatch).apply{transform().eulerAngles=EulerAngles(12f,0f,if(i<3)-18f else 18f)}}
        h.id to Building(original,b,original.transform().position,original.transform().scaleVector,original.transform().eulerAngles,
            models,slots,ruins,frame,roofs,roofs.map{it.transform().position})
    }.toMap()
    private val leafMesh:MeshResource
    private val soilMesh:MeshResource
    private val plants:List<Entity>;private val wetGround:List<Entity>
    private val field=world.requireNode("FirstField")
    private val fieldRoot=Entity().apply { setName("ContourField");root.addChild(this) }
    private val cropLeaves=mutableListOf<Entity>()
    private val expansions=mutableMapOf<String,List<Entity>>()
    private val birds=mutableListOf<Entity>()
    private val deer=mutableListOf<Entity>()
    private val deerRadii=mutableListOf<Float>()
    private val deerParts=mutableListOf<List<Entity>>()
    private val deerLife by lazy {
        val banks=effects.simulation.navigation.points.filter { p->
            abs(p.x)<.38f&&abs(p.z)<.26f&&effects.drySupport(p.x,p.z)!=null&&
                (effects.drySupport(p.x-.012f,p.z)==null||effects.drySupport(p.x+.012f,p.z)==null)
        }
        VisitingDeerLife(effects.simulation,banks)
    }
    private var deerSpawnRetry=0f
    private var deerEvidenceAt=0f
    private lateinit var canopy:Vector3
    private val scar by lazy { model("FireScar",soilMesh,scorchMaterial) }
    private var dirtTexture:TextureResource?=null
    private fun contourField() {
        val bitmap=android.graphics.Bitmap.createBitmap(96,96,android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val rng=kotlin.random.Random(709)
            for(y in 0 until 96) for(x in 0 until 96) {
                val side=(1f-abs(x-47.5f)/47.5f).coerceAtLeast(0f)
                val grain=rng.nextInt(26)
                val opacity=(side.pow(.65f)*(140+grain*3)*(if(sin(y*.21f+x*.08f)>.75f) .55f else 1f)).toInt().coerceIn(0,255)
                bitmap.setPixel(x,y,android.graphics.Color.argb(opacity,87+grain,74+grain,48+grain))
            }
            dirtTexture=TextureResource.create(bitmap).apply { toGlobal() }
        } finally { bitmap.recycle() }
        val earth=UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            toGlobal();materials+=this;setBaseColor(Color4.WHITE);setApplyToneMapping(false)
            setBaseColorTextureParameter(MaterialTextureParameter(requireNotNull(dirtTexture)))
            setDepthTest(true);setDepthWrite(false);setOpacity(.86f)
        }
        val soilUv=mutableListOf<Vector2>()
        val leaf=material(Color4(.37f,.47f,.18f,1f))
        val oat=material(Color4(.56f,.54f,.25f,1f))
        val vertices=mutableListOf<Vector3>()
        val indices=mutableListOf<Int>()
        fun mesh(name:String,v:List<Vector3>,f:List<Int>,tints:List<Color4>,uv:List<Vector2> = emptyList())=
            MeshResource.createWithMeshModel(MeshModel(v,f,colors=tints,uv0=uv),boundsOf(v),name).apply { toGlobal();meshes+=this }
        val blades=mutableListOf<Vector3>()
        val bladeIndices=mutableListOf<Int>()
        val bladeColors=mutableListOf<Color4>()
        for(i in 0..6) {
            val angle=i*2.39996f
            val reach=.0008f+i%3*.00037f
            val height=.0024f+i%4*.00037f
            val begin=blades.size
            for(j in 0..4) {
                val f=j/4f
                val radius=reach*f*f
                val width=.00023f*(.22f+sin(f*PI.toFloat())*.78f)*(if(j==4).08f else 1f)
                for(side in listOf(-1,1)) {
                    blades+=Vector3(cos(angle)*radius-sin(angle)*width*side,height*(f-.26f*f*f),sin(angle)*radius+cos(angle)*width*side)
                    bladeColors+=Color4(.50f+f*.48f,.59f+f*.40f,.43f+f*.36f,1f)
                }
                if(j>0) {
                    val k=begin+2*j
                    bladeIndices+=listOf(k-2,k,k-1,k-1,k,k+1,k-1,k,k-2,k+1,k,k-1)
                }
            }
        }
        // A short, tapered seed head on a central stalk.
        val start=blades.size
        blades+=listOf(Vector3(-.00009f,0f,0f),Vector3(.00009f,0f,0f),Vector3(.00016f,.003f,0f),
            Vector3(.00003f,.0034f,0f),Vector3(.00043f,.00355f,.00012f),Vector3(.00048f,.004f,0f))
        bladeIndices+=listOf(0,1,2,2,1,0,2,3,4,4,3,2,3,4,5,5,4,3).map { it+start }
        repeat(6){bladeColors+=Color4(1f,.91f,.60f,1f)}
        val sprout=mesh("SeedHeads",blades,bladeIndices,bladeColors)
        val soilColors=mutableListOf<Color4>()
        for(row in 0..4) {
            val n=16
            val start=vertices.size
            for(i in 0..n) {
                val t=i.toFloat()/n
                val x=-.055f+(t-.5f)*(.030f-.002f*abs(row-2))
                val z=.285f+(row-2)*.0037f+.0007f*sin(t*4f+row)
                val width=.0024f*(.25f+.75f*sin(t*PI.toFloat()))
                for(k in -1..1) {
                    val zz=z+k*width
                    val y=requireNotNull(effects.drySupport(x,zz))
                    vertices+=Vector3(x,y+(if(k==0) .00034f else -.00012f),zz)
                    soilUv+=Vector2((k+1)*.5f,t)
                    val tone=.76f+.10f*cos(i*2.6f+row)+if(k==0).12f else 0f
                    soilColors+=Color4(tone,tone*.98f,tone*.92f,1f)
                }
                if(i>0) for(k in 0..1) {
                    val j=start+(i-1)*3+k
                    indices+=listOf(j,j+1,j+3,j+1,j+4,j+3)
                }
                if(i%2==1) {
                    val plant=model("FieldShoot_${row}_$i",sprout,if((i+row)%3==0)oat else leaf,fieldRoot)
                    plant.transform().position=Vector3(x,requireNotNull(effects.drySupport(x,z))+.0003f,z)
                    plant.transform().eulerAngles=EulerAngles(0f,i*31f+row*64f,0f)
                    cropLeaves+=plant
                }
            }
        }
        model("CultivatedSoil",mesh("ContourRidges",vertices,indices,soilColors,soilUv),earth,fieldRoot).enabled=true
        components(field).forEach { it.isVisible=false }
    }
    private fun settlement() {
        val life=village.ecology
        effects.simulation.navigation.points.asSequence().filter { it.distance(life.field)<.32f &&
            effects.drySupport(it.x,it.z)!=null && effects.drySupport(it.x-.014f,it.z)==null }
            .minByOrNull { it.distance(life.field) }?.let { life.bank=it }
        village.enableExpansion()
        village.ecology.sites.forEach { (id,p) ->
            val parts=mutableListOf<Entity>()
            fun timber(x:Float,z:Float,y:Float,w:Float,h:Float,d:Float,roof:Boolean=false) {
                val base=requireNotNull(effects.drySupport(p.x+x,p.z+z))
                parts+=box(id+parts.size,Vector3(p.x+x,base+y,p.z+z),Vector3(w,h,d),if(roof)thatch else wood)
            }
            if(id=="FieldFence") {
                for(i in -3..3) timber(i*.004f,0f,.002f,.0007f,.004f,.0007f)
                timber(0f,0f,.0028f,.025f,.0006f,.0006f)
            } else {
                val width=if(id=="Longhouse") .020f else .012f
                for(i in 0..3) timber((if(i%2==0)-1 else 1)*width*.42f,(if(i<2)-1 else 1)*.004f,.004f,.001f,.008f,.001f)
                timber(0f,0f,.004f,width,.005f,.008f)
                for(i in listOf(-1,1)) {
                    timber(0f,i*.003f,.009f,width*1.12f,.0014f,.0075f,true)
                    parts.last().transform().eulerAngles=EulerAngles(i*28f,0f,0f)
                }
            }
            expansions[id]=parts
        }
        fun visitor(name:String,isBird:Boolean):Entity {
            val owner=Entity().apply { setName(name);root.addChild(this) }
            fun part(x:Float,y:Float,z:Float,w:Float,h:Float,d:Float,tilt:Float) {
                model(name+"Part",cube,if(isBird)char else wood,owner).apply {
                    enabled=true;transform().position=Vector3(x,y,z);transform().scaleVector=Vector3(w,h,d)
                    transform().eulerAngles=EulerAngles(0f,0f,tilt)
                }
            }
            if(isBird) {
                for(i in listOf(-1,1))part(i*.0009f,0f,0f,.0022f,.00028f,.00065f,i*18f)
            } else {
                part(0f,.0034f,0f,.006f,.0027f,.0021f,0f)
                part(.0031f,.0052f,0f,.0035f,.0013f,.0012f,58f)
                part(.0045f,.0065f,0f,.002f,.0012f,.0011f,0f)
                for(i in 0..3)part(if(i%2==0)-.002f else .002f,.0015f,if(i<2)-.0008f else .0008f,.00055f,.0031f,.0005f,0f)
            }
            return owner
        }
        val branches=world.requireNode("NaturalTree_9").getVisualBounds(world,recursive=true,enabledOnly=true)
        canopy=Vector3(branches.center.x,branches.max.y-.003f,branches.center.z)
        repeat(3){birds+=visitor("VisitingBird_$it",true)}
        repeat(2){i->
            val animal=visitor("VisitingDeer_$i",false)
            val parts=animal.getChildren().toList()
            fun radius():Float {
                val b=animal.getVisualBounds(animal,recursive=true,enabledOnly=true)
                check(!b.isEmpty())
                return hypot(maxOf(abs(b.min.x),abs(b.max.x)),maxOf(abs(b.min.z),abs(b.max.z)))
            }
            val standing=radius()
            // The lowered head extends farther than the standing silhouette.
            // Keep one conservative collision disc for both real mesh poses.
            parts[1].transform().eulerAngles=EulerAngles(0f,0f,25f)
            parts[2].transform().position=Vector3(.0052f,.0045f,0f)
            parts[2].transform().eulerAngles=EulerAngles(0f,0f,-25f)
            deerRadii+=maxOf(standing,radius())
            parts[1].transform().eulerAngles=EulerAngles(0f,0f,58f)
            parts[2].transform().position=Vector3(.0045f,.0065f,0f)
            parts[2].transform().eulerAngles=EulerAngles(0f,0f,0f)
            deerParts+=parts
            animal.enabled=false;deer+=animal
        }
    }
    private val pail by lazy { heldWater("ClayWaterJar","PrimitiveResident_7") }
    private val torch by lazy { residentProp("WatchFire","PrimitiveResident_4",Vector3(.0012f,.0025f,.0012f),material(Color4(.98f,.47f,.09f,1f))) }
    private val grain=material(Color4(.66f,.55f,.28f,1f))
    private val storedFood=List(16) { i ->
        val p=effects.simulation.depot
        box("StoredFood_$i",Vector3(p.x-.012f+(i%4)*.0024f,(effects.drySupport(p.x,p.z)?:.034f)+.0012f+(i/4)*.0012f,p.z-.006f),
            Vector3(.002f,.0012f,.003f),grain)
    }
    private val looseFood=List(16) { box("GatheredFood_$it",Vector3.ZERO,Vector3(.002f,.0012f,.003f),grain) }
    private val carriedFood=List(9) {
        model("CarriedFood_$it",cube,grain,world.requireNode("PrimitiveResident_${it}_NeedGrip")).apply {
            transform().scaleVector=Vector3(.003f,.0012f,.002f)
        }
    }
    private val lifePails=List(9) { heldWater("ResidentWater_$it","PrimitiveResident_$it") }
    private val commonJar=waterVessel("CommonWater",root,Vector3(effects.simulation.depot.x+.01f,
        (effects.drySupport(effects.simulation.depot.x,effects.simulation.depot.z)?:.034f)+.0016f,effects.simulation.depot.z),
        .0032f)
    private val waterTop=run {
        val vertices=listOf(Vector3.ZERO)+(0 until 16).map{n->
            val angle=n*2f*PI.toFloat()/16f
            Vector3(cos(angle)*.00062f,0f,sin(angle)*.00062f)
        }
        val indices=(0 until 16).flatMap{n->listOf(0,n+1,(n+1)%16+1,0,(n+1)%16+1,n+1)}
        val mesh=MeshResource.createWithMeshModel(MeshModel(vertices,indices),boundsOf(vertices),"JarWaterDisk").apply{toGlobal();meshes+=this}
        model("StoredWaterSurface",mesh,material(Color4(.24f,.43f,.44f,1f)))
    }
    private val seedPiles=List(3) { i->box("SeedReserve_$i",Vector3(effects.simulation.depot.x-.014f,
        (effects.drySupport(effects.simulation.depot.x,effects.simulation.depot.z)?:.034f)+.0009f,
        effects.simulation.depot.z+i*.002f),Vector3(.0015f,.001f,.002f),grain) }
    private fun prop(entity:Entity,worker:String,task:String) {
        val point=village.residents[worker]
        entity.enabled=village.jobs[worker]?.kind==task && point!=null && !effects.simulation.dangerous(point)
    }
    private fun ecologyView(frameDelta:Float,refreshSlow:Boolean) {
        val life=village.ecology
        if(refreshSlow) {
        fieldRoot.enabled=field.enabled || life.cultivation>0f
        cropLeaves.forEachIndexed { i,e ->
            e.enabled=field.enabled && (i*17%40)<(life.crop*40).toInt()
            e.transform().scaleVector=Vector3((.64f+life.crop*.42f)*(.86f+(i*7%9)*.036f))
        }
        storedFood.forEachIndexed { i,e ->
            e.enabled=i<life.food
            val p=if(i<village.life.foodInGranary)life.sites.getValue("Granary")else effects.simulation.depot
            val slot=if(i<village.life.foodInGranary)i else i-village.life.foodInGranary
            e.transform().position=Vector3(p.x-.012f+(slot%4)*.0024f,(effects.drySupport(p.x,p.z)?:.034f)+.0012f+(slot/4)*.0012f,p.z-.006f)
        }
        looseFood.forEachIndexed { i,e ->
            val bundle=village.foodBundles.getOrNull(i)
            val carrierJob=bundle?.carrier?.let { village.jobs[it] }
            e.enabled=bundle!=null && !(carrierJob?.carrying==true && carrierJob.food===bundle)
            if(bundle!=null) e.transform().position=Vector3(bundle.point.x,(effects.drySupport(bundle.point.x,bundle.point.z)?:.034f)+.0006f,bundle.point.z)
        }
        }
        carriedFood.forEachIndexed { i,e ->
            val id="PrimitiveResident_$i"
            val job=village.jobs[id]
            val point=village.residents[id]
            e.enabled=job?.food!=null && job.carrying && point!=null
        }
        lifePails.forEachIndexed{i,e->
            val id="PrimitiveResident_$i";val j=village.jobs[id];val at=village.residents[id]
            val usingVessel=j?.event in setOf(VillageEvent.DRINK_RIVER,VillageEvent.DRINK_JAR,VillageEvent.ALTERNATE_BANK,VillageEvent.CATCH_RAIN,VillageEvent.FILL_JAR)
            e.enabled=at!=null&&j!=null&&((usingVessel&&j.path.isEmpty())||j.carrying&&j.food==null&&j.wood==null)
        }
        if(refreshSlow) {
        commonJar.enabled=village.life.enabled
        waterTop.enabled=village.life.enabled&&village.life.water>.05f
        val jar=commonJar.transform().position
        waterTop.transform().position=Vector3(jar.x,jar.y+.0010f+village.life.water*.00018f,jar.z)
        seedPiles.forEachIndexed{i,e->e.enabled=i<village.life.seeds}
        expansions.forEach { (id,parts) ->
            val h=village.houses.getValue(id)
            parts.forEachIndexed { i,e->e.enabled=h.available && (h.rebuilds>0 && !h.ruined || h.progress/h.workRequired>(i+.2f)/parts.size) }
        }
        }
        prop(pail,"PrimitiveResident_7","quench")
        prop(torch,"PrimitiveResident_4","guard")
        val t=effects.simulation.seconds
        val danger=effects.simulation.hazards.any { it.kind==LocalInterventionWorld.Kind.FIRE }
        birds.forEachIndexed { i,b ->
            val foodTarget=village.life.birdTarget()
            val fly=danger || (t+i*1.3f)%37f<12f
            val a=if(fly)t*.65f+i else i.toFloat()
            val target=if(foodTarget!=null&&!danger)Vector3(foodTarget.x,(effects.drySupport(foodTarget.x,foodTarget.z)?:.034f)+.001f,foodTarget.z)
                else Vector3(canopy.x+cos(a)*(if(fly).024f else .008f),canopy.y+(if(fly).022f+sin(a)*.005f else 0f),canopy.z+sin(a)*.012f)
            val prior=b.transform().position
            b.transform().position=if(prior.y<=0f)target else prior+(target-prior)*(frameDelta*3f).coerceIn(0f,1f)
            b.transform().eulerAngles=EulerAngles(0f,-a*57f,if(fly)sin(t*12f)*19f else 0f)
            val actual=b.transform().position
            if(frameDelta>0f&&actual.y<.045f)village.life.birdAt(Point(actual.x,actual.z))
        }
        if(frameDelta>0f&&t>=deerSpawnRetry) {
            deerSpawnRetry=t+2f
            deer.forEachIndexed { i,e->
                val id="VisitingDeer_$i"
                if(id !in deerLife.states&&deerLife.add(id,Point(-.145f+i*.014f,.105f),deerRadii[i],i)!=null)e.enabled=true
            }
        }
        val grazed=deerLife.states.values.sumOf{it.grazes}
        deerLife.advance(frameDelta)
        if(deerLife.states.values.sumOf{it.grazes}!=grazed)village.changed()
        deer.forEachIndexed { i,e->
            val state=deerLife.states["VisitingDeer_$i"]?:return@forEachIndexed
            val at=state.body.point
            e.transform().position=Vector3(at.x,requireNotNull(effects.support(at.x,at.z)),at.z)
            e.transform().eulerAngles=EulerAngles(0f,state.heading,0f)
            val parts=deerParts[i]
            val lower=state.activity in listOf("drink","graze")
            parts[1].transform().eulerAngles=EulerAngles(0f,0f,if(lower)25f else 58f)
            parts[2].transform().position=if(lower)Vector3(.0052f,.0045f,0f)else Vector3(.0045f,.0065f,0f)
            parts[2].transform().eulerAngles=EulerAngles(0f,0f,if(lower)-25f else 0f)
            for(leg in 0..3)parts[leg+3].transform().eulerAngles=EulerAngles(0f,0f,
                if(state.moving)sin(t*(if(state.intent==VisitingDeerLife.Intent.FLEE)18f else 10f)+leg*PI.toFloat())*18f else 0f)
            if(frameDelta>0f&&t>=deerEvidenceAt)Log.i("VisitorDeer",
                "id=${state.body.id} point=$at radius=${state.body.radius} y=${e.transform().position.y} support=${effects.support(at.x,at.z)} ford=${effects.drySupport(at.x,at.z)==null} activity=${state.activity} intent=${state.intent} goal=${state.goal} work=${state.worked} thirst=${state.thirst} hunger=${state.hunger} drinks=${state.drinks} grazes=${state.grazes} escapes=${state.escapes}")
        }
        if(frameDelta>0f&&t>=deerEvidenceAt)deerEvidenceAt=t+5f
        if(refreshSlow) {
        scar.enabled=life.scorch!=null && life.scarAge<135f
        scorchMaterial.setOpacity(.28f*(1f-life.scarAge/135f).coerceIn(0f,1f))
        life.scorch?.let { p->effects.drySupport(p.x,p.z)?.let { y->
            scar.transform().position=Vector3(p.x,y+.00012f,p.z)
            scar.transform().scaleVector=Vector3(.23f*(1f-life.scarAge/140f).coerceAtLeast(.01f),1f,.17f)
        } }
        }
    }
    init {
        val points=mutableListOf<Vector3>();val faces=mutableListOf<Int>()
        repeat(30){i->val a=i*2.399963f;val radius=.004f+sqrt(i/30f)*.034f;val x=cos(a)*radius;val z=sin(a)*radius;val start=points.size
            points+=listOf(Vector3(x-.001f,0f,z),Vector3(x+.001f,0f,z),Vector3(x+.001f,.003f+(i%3)*.0006f,z+.0015f));faces+=listOf(start,start+1,start+2,start+2,start+1,start)
        }
        leafMesh=MeshResource.createWithMeshModel(MeshModel(points,faces),boundsOf(points),"RainFedGrass").apply{toGlobal();meshes+=this}
        val ring=listOf(Vector3.ZERO)+(0..17).map{i->val a=i*2*PI.toFloat()/18;Vector3(cos(a)*.047f*(.9f+.08f*sin(i*2f)),0f,sin(a)*.047f)}
        val indices=(0..17).flatMap{i->listOf(0,1+(i+1)%18,1+i)}
        soilMesh=MeshResource.createWithMeshModel(MeshModel(ring,indices,colors=ring.indices.map{Color4(1f,1f,1f,if(it==0).8f else 0f)}),boundsOf(ring),"WetGroundPatches").apply{toGlobal();meshes+=this}
        plants=List(24){model("RainGrowth_$it",leafMesh,grass)};wetGround=List(24){model("WetGround_$it",soilMesh,soil)}
        contourField();settlement();scar.enabled=false;pail.enabled=false;torch.enabled=false
        village.sources.clear()
        listOf(0,2,5,7,9,11,13,15).forEach {i->val b=world.requireNode("NaturalTree_$i").getVisualBounds(world,recursive=true,enabledOnly=true)
            village.nearestOpen(Point(b.center.x+.024f,b.center.z+.024f))?.let{village.sources+=it}
        }
        village.life.enable()
    }
    private var slowElapsed=.1f
    fun apply(dt:Float,active:Boolean) {
        slowElapsed+=if(active)dt else 0f
        val refreshSlow=slowElapsed>=.1f || !active
        if(refreshSlow)slowElapsed=0f
        buildings.forEach { (id,b)->val h=village.houses.getValue(id)
            if(active)b.collapse=(b.collapse+(if(h.ruined)dt*1.8f else -dt*3f)).coerceIn(0f,1f)
            val visualKey="${h.health}|${h.ruined}|${h.cleared}|${h.materials}|${h.progress}|${h.damageCause}|${h.stage}|${b.collapse}"
            if(visualKey==b.visualKey)return@forEach
            b.visualKey=visualKey
            val factor=1f-.94f*b.collapse
            b.original.transform().scaleVector=Vector3(b.scale.x,b.scale.y*factor,b.scale.z)
            b.original.transform().position=Vector3(b.position.x,b.position.y+(b.bounds.min.y-b.position.y)*(1f-factor),b.position.z)
            b.components.forEach{it.isVisible=b.collapse<.99f}
            val shade=if(h.damageCause=="fire")if(h.ruined).22f else (.28f+.72f*h.health/100f)else 1f
            b.colors.forEach{it.material.setBaseColor(Color4(it.color.red*shade,it.color.green*shade,it.color.blue*shade,it.color.alpha))}
            b.ruins.forEachIndexed{i,e->
                e.enabled=h.ruined&&i>=((h.cleared/6f)*5).toInt()
                e.components.get<ModelComponent>()?.materials?.set(0,if(h.damageCause=="fire")char else wood)
            }
            if(!h.ruined && h.damageCause=="animal") {
                b.original.transform().eulerAngles=EulerAngles(b.rotation.pitch,b.rotation.yaw,b.rotation.roll+(1f-h.health/100f)*9f)
                b.roofs.forEachIndexed{i,e->e.enabled=i<2;e.transform().position=Vector3(b.bounds.center.x+(i-1)*.002f,b.bounds.min.y+.001f,b.bounds.center.z+.004f)}
            } else {
                b.original.transform().eulerAngles=b.rotation
                b.roofs.forEachIndexed{i,e->e.transform().position=b.roofPositions[i]}
            }
            val fraction=(h.progress/h.workRequired).coerceIn(0f,1f)
            b.frame.forEachIndexed{i,e->e.enabled=h.ruined&&h.materials>0&&fraction>(if(i<4).02f else .25f)}
            if(h.ruined || h.damageCause!="animal")b.roofs.forEachIndexed{i,e->e.enabled=h.ruined&&fraction>.55f+i*.06f}
            val stage="${h.stage}/${h.ruined}/${(fraction*4).toInt()}/${(h.health/25).toInt()}"
            if(stage!=b.last){Log.i("LivingVillage","house=$id health=${h.health} stage=$stage materials=${h.materials} rebuilt=${h.rebuilds}");b.last=stage}
        }
        if(refreshSlow) {
        effects.simulation.wetSpots.forEachIndexed {i,patch->
            val y=effects.drySupport(patch.point.x,patch.point.z)
            val e=plants[i];e.enabled=y!=null&&patch.growth>.05f
            if(y!=null){e.transform().position=Vector3(patch.point.x,y+.0002f,patch.point.z);e.transform().scaleVector=Vector3(1f,patch.growth,1f);e.transform().eulerAngles=EulerAngles(0f,0f,sin(effects.simulation.seconds*1.3f+i)*2f)}
            val soil=wetGround[i];soil.enabled=y!=null&&patch.wet>.15f
            if(y!=null){soil.transform().position=Vector3(patch.point.x,y+.00006f,patch.point.z);soil.transform().scaleVector=Vector3(.35f+patch.wet*.45f,1f,.35f+patch.wet*.45f)}
        }
        for(i in effects.simulation.wetSpots.size until 24){plants[i].enabled=false;wetGround[i].enabled=false}
        }
        ecologyView(if(active)dt else 0f,refreshSlow)
    }
    fun close(){deerLife.close();meshes.forEach{it.close()};materials.forEach{it.close()};dirtTexture?.close()}
}
