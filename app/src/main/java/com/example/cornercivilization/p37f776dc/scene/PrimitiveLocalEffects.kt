package com.example.cornercivilization.p37f776dc.scene

import com.example.cornercivilization.p37f776dc.domain.usecase.VillageChronicle
import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalNavigation
import com.example.cornercivilization.p37f776dc.domain.usecase.LivingVillage
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.resource.*
import com.pico.spatial.core.math.*
import kotlin.math.*

/** Bounded scene pools; short-lived events use the same local metre space as terrain and residents. */
internal class PrimitiveLocalEffects(private val world: Entity, private val ground: PrimitiveGroundSurface) {
    private val root=Entity().apply {setName("LocalWorldEffects");world.addChild(this)}
    private val materials=mutableListOf<UnlitMaterial>()
    private val ownedMeshes=mutableListOf<MeshResource>()
    private val cube=MeshResource.createBox(Vector3(1f)).also {ownedMeshes+=it}
    private val sphere=MeshResource.createSphere(.5f).also {ownedMeshes+=it}
    private fun material(r:Float,g:Float,b:Float)=UnlitMaterial.create().apply {toGlobal();setBaseColor(Color4(r,g,b,1f));setApplyToneMapping(false);materials+=this}
    private val stoneMaterial=material(.31f,.29f,.23f)
    private val buildings=listOf("Shelter_0","Shelter_1","Shelter_2","RainShelter").associateWith {world.requireNode(it).getVisualBounds(world,recursive=true,enabledOnly=false)}
    private val huts=buildings.filterKeys { it != "RainShelter" }.values
    // Keep walls and rear corners solid. One footpath ends just under the front eave.
    private fun blockedByHut(x:Float,z:Float)=huts.any { b ->
        val inside=x>b.min.x-.004f && x<b.max.x+.004f && z>b.min.z-.004f && z<b.max.z+.004f
        val doorway=abs(x-b.center.x)<minOf(.011f,b.halfExtent.x*.50f) && z>=b.center.z
        inside && !doorway
    }
    private fun dryHeight(x:Float,z:Float):Float? = ground.heightOrNull(x,z)
    // A narrow visible stepping-stone ford connects both banks; elsewhere water stays impassable.
    fun support(x:Float,z:Float):Float? = dryHeight(x,z) ?: if(abs(z)<.014f && x in -.44f.. .44f) .029f else null
    val simulation=LocalInterventionWorld(LocalNavigation(::support, ::blockedByHut).apply {
        trace = { name, begin -> if(begin) android.os.Trace.beginSection(name) else android.os.Trace.endSection() }
    })
    val living=LivingVillage(simulation).also { v ->
        simulation.village=v
        buildings.forEach { (id,b) ->
            val work=v.nearestOpen(LocalInterventionWorld.Point(b.center.x,b.max.z+.009f)) ?: error("No house worksite $id")
            v.addHouse(id,LocalInterventionWorld.Point(b.center.x,b.center.z),maxOf(b.halfExtent.x,b.halfExtent.z),work)
            val preferred=LocalInterventionWorld.Point(b.center.x,b.center.z+b.halfExtent.z*.32f)
            simulation.navigation.points.asSequence().filter { p ->
                abs(p.x-b.center.x)<minOf(.009f,b.halfExtent.x*.45f) &&
                    p.z>=b.center.z && p.z<b.max.z-.003f && dryHeight(p.x,p.z)!=null
            }.minByOrNull { it.distance(preferred) }?.let { v.restSeats[id]=it }
        }
    }
    fun drySupport(x:Float,z:Float)=dryHeight(x,z)
    private fun roofBounds()=buildings.mapValues { (id,b)->if(living.houses[id]?.ruined==true) BoundingBox(Vector3(b.center.x,b.min.y+.001f,b.center.z),Vector3(b.halfExtent.x,.001f,b.halfExtent.z)) else b }
    val maxEnvelope=BoundingBox(Vector3(0f,.12f,0f),Vector3(.505f,.12f,.39f))
    private fun model(name:String,mesh:MeshResource,mat:Material)=ModelEntity(mesh,mat).apply {setName(name);root.addChild(this);enabled=false}
    private val grainGold=material(.86f,.62f,.25f)
    private val leafGreen=material(.24f,.46f,.14f)
    private val berryRed=material(.72f,.18f,.09f)
    private val pale=material(.96f,.81f,.46f)
    private val seedDots=List(18){model("PlantedSeed_$it",cube,grainGold)}
    private val shoots=List(18){i->List(3){k->model("SeedShoot_${i}_$k",cube,leafGreen)}}
    private val berries=List(18){model("SeedBerry_$it",cube,berryRed)}
    private val previews=List(12){model("SowingPreview_$it",cube,pale)}
    private val tracks=List(6){model("ResidentTrack_$it",cube,pale)}
    private val ripples=List(12){model("DrumRipple_$it",cube,pale)}
    private fun mark(e:Entity,x:Float,z:Float,y:Float,sx:Float,sy:Float,sz:Float,angle:Float=0f){
        e.enabled=true;e.transform().position=Vector3(x,y,z)
        e.transform().scaleVector=Vector3(sx,sy,sz);e.transform().eulerAngles=EulerAngles(0f,angle,0f)
    }
    private fun showInfluence(){
        val influence=living.influence
        seedDots.forEachIndexed { i,e->
            val plot=influence.plots.getOrNull(i)
            if(plot==null) {
                if(e.enabled)e.enabled=false
                shoots[i].forEach{if(it.enabled)it.enabled=false};if(berries[i].enabled)berries[i].enabled=false
            }else{
                val p=plot.point;val y=dryHeight(p.x,p.z)?:.034f
                mark(e,p.x,p.z,y+.0006f,.004f,.0011f,.0026f,28f)
                shoots[i].forEachIndexed{k,leaf->
                    if(plot.growth>.12f){
                        val a=k*2.1f;val h=.001f+plot.growth*.005f
                        mark(leaf,p.x+.0017f*cos(a),p.z+.0017f*sin(a),y+h*.5f,.0017f,h,.003f,k*64f)
                    }else leaf.enabled=false
                }
                if(!plot.meadow && plot.growth>=1f)mark(berries[i],p.x,p.z,y+.006f,.003f,.0026f,.003f,40f)
                else berries[i].enabled=false
            }
        }
        previews.forEachIndexed{i,e->val p=seedPreview.getOrNull(i)
            if(p==null)e.enabled=false else mark(e,p.x,p.z,(dryHeight(p.x,p.z)?:.034f)+.001f,.003f,.001f,.002f)}
        tracks.forEachIndexed{i,e->
            val p=if(i==5)influence.followed?.let{living.residents[it]} else influence.footsteps.getOrNull(i)
            if(p==null)e.enabled=false else mark(e,p.x+if(i%2==0).001f else -.001f,p.z,
                (dryHeight(p.x,p.z)?:.034f)+.0008f,if(i==5).008f else .0023f,.0008f,.0036f,if(i%2==0)18f else -18f)
        }
        val beat=influence.beat
        val age=beat?.let{simulation.seconds-it.at}?:2f
        ripples.forEachIndexed{i,e->
            if(beat==null || age>=1.1f)e.enabled=false else{
                val angle=i*2f*PI.toFloat()/12f;val radius=.008f+age*.053f
                val x=beat.point.x+radius*cos(angle);val z=beat.point.z+radius*sin(angle)
                mark(e,x,z,(dryHeight(x,z)?:.034f)+.0012f,.0013f,.0007f,.004f+radius*.24f,-angle*180f/PI.toFloat())
            }
        }
    }
    private var nextInfluenceFrame=-1f
    private val rainParticles=PrimitiveRainParticles(root)
    private val fireParticles=PrimitiveFireParticles(root)
    private val woodSource=requireNotNull(world.requireNode("Resources_0").requireNode("NativeModel").components.get<ModelComponent>())
    private val wood=List(6) {i->ModelEntity(woodSource.mesh,woodSource.materials.toList().toTypedArray()).apply {
        setName("DroppedWood_$i");root.addChild(this)
        val b=getVisualBounds(root,recursive=true,enabledOnly=true);val scale=PrimitiveWoodAppearance.BUNDLE_LENGTH_METRES/maxOf(b.size.x,b.size.y,b.size.z)
        transform().scaleVector=Vector3(scale);transform().position=Vector3(-b.center.x*scale,-b.min.y*scale,-b.center.z*scale);enabled=false
    }}
    private val woodOffset=wood.map {it.transform().position}
    private val bundleSize = wood.first().getVisualBounds(root,recursive=true,enabledOnly=false).size
    private val stackHeight = bundleSize.y
    private var lastSummary=""
    init {
        for(i in 0..146) {val x=-.438f+i*.006f
            if(dryHeight(x,0f)==null && abs(x)<.43f) model("RiverFord_$i",sphere,stoneMaterial).apply {
                enabled=true
                transform().position=Vector3(x,.0278f+(i%3)*.00012f,sin(i*2.1f)*.0015f)
                transform().scaleVector=Vector3(.0054f+(i%3)*.0002f,.0024f,.008f+(i%4)*.0005f)
                transform().eulerAngles=EulerAngles(0f,i*37f,0f)
            }
        }
        Log.i("LocalWorld","pools ready rain=120 nativeFireSlots=3 nativeParticleCap=408 wood=6 ground=sampled ford=visible life=15s woodDecayAfterArrival=30s")
    }
    var seedPreview:List<LocalInterventionWorld.Point> = emptyList()
    fun canSow(p:LocalInterventionWorld.Point)=p.x in -.44f.. .44f && p.z in -.32f.. .32f &&
        dryHeight(p.x,p.z)!=null && simulation.navigation.open(p) && living.houses.values.none{it.available && it.center.distance(p)<it.radius}
    fun cast(tool:FireTool,point:Vector3,house:String?=null):Boolean {
        if(point.x !in -.44f.. .44f || point.z !in -.32f.. .32f)return false
        if(house!=null && living.houses[house]?.available==false)return false
        val p=LocalInterventionWorld.Point(point.x,point.z)
        val b=house?.let {roofBounds()[it]}
        val y=b?.max?.y ?: dryHeight(point.x,point.z) ?: .0275f
        val accepted=when(tool) {
            FireTool.WATER->simulation.cast(LocalInterventionWorld.Kind.RAIN,p,y,radius=.06f)
            FireTool.FIRE->if(dryHeight(point.x,point.z)!=null || b!=null) simulation.cast(LocalInterventionWorld.Kind.FIRE,p,y,house,radius=.05f) else false
            FireTool.WOOD->house==null && dryHeight(point.x,point.z)!=null && simulation.dropWood(p)
            FireTool.DRUM->house==null && dryHeight(p.x,p.z)!=null && living.influence.drum(p)
            FireTool.SEED->house==null && canSow(p) && living.influence.plant(listOf(p))>0
            FireTool.OBSERVE->false
        }
        if (accepted) {
            val site = house?.let(VillageChronicle::house) ?: "沙盘" + VillageChronicle.place(p)
            when (tool) {
                FireTool.FIRE -> simulation.tell("你在${site}点起了一簇火。", true)
                FireTool.WOOD -> simulation.tell("你在${site}放下了一束木料。", true)
                FireTool.WATER -> simulation.tell("你在${site}唤来一场雨。", true)
                FireTool.DRUM,FireTool.SEED,FireTool.OBSERVE -> Unit
            }
        }
        Log.i("LocalInput","cast tool=$tool x=${point.x} z=${point.z} y=$y house=$house accepted=$accepted")
        return accepted
    }
    fun placeHeldRain(point:Vector3, id:Int?=null, house:String?=null):Int? {
        if(!point.x.isFinite() || !point.z.isFinite() || point.x !in -.44f.. .44f || point.z !in -.32f.. .32f)return null
        val p=LocalInterventionWorld.Point(point.x,point.z)
        val y=house?.let{roofBounds()[it]?.max?.y} ?: dryHeight(point.x,point.z) ?: .0275f
        return if(id==null) simulation.beginRain(p,y,house)?.also { simulation.tell("你唤来一片雨云，云随手而行。", true) }
            else if(simulation.moveRain(id,p,y,house)) id else null
    }
    fun endHeldRain(id:Int,cancelled:Boolean=false) = simulation.endRain(id,cancelled)

    fun setActive(value:Boolean) {fireParticles.setActive(value);rainParticles.setActive(value)}
    fun advance(dt:Float) {simulation.advance(dt)}
    fun render() {
        if(simulation.seconds>=nextInfluenceFrame){showInfluence();nextInfluenceFrame=simulation.seconds+.055f}
        val rains=simulation.hazards.filter{it.kind==LocalInterventionWorld.Kind.RAIN}
        val fires=simulation.hazards.filter{it.kind==LocalInterventionWorld.Kind.FIRE}
        rainParticles.render(rains)
        fireParticles.render(fires,roofBounds())
        wood.forEachIndexed {i,e->
            val w=simulation.woods.getOrNull(i);e.enabled=w!=null && w.phase!=LocalInterventionWorld.WoodPhase.CARRIED
            if(w!=null && e.enabled) {
                val stacked=w.phase==LocalInterventionWorld.WoodPhase.STACKED
                val slot=simulation.woods.take(i).count{it.phase==LocalInterventionWorld.WoodPhase.STACKED}
                // Broad base 3 bundles, then 2, then 1: a low, interlocking timber stack.
                val row=if(slot<3)0 else if(slot<5)1 else 2
                val column=if(row==0)slot-1f else if(row==1)slot-3.5f else 0f
                val p=if(stacked)LocalInterventionWorld.Point(simulation.depot.x+(row%2)*bundleSize.x*.04f,simulation.depot.z+column*bundleSize.z*1.04f)else w.point
                val stack=if(stacked)row*stackHeight*.86f else 0f
                val yaw=if(stacked)if(row%2==0)2f else -2f else (w.id%7-3)*7f
                e.transform().eulerAngles=EulerAngles(0f,yaw,0f)
                val angle=yaw*PI.toFloat()/180f;val offset=woodOffset[i]
                val rotated=Vector3(offset.x*cos(angle)+offset.z*sin(angle),offset.y,-offset.x*sin(angle)+offset.z*cos(angle))
                e.transform().position=Vector3(p.x,(support(p.x,p.z)?:.034f)+stack,p.z)+rotated
            }
        }
        val summary="rain=${rains.size} fire=${fires.size} woods=${simulation.woods.map{it.phase}}"
        if(summary!=lastSummary){Log.i("LocalWorld", "state t=${simulation.seconds} $summary");lastSummary=summary}
    }
    fun close() {fireParticles.close();rainParticles.close();ownedMeshes.forEach{it.close()};materials.forEach{it.close()}}
}
