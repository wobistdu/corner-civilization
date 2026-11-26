package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ViewCoordinateSpace
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real scene and real elapsed time in the isolated acceptance package. Input is programmatic,
 * through the same view-local-pixel conversion as SpatialTapValue; not a hand-tracking test. */
@RunWith(AndroidJUnit4::class)
class LocalInteractionJourneyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private fun await(label: String, timeout: Long, predicate: () -> Boolean) {
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start < timeout) {
            var done=false; main {done=predicate()}; if(done) {Log.i("LocalJourney", "PASS $label elapsed=${SystemClock.elapsedRealtime()-start}");return}
            SystemClock.sleep(100)
        }
        fail("Timed out: $label")
    }
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T = owner.javaClass.getDeclaredField(name).let {it.isAccessible=true;it.get(owner) as T}
    @Test fun rainToPotteryInANewVillage() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa18.living.acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("fresh village ready",90_000) { PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true }
        val runtime = requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        val effects = field<PrimitiveLocalEffects>(runtime,"localEffects")
        val sim=effects.simulation
        val village=effects.living
        val world=field<Entity>(runtime,"world")
        lateinit var target:Entity
        val owner=field<Entity>(runtime,"placementOwner")
        val content=field<SpatialViewContent>(runtime,"content")
        fun px(p:LocalInterventionWorld.Point)=content.convertPosition(world.convertPositionTo(Vector3(p.x,0f,p.z),owner),
            content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
        main {
            target=world.requireNode("FloodplainTerrain")
            assertFalse(village.life.river.state.cistern)
            val p=px(village.ecology.bank)
            assertTrue(runtime.rainPointer("clay",target,p,true,true))
            assertTrue(runtime.rainPointer("clay",target,p,false,false))
        }
        await("rain exposed workable river mud",20_000) { village.life.river.state.softBank>50f }
        await("resident delivered blank to stockpile",180_000) { village.life.river.state.blank || village.life.river.state.cistern }
        main {
            if(!village.life.river.state.cistern) assertTrue(runtime.castAtTap(target,px(sim.depot),FireTool.WOOD))
        }
        await("gift hauled and vessel fired",150_000) { village.life.river.state.cistern && world.requireNode("RainCistern").enabled }
        main {
            assertTrue(village.life.journal.counts.containsKey(com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.FIRE_CISTERN))
            Log.i("LocalJourney","fresh village pottery complete; people=${village.residents.size} entries=${village.life.journal.counts}")
        }
    }

    @Test fun riverEquipmentReactsToRainAndVillagersLandACatch() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".qa18.living.acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("village ready", 90_000) { PrimitivePreviewRuntime.acceptanceInstance.get()?.ready == true }
        val runtime = requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        val effects = field<PrimitiveLocalEffects>(runtime,"localEffects")
        val world = field<Entity>(runtime,"world")
        val sim = effects.simulation
        val life = effects.living.life
        val owner = field<Entity>(runtime,"placementOwner")
        val content = field<SpatialViewContent>(runtime,"content")
        main {
            // Resume an isolated fishing village one tide before its first catch.
            life.river.restore(com.example.cornercivilization.p37f776dc.domain.usecase.RiverLivelihood.State(
                cistern=true, trapUses=3, catchProgress=.90f))
            life.water = .05f
            val p = sim.depot
            val px = content.convertPosition(world.convertPositionTo(Vector3(p.x,0f,p.z),owner),content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
            val ground = world.requireNode("FloodplainTerrain")
            assertTrue(runtime.rainPointer("river",ground,px,true,true))
            assertTrue(runtime.rainPointer("river",ground,px,false,false))
            assertTrue(sim.rainAt(p)>.5f)
        }
        await("visible cistern takes rain through the terrain gesture",20_000) {
            world.requireNode("RainCistern").enabled && world.requireNode("RiverFishTrap").enabled && life.water>.35f
        }
        await("catch reaches the float",25_000) { world.requireNode("FishTrapFloat").enabled }
        val event = com.example.cornercivilization.p37f776dc.domain.usecase.VillageEvent.LIFT_FISH_TRAP
        await("resident reaches shore and lands actual fish",150_000) {
            life.journal.counts.getOrDefault(event,0)>0 && life.river.state.trapUses==2
        }
        main {
            assertTrue(world.requireNode("RiverFishTrap").enabled)
            assertTrue(requireNotNull(runtime.chronicle).snapshot().any{it.text.contains("鲜鱼")})
            Log.i("LocalJourney","river state=${life.river.state} water=${life.water} catch=${life.journal.counts[event]}")
        }
    }

    @Test fun chronicleRecordsAcceptedToolsAndReactions() {
        val context = instrumentation.targetContext
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("village ready", 75_000) { PrimitivePreviewRuntime.acceptanceInstance.get()?.ready == true }
        val runtime = requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        main {
            runtime.toggleScroll()
            assertTrue(runtime.scrollOpen)
            val effects = field<PrimitiveLocalEffects>(runtime,"localEffects")
            val sim = effects.simulation
            val p = sim.navigation.points.first { it.x in .16f.. .32f && it.z in -.23f..-.13f && effects.drySupport(it.x,it.z) != null && sim.navigation.path(it, sim.depot) != null }
            val at = Vector3(p.x, 0f, p.z)
            val world = field<Entity>(runtime,"world")
            val target = world.requireNode("FloodplainTerrain")
            val owner = field<Entity>(runtime,"placementOwner")
            val content = field<SpatialViewContent>(runtime,"content")
            val px = content.convertPosition(world.convertPositionTo(at,owner), content.localSpatialCoordinateSpace, ViewCoordinateSpace.Local)
            val book = requireNotNull(runtime.chronicle)
            val before = book.revision
            assertTrue(runtime.castAtTap(target, px, FireTool.WOOD))
            assertTrue(runtime.castAtTap(target, px, FireTool.FIRE))
            assertTrue(runtime.rainPointer("test", target, px, true, true))
            repeat(12) { assertTrue(runtime.rainPointer("test", target, px, true, false)) }
            assertTrue(runtime.rainPointer("test", target, px, false, false))
            val added = book.snapshot().takeLast((book.revision - before).toInt())
            assertEquals(4, added.size)
            assertTrue(added.all { it.player })
            assertTrue(added[0].text.contains("木料"))
            assertTrue(added[1].text.contains("点起"))
            assertTrue(added.last().text.contains("右侧后方"))
        }
        await("fire extinguished with scroll open", 15_000) {
            runtime.scrollOpen && requireNotNull(runtime.chronicle).snapshot().any { !it.player && it.text.contains("雨中熄灭") }
        }
        await("page saved with both origins", 10_000) {
            val data = runCatching { org.json.JSONObject(context.filesDir.resolve("living-village-v11.json").readText()) }.getOrNull()
            val rows = data?.optJSONArray("chronicle")
            rows != null && (0 until rows.length()).any { rows.getJSONObject(it).getString("text").contains("雨中熄灭") } &&
                (0 until rows.length()).any { rows.getJSONObject(it).getBoolean("player") }
        }
        main { runtime.toggleScroll(); assertFalse(runtime.scrollOpen) }
    }

    @Test fun farBankEncounterAndWildCalfHunt() {
        val ctx=instrumentation.targetContext
        check(ctx.packageName.contains("qa173")) { "isolated wildlife fixtures only" }
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("ready",75000){ PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true }
        val rt=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        val local=field<PrimitiveLocalEffects>(rt,"localEffects")
        val sim=local.simulation
        val scene=field<Entity>(rt,"world")
        val fauna=field<PrimitiveFaunaRuntime>(rt,"fauna")
        val actors=field<List<Any>>(fauna,"actors")
        fun nav(id:String)=field<com.example.cornercivilization.p37f776dc.domain.usecase.PrimitiveFaunaMind>(
            actors.first{field<Entity>(it,"entity").getName()==id},"mind").navigation
        val tiger=sim.wildlife.bodies.getValue("Sabertooth_1")
        val villager=sim.wildlife.bodies.getValue("PrimitiveResident_7")
        val calf=sim.wildlife.bodies.getValue("MammothCalf")
        var marked=0f
        main {
            for(a in actors) {
                val id=field<Entity>(a,"entity").getName()
                for(house in local.living.houses.values.filter{it.id.startsWith("Shelter_")}) {
                    assertFalse("camp held away from $id", nav(id).open(house.center))
                    assertFalse("buffer around camp", nav(id).open(LocalInterventionWorld.Point(house.center.x+.065f,house.center.z)))
                }
            }
            val site=nav(tiger.id).points.first{it.x in .20f.. .34f && it.z in .06f.. .17f && sim.farBank(it) &&
                sim.wildlife.open(tiger.id,it) && sim.navigation.open(LocalInterventionWorld.Point(it.x+tiger.radius+villager.radius+.001f,it.z))}
            tiger.point=site;tiger.rest=0f;tiger.feeding=0f
            val at=LocalInterventionWorld.Point(site.x+tiger.radius+villager.radius+.001f,site.z)
            villager.point=at
            scene.requireNode(villager.id).transform().position=Vector3(at.x,requireNotNull(sim.navigation.height(at.x,at.z)),at.z)
            marked=sim.seconds
        }
        await("contact on the far shore takes real resident",5000){ !villager.alive && !scene.requireNode(villager.id).enabled }
        val due=sim.returnAt.getValue(villager.id)
        assertTrue(due>=marked+20f)
        await("visible at edge after twenty world seconds",45000){
            villager.alive && scene.requireNode(villager.id).enabled && sim.seconds>=due &&
                (kotlin.math.abs(villager.point.x)>.38f || kotlin.math.abs(villager.point.z)>.27f)
        }
        main {
            val hunter=sim.wildlife.bodies.getValue("Sabertooth_0")
            for(id in listOf("MammothAdult_0","MammothAdult_1","Sabertooth_1"))
                sim.wildlife.bodies.getValue(id).point=nav(id).points.filter{sim.wildlife.open(id,it)}.maxBy{it.x+it.z}
            val center=LocalInterventionWorld.Point(-.22f,-.20f)
            calf.point=nav(calf.id).points.filter{sim.wildlife.open(calf.id,it)}.minBy{it.distance(center)}
            val from=LocalInterventionWorld.Point(calf.point.x-.05f,calf.point.z)
            hunter.point=nav(hunter.id).points.filter{sim.wildlife.open(hunter.id,it)}.minBy{it.distance(from)}
            hunter.rest=0f;hunter.hunger=1f
        }
        await("actual pursuit, contact, and feeding",85000){ !calf.alive && sim.wildlife.bodies.getValue("Sabertooth_0").kills>0 }
    }

    @Test fun timberBindingsAndCutEnds() {
        val ctx=instrumentation.targetContext
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("ready to inspect timber",75000){PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        var specimen: Entity?=null
        main {
            val rt=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
            val world=field<Entity>(rt,"world")
            val sample=world.requireNode("DroppedWood_0")
            val m=requireNotNull(sample.components.get<com.pico.spatial.core.ecs.ModelComponent>())
            val uv=m.mesh.getUv0()!!.toList().chunked(2)
            val xyz=m.mesh.getPositions().toList().chunked(3)
            val bands=uv.indices.filter { uv[it][0] in .635f.. .705f }
            val middle=xyz.map{it[0]}.let{(it.min()+it.max())/2}
            assertTrue(bands.count{xyz[it][0]<middle}>60 && bands.count{xyz[it][0]>middle}>60)
            assertTrue(uv.any{it[0]>.75f})
            if (InstrumentationRegistry.getArguments().getString("inspectTimber")=="1") {
                specimen=com.pico.spatial.core.ecs.ModelEntity(m.mesh,m.materials.toList().toTypedArray()).apply {
                    world.addChild(this)
                    val bounds=getVisualBounds(world,recursive=true,enabledOnly=true)
                    val scale=.27f/bounds.size.x
                    transform().scaleVector=Vector3(scale)
                    transform().eulerAngles=com.pico.spatial.core.math.EulerAngles(0f,35f,0f)
                    val center=getVisualBounds(world,recursive=true,enabledOnly=true).center
                    transform().position=Vector3(0f,.19f,0f)-center
                }
                Log.i("TimberInspect","native specimen ready")
            }
        }
        try {
            if(specimen!=null) {
                val until=SystemClock.elapsedRealtime()+55_000
                while(SystemClock.elapsedRealtime()<until && !ctx.filesDir.resolve("specimen-captured").isFile) SystemClock.sleep(300)
            }
        } finally { main {specimen?.destroy(true)} }
    }

    @Test fun fordHeightInResidentDiagnostics() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("Scene ready for ford crossing", 75000) { PrimitivePreviewRuntime.acceptanceInstance.get()?.ready == true }
        main {
            val runtime = requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
            val world = field<Entity>(runtime,"world")
            val effects = field<PrimitiveLocalEffects>(runtime,"localEffects")
            val residents = field<PrimitiveResidentsRuntime>(runtime,"residents")
            val fordX = -.07810679f
            val fordZ = .0069999993f
            assertNull(effects.drySupport(fordX,fordZ))
            val height = requireNotNull(effects.support(fordX,fordZ))
            val transform = world.requireNode("PrimitiveResident_0").transform()
            val previous = transform.position
            try {
                transform.position = Vector3(fordX,height,fordZ)
                residents.logDiagnostics()
            } finally { transform.position = previous }
        }
    }

    @Test fun targetedWeatherHouseFireAndDeliveredWood() {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("scene ready",60_000) {PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        val runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        lateinit var world: Entity;lateinit var effects: PrimitiveLocalEffects;lateinit var content: SpatialViewContent
        main {world=field(runtime,"world");effects=field(runtime,"localEffects");content=field(runtime,"content")}
        val sim=effects.simulation
        fun cast(tool:FireTool,p:Vector3,target:Entity) {
            val owner=field<Entity>(runtime,"placementOwner")
            val viewPoint=world.convertPositionTo(p,owner)
            val pixels=content.convertPosition(viewPoint,content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
            val back=world.convertPositionFrom(content.convertPosition(pixels,ViewCoordinateSpace.Local,content.localSpatialCoordinateSpace),owner)
            assertEquals(p.x,back.x,.0001f);assertEquals(p.z,back.z,.0001f)
            assertTrue("cast $tool at $p",runtime.castAtTap(target,pixels,tool))
        }
        fun enabled(prefix:String):Int {
            fun count(e:Entity):Int=(if(e.enabled && e.getName().startsWith(prefix))1 else 0)+e.getChildren().sumOf(::count)
            return count(world.requireNode("LocalWorldEffects"))
        }
        lateinit var actor: Entity
        lateinit var rainPoint: LocalInterventionWorld.Point
        main {
            actor=world.requireNode("PrimitiveRuntimeResidents").requireNode("PrimitiveResident_2")
            val p=actor.transform().position;rainPoint=LocalInterventionWorld.Point(p.x,p.z)
            cast(FireTool.WATER,p,world.requireNode("FloodplainTerrain"))
        }
        await("rain visible",3_000) {enabled("NativeRain_")==1}
        await("resident escaped rain",10_000) {
            val p=actor.transform().position
            LocalInterventionWorld.Point(p.x,p.z).distance(rainPoint)>=.068f
        }
        await("rain stops after fifteen scene seconds",20_000) {sim.hazards.none {it.kind==LocalInterventionWorld.Kind.RAIN} && enabled("NativeRain_")==0}
        var woodId=0
        main {
            val p=sim.navigation.points.filter {it.distance(sim.depot) in .025f.. .045f}.minBy {it.distance(rainPoint)}
            val previous=sim.woods.map{it.id}.toSet()
            cast(FireTool.WOOD,Vector3(p.x,requireNotNull(effects.support(p.x,p.z)),p.z),world.requireNode("FloodplainTerrain"))
            woodId=sim.woods.single{it.id !in previous}.id
        }
        await("wood picked up by a real resident",120_000) {sim.woods.any {it.id==woodId && it.phase==LocalInterventionWorld.WoodPhase.CARRIED && it.carrier!=null}}
        await("wood arrives at depot",60_000) {sim.woods.any {it.id==woodId && it.phase==LocalInterventionWorld.WoodPhase.STACKED}}
        SystemClock.sleep(5_000)
        main {assertTrue("delivered wood remains visible before thirty seconds",sim.woods.any {it.id==woodId && it.stackedAge<10f});assertTrue(enabled("DroppedWood_")>0)}
        await("delivered wood consumed after thirty seconds",40_000) {sim.woods.none {it.id==woodId}}
        main {
            val house=world.requireNode("Shelter_0");val b=house.getVisualBounds(world,recursive=true,enabledOnly=false)
            cast(FireTool.FIRE,Vector3(b.center.x,b.max.y,b.center.z),house)
            assertEquals("Shelter_0",sim.hazards.single().house)
        }
        await("roof flames visible",3_000) {enabled("NativeFire_")==1}
        await("fire fades only after fifteen scene seconds",20_000) {
            sim.hazards.firstOrNull {it.kind==LocalInterventionWorld.Kind.FIRE}?.let {it.age>=15.2f && it.strength<1f && it.strength>0f}==true
        }
        await("roof fire extinguishes",6_000) {sim.hazards.none {it.kind==LocalInterventionWorld.Kind.FIRE} && enabled("NativeFire_")==0}
        Log.i("LocalJourney","PASS all production-scene journeys; clock=real input=programmatic handInput=UNOBSERVED")
    }

    @Test fun rainDragAndWoodConsistency() {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("V8 scene ready",60_000) {PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        val runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        lateinit var world:Entity;lateinit var content:SpatialViewContent;lateinit var owner:Entity;lateinit var effects:PrimitiveLocalEffects
        main {world=field(runtime,"world");content=field(runtime,"content");owner=field(runtime,"placementOwner");effects=field(runtime,"localEffects")}
        val sim=effects.simulation
        fun pointer(id:String,p:Vector3,pressed:Boolean,down:Boolean):Boolean {
            val px=content.convertPosition(world.convertPositionTo(p,owner),content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
            return runtime.rainPointer(id,world.requireNode("FloodplainTerrain"),px,pressed,down)
        }
        val a=Vector3(-.25f,.034f,-.12f);val b=Vector3(.25f,.034f,.12f)
        var rainId=0
        var releasedAt=0f
        main {
            assertTrue(pointer("test-a",a,true,true));rainId=sim.hazards.single().id
            var bundles=0
            fun checkWood(e:Entity) {
                if(e.getName().startsWith("DroppedWood_") || e.getName().endsWith("_CarriedWood")) {
                    val bounds=e.getVisualBounds(e.getParent(),recursive=true,enabledOnly=false)
                    assertEquals("same portable bundle size ${e.getName()}",PrimitiveWoodAppearance.BUNDLE_LENGTH_METRES,maxOf(bounds.size.x,bounds.size.y,bounds.size.z),.00001f)
                    val model=requireNotNull(e.components.get<com.pico.spatial.core.ecs.ModelComponent>())
                    assertTrue("UV retained",model.mesh.getUv0()?.isNotEmpty()==true)
                    model.materials.forEach {m->assertTrue("original wood albedo bound",(m as com.pico.spatial.core.ecs.resource.UnlitMaterial).getBaseColorTextureParameter().texture.valid)}
                    bundles++
                }
                e.getChildren().forEach(::checkWood)
            }
            checkWood(world);assertEquals(6 + world.requireNode("PrimitiveRuntimeResidents").getChildren().size,bundles)
            Log.i("LocalJourney","PASS wood dimensions=${PrimitiveWoodAppearance.BUNDLE_LENGTH_METRES}m portableInstances=$bundles textured=true uv=true")
        }
        SystemClock.sleep(16_000)
        main {
            assertEquals(rainId,sim.hazards.single().id);assertTrue(sim.hazards.single().held);assertEquals(1f,sim.hazards.single().strength)
            assertTrue(pointer("test-a",b,true,false));assertEquals(rainId,sim.hazards.single().id)
            assertEquals(b.x,sim.hazards.single().point.x,.0001f);assertEquals(b.z,sim.hazards.single().point.z,.0001f)
            assertTrue(pointer("test-b",a,true,true));assertTrue(pointer("test-c",a,true,true));assertFalse(pointer("test-d",a,true,true));assertEquals(3,sim.hazards.size)
        }
        await("V8 three rain clouds follow held points",3_000) {
            val root=world.requireNode("LocalWorldEffects")
            val cloud=root.requireNode("NativeRain_$rainId")
            root.getChildren().count{it.enabled && it.getName().startsWith("NativeRain_")}==3 && kotlin.math.abs(cloud.transform().position.x-b.x)<.0001f
        }
        main {
            assertTrue(pointer("test-a",b,false,false));assertFalse(sim.hazards.first().held)
            assertFalse(pointer("test-a",a,false,false));assertEquals(b.x,sim.hazards.first().point.x,.0001f)
            runtime.cancelRainPointers();assertEquals(1,sim.hazards.size);assertEquals(rainId,sim.hazards.single().id)
            assertTrue(sim.hazards.single().age<.2f)
            releasedAt=sim.seconds
            Log.i("LocalJourney","PASS V8 hold>15s move preserves identity cap=3 release fixes position cancel reclaims held slots")
        }
        await("V8 release starts fifteen scene second expiry",60_000) {
            val age=sim.seconds-releasedAt
            val rain=sim.hazards.firstOrNull{it.id==rainId}
            if(age<14.8f)assertNotNull("rain must not expire before fifteen active scene seconds",rain)
            rain?.let { assertEquals("released rain age follows active scene time",age,it.age,.2f) }
            sim.hazards.isEmpty()
        }
        main {
            val elapsed=sim.seconds-releasedAt
            assertTrue("rain reached fifteen scene seconds before retiring: $elapsed",elapsed>=14.95f)
            assertTrue("rain retired promptly after fifteen scene seconds: $elapsed",elapsed<16f)
            Log.i("LocalJourney","PASS V8 expiry sceneElapsed=$elapsed lifetime=15s")
        }
        Log.i("LocalJourney","PASS V8 rain drag lifecycle and wood appearance; input=programmatic handTracking=UNOBSERVED")
    }
    @Test fun animatedFaunaAndThreeNativeFires() {
        val context=instrumentation.targetContext
        check(context.packageName.endsWith(".acceptance"))
        context.startActivity(context.packageManager.getLaunchIntentForPackage(context.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("V9 scene ready",60_000) {PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        val runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        lateinit var world:Entity;lateinit var effects:PrimitiveLocalEffects;lateinit var content:SpatialViewContent;lateinit var owner:Entity
        main {world=field(runtime,"world");effects=field(runtime,"localEffects");content=field(runtime,"content");owner=field(runtime,"placementOwner")}
        val sim=effects.simulation
        fun cast(tool:FireTool,p:Vector3,target:Entity):Boolean {
            val pixels=content.convertPosition(world.convertPositionTo(p,owner),content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
            return runtime.castAtTap(target,pixels,tool)
        }
        lateinit var mammoth:Entity;lateinit var tiger:Entity
        lateinit var m0:Vector3;lateinit var t0:Vector3
        main {
            mammoth=world.requireNode("MammothAdult_0");tiger=world.requireNode("Sabertooth_0")
            val skeletons=world.requireNode("LivingFauna").findSkeletonRootEntity(includeDisabled=true)
            assertEquals(5,skeletons.size);assertEquals(5,skeletons.map{it.id}.distinct().size)
            skeletons.forEach {s->assertTrue(s.getAnimationResources().map{it.getName()}.containsAll(listOf("idle","walk","flee")))}
            m0=mammoth.transform().position;t0=tiger.transform().position
            assertTrue(cast(FireTool.FIRE,m0,world.requireNode("FloodplainTerrain")))
            val house=world.requireNode("Shelter_0");val b=house.getVisualBounds(world,recursive=true,enabledOnly=false)
            assertTrue(cast(FireTool.FIRE,Vector3(b.center.x,b.max.y,b.center.z),house))
            assertTrue(cast(FireTool.FIRE,Vector3(.28f,.034f,.15f),world.requireNode("FloodplainTerrain")))
            assertFalse(cast(FireTool.FIRE,Vector3(.32f,.034f,.2f),world.requireNode("FloodplainTerrain")))
            assertEquals(3,sim.hazards.count{it.kind==LocalInterventionWorld.Kind.FIRE})
            assertTrue(cast(FireTool.WATER,t0,world.requireNode("FloodplainTerrain")))
            Log.i("FaunaJourney","CAPTURE_READY fire=3 rain=1 animals=5")
        }
        await("V9 native fire emitters material and bounded budget",5_000) {
            val root=world.requireNode("LocalWorldEffects")
            val fires=root.getChildren().filter{it.getName().startsWith("NativeFire_")}
            if(fires.size!=3)false else {
                var count=0
                fires.forEach{f->assertEquals(3,f.getChildren().size);f.getChildren().forEach{e->
                    val pc=requireNotNull(e.components.get<com.pico.spatial.core.ecs.ParticleComponent>())
                    assertTrue(pc.isEmitting);assertTrue(pc.mainSimulation.material.baseColorMap.valid)
                    assertEquals(com.pico.spatial.core.ecs.particle.ParticleMaterialType.DEFAULT_UNLIT,pc.mainSimulation.material.materialType)
                    count+=pc.mainSimulation.maxParticleCount
                }}
                assertEquals(408,count);true
            }
        }
        await("V9 mammoth escapes fire and tiger escapes rain",14_000) {
            val m=mammoth.transform().position;val t=tiger.transform().position
            kotlin.math.hypot(m.x-m0.x,m.z-m0.z)>.081f && kotlin.math.hypot(t.x-t0.x,t.z-t0.z)>.080f
        }
        await("V9 fifteen seconds begins fire fade",22_000) {sim.hazards.any{it.kind==LocalInterventionWorld.Kind.FIRE && it.age>15.1f && it.strength<1f}}
        await("V9 all rain fire retired",6_000) {sim.hazards.isEmpty() && world.requireNode("LocalWorldEffects").getChildren().none{it.getName().startsWith("NativeFire_")}}
        main {
            val m=mammoth.transform().position;val t=tiger.transform().position
            assertTrue(cast(FireTool.WATER,m,world.requireNode("FloodplainTerrain")))
            assertTrue(cast(FireTool.FIRE,t,world.requireNode("FloodplainTerrain")))
            m0=m;t0=t
            Log.i("FaunaJourney","CAPTURE_SECOND rain=mammoth fire=tiger")
        }
        await("V9 mammoth escapes rain and tiger escapes fire",14_000) {
            val m=mammoth.transform().position;val t=tiger.transform().position
            kotlin.math.hypot(m.x-m0.x,m.z-m0.z)>.091f && kotlin.math.hypot(t.x-t0.x,t.z-t0.z)>.070f
        }
        // Escape can finish several seconds before the weather. Measure the
        // remaining lifetime against the simulation, not the last animal step.
        await("V9 second effects retire",24_000){sim.hazards.isEmpty()}
        main { assertTrue(world.requireNode("LocalWorldEffects").getChildren().none{it.getName().startsWith("NativeFire_")}) }
        Log.i("FaunaJourney","PASS V9 animals=5 skeletons=5 bothSpeciesAvoidRainAndFire=true fireCap=3 nativeEmitters=9 particleCap=408 input=programmatic handInput=UNOBSERVED")
    }

    @Test fun atmosphereAndTimber() {
        val ctx=instrumentation.targetContext;check(ctx.packageName.endsWith(".acceptance"))
        kotlinx.coroutines.runBlocking {com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository(ctx.filesDir).save(com.example.cornercivilization.p37f776dc.domain.model.FireSliceState(evolutionMillis=600_000,fieldEstablished=true))}
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("V10 ready",60_000){PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        val runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        lateinit var world:Entity;lateinit var effects:PrimitiveLocalEffects;lateinit var sounds:PrimitiveWorldAudio
        main{world=field(runtime,"world");effects=field(runtime,"localEffects");sounds=field(runtime,"audio")}
        await("V10 default birds native audio playing",10_000){sounds.starts>0}
        await("established plot is active",8_000){world.requireNode("ContourField").enabled}
        main {
            assertTrue(world.requireNode("ContourField").enabled)
            assertTrue(world.requireNode("CultivatedSoil").enabled)
            val points=listOf(world.requireNode("PrimitiveResident_2").transform().position,world.requireNode("MammothAdult_0").transform().position,world.requireNode("Sabertooth_0").transform().position)
            points.forEach{assertTrue(effects.cast(FireTool.WATER,it))}
            assertFalse(effects.cast(FireTool.WATER,Vector3(.2f,.034f,.12f)))
            assertTrue(effects.cast(FireTool.FIRE,Vector3(.26f,.034f,.20f)))
            val depot=effects.simulation.depot
            repeat(6){assertTrue(effects.simulation.dropWood(depot));effects.simulation.woods.last().phase=LocalInterventionWorld.WoodPhase.STACKED}
            Log.i("AtmosphereJourney","CAPTURE_READY V10 three rains fire sounds field six-pile visual fixture")
        }
        await("V10 native rain and timber arrangement",4_000) {
            val root=world.requireNode("LocalWorldEffects");val rains=root.getChildren().filter{it.getName().startsWith("NativeRain_")}
            if(rains.size!=3)false else {
                rains.forEach{r->assertEquals(6,r.getChildren().first{it.getName().startsWith("RainCloud_")}.getChildren().size)
                    val p=requireNotNull(r.requireNode("RainStreaks").components.get<com.pico.spatial.core.ecs.ParticleComponent>());assertEquals(96,p.mainSimulation.maxParticleCount);assertTrue(p.mainSimulation.material.baseColorMap.valid)}
                val piles=(0..5).map{root.requireNode("DroppedWood_$it")}
                assertTrue(piles.map{it.transform().position.z}.distinct().size>=3)
                val ys=piles.map{it.transform().position.y};val height=piles.first().getVisualBounds(root,recursive=true,enabledOnly=true).size.y
                assertTrue("three interlocking layers",ys.max()-ys.min() in height*1.3f..height*2.2f)
                piles.forEach{e->val m=requireNotNull(e.components.get<com.pico.spatial.core.ecs.ModelComponent>());assertTrue(m.mesh.getUv0()!!.isNotEmpty());assertTrue((m.materials[0] as com.pico.spatial.core.ecs.resource.UnlitMaterial).getBaseColorTextureParameter().texture.valid)}
                true
            }
        }
        await("V10 audio event sources",4_000){sounds.starts>=8}
        main {
            val loops=field<Map<Int,Any>>(sounds,"loops");assertEquals(4,loops.size)
            loops.values.forEach{v->val c=field<com.pico.spatial.core.ecs.audio.AudioPlayerController>(v,"player");assertTrue(c.valid);assertTrue(c.isPlaying());assertTrue(c.getCurrentPosition()>0)}
            Log.i("AtmosphereJourney","PASS audio playing=true positionAdvances=true sources=4 reactionsTriggered=true")
        }
        await("V10 rain expires",18_000){effects.simulation.hazards.none{it.kind==LocalInterventionWorld.Kind.RAIN}}
        await("V10 fire expires",5_000){effects.simulation.hazards.isEmpty()}
        main{sounds.update(0f,false);assertTrue(field<Map<Int,Any>>(sounds,"loops").isEmpty());assertTrue(field<List<Any>>(sounds,"shots").isEmpty())}
        Log.i("AtmosphereJourney","PASS V10 rain fire timber field audio suspend; sound character subjective=UNOBSERVED input=programmatic")
    }

    @Test fun livingWorldJourney() {
        val ctx=InstrumentationRegistry.getInstrumentation().targetContext
        check(ctx.packageName.endsWith(".acceptance")) { "Never reset player saves in the playable package" }
        java.io.File(ctx.filesDir,"living-village-v11.json").delete()
        kotlinx.coroutines.runBlocking { com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository(ctx.filesDir).save(
            com.example.cornercivilization.p37f776dc.domain.model.FireSliceState(evolutionMillis=600_000,
                shelter=com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase.COMPLETE,buildMillis=12_000,sourceWood=0,spentWood=8)) }
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("V11 scene ready",75_000){PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        var runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        lateinit var world:Entity;lateinit var effects:PrimitiveLocalEffects
        main{world=field(runtime,"world");effects=field(runtime,"localEffects")}
        val v=effects.living;val h=v.houses.getValue("Shelter_0")
        main {
            assertTrue(effects.cast(FireTool.FIRE,Vector3(h.center.x,.04f,h.center.z),h.id))
            assertTrue(effects.cast(FireTool.WATER,Vector3(.26f,.034f,-.13f)))
            Log.i("VillageJourney","CAPTURE_BURN house=${h.id} native=true")
        }
        await("V11 genuine fire damage and collapse",18_000){h.ruined&&h.health==0f&&world.requireNode("Shelter_0").transform().scaleVector.y<.1f}
        main {assertTrue(world.requireNode("Shelter_0").transform().scaleVector.y<.1f);Log.i("VillageJourney","RUIN confirmed native=true")}
        await("V11 autonomous gathering and real site delivery",150_000){v.delivered>=1}
        main{Log.i("VillageJourney","CAPTURE_REBUILD delivered=${v.delivered} collected=${v.collected} materials=${h.materials}")}
        await("V11 real construction completes",160_000){h.rebuilds>=1&&h.health==100f&&world.requireNode("Shelter_0").transform().scaleVector.y>.1f}
        main {
            assertTrue(v.delivered>=3);assertTrue(v.collected>=3)
            assertTrue(v.completedTasks>=3)
            assertTrue(world.requireNode("Shelter_0").transform().scaleVector.y>.1f)
            Log.i("VillageJourney","REBUILT delivered=${v.delivered} collected=${v.collected} tasks=${v.completedTasks}")
            val h2=v.houses.getValue("Shelter_1")
            assertTrue(effects.cast(FireTool.FIRE,Vector3(h2.center.x,.04f,h2.center.z),h2.id))
        }
        val rescued=v.houses.getValue("Shelter_1")
        await("V11 damage before rescue",5_000){rescued.health<85f}
        main{assertTrue(effects.cast(FireTool.WATER,Vector3(rescued.center.x,.05f,rescued.center.z)))}
        await("V11 rain saves house",4_000){effects.simulation.hazards.none{it.kind==LocalInterventionWorld.Kind.FIRE}}
        main{assertFalse(rescued.ruined);assertTrue(rescued.health>40f);Log.i("VillageJourney","RESCUED health=${rescued.health}")
            assertTrue(effects.cast(FireTool.WATER,Vector3(-.055f,.034f,.285f)))
        }
        await("V11 rain-fed field and grass",25_000){effects.simulation.wetSpots.any{it.point.distance(LocalInterventionWorld.Point(-.055f,.285f))<.035f&&it.growth>.6f}}
        main {
            val roots=world.requireNode("VillageConsequences").getChildren()
            assertTrue(roots.any{it.getName().startsWith("RainGrowth_")&&it.enabled})
            assertTrue(world.requireNode("ContourField").getChildren().count { it.enabled && it.getName().startsWith("FieldShoot") }>0)
            val fauna:PrimitiveFaunaRuntime=field(runtime,"fauna")
            val actors=field<List<Any>>(fauna,"actors")
            actors.forEach {a->val clips=field<Map<String,Any>>(a,"clips");assertTrue(clips.keys.containsAll(listOf("graze","drink","rest")))}
            field<LivingVillageStore>(runtime,"villageStore").tick(0f,true)
        }
        await("V11 persisted house outcome",5_000){java.io.File(ctx.filesDir,"living-village-v11.json").takeIf{it.exists()}?.readText()?.contains("\"rebuilds\":1")==true}
        val old=runtime
        main {
            val activities=androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
            activities.first().recreate()
        }
        await("V11 recreate and restore",75_000){PrimitivePreviewRuntime.acceptanceInstance.get()?.let{it!==old&&it.ready}==true}
        runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        main {
            val restored:PrimitiveLocalEffects=field(runtime,"localEffects")
            assertEquals(1,restored.living.houses.getValue("Shelter_0").rebuilds)
            assertTrue(restored.living.delivered>=3)
            assertTrue(restored.simulation.hazards.isEmpty())
            Log.i("VillageJourney","PASS destruction, collection, transport, rebuild, rain rescue, growth, animations, save restore; input=programmatic")
        }
    }

    @Test fun fieldAndVillageVisitors() {
        val ctx=instrumentation.targetContext
        check(ctx.packageName.endsWith(".acceptance"))
        java.io.File(ctx.filesDir,"living-village-v11.json").delete()
        kotlinx.coroutines.runBlocking {
            com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository(ctx.filesDir).save(
                com.example.cornercivilization.p37f776dc.domain.model.FireSliceState(evolutionMillis=600_000))
        }
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("V12 ready",75_000){PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        val rt=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
        val world=field<Entity>(rt,"world")
        val effects=field<PrimitiveLocalEffects>(rt,"localEffects")
        val village=effects.living
        val life=village.ecology
        await("Agricultural save applies to field",8_000){village.agriculture && world.requireNode("ContourField").enabled}
        main {
            val plot=world.requireNode("ContourField")
            assertTrue(plot.enabled);assertEquals(40,plot.getChildren().count { it.getName().startsWith("FieldShoot") })
            assertTrue(world.requireNode("CultivatedSoil").enabled)
            assertTrue(world.requireNode("VisitingDeer_0").enabled)
        }
        await("Mammoth reached the crop and actually fed",110_000){life.raids>0}
        main { assertTrue(effects.cast(FireTool.WATER,Vector3(.018f,.034f,.231f)));Log.i("FarmJourney","RAID raids=${life.raids} crop=${life.crop}") }
        await("Rain fed grass away from field",32_000){effects.simulation.wetSpots.any { it.growth>.4f }}
        await("Mammoth leaves field",60_000){life.mammoth?.distance(life.field)?.let { it>.045f }==true}
        await("Tiger visits village edge",120_000){life.tiger!=null}
        Log.i("FarmJourney","PASS native plot, wildlife visits, raid and rain")
    }
}
