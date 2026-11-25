package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Fixtures reposition isolated animals; every measured consequence uses the real frame loop.
 * Does not call decide/settle/animate or modify damage, kill, or repair counters. */
@RunWith(AndroidJUnit4::class)
class WildlifeJourneyTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner:Any,name:String):T=owner.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(owner) as T}
    private lateinit var runtime:PrimitivePreviewRuntime
    private lateinit var world:Entity
    private lateinit var effects:PrimitiveLocalEffects
    private lateinit var wildlife:WildlifeWorld
    private lateinit var actors:List<Any>
    private var samples=0
    private fun mind(id:String):PrimitiveFaunaMind {
        val a=actors.single{field<FaunaPlacement>(it,"placement").name==id}
        return field(a,"mind")
    }
    private fun checkBodies() {
        if(!::wildlife.isInitialized)return
        for(retired in wildlife.bodies.values.filter{it.returnedToSoil}) {
            assertFalse("returned animal should be hidden",world.requireNode(retired.id).enabled)
        }
        val animals=wildlife.bodies.values.filter{it.species!=WildlifeWorld.Species.RESIDENT && !it.returnedToSoil}
        wildlife.bodies.values.filter{it.species==WildlifeWorld.Species.RESIDENT}.forEach{r->
            val p=world.requireNode(r.id).transform().position
            assertEquals(r.point.x,p.x,.00001f);assertEquals(r.point.z,p.z,.00001f)
            animals.forEach{a->assertTrue("resident/animal crossing ${r.id} ${a.id} distance=${r.point.distance(a.point)} required=${r.radius+a.radius}",
                r.point.distance(a.point)>=r.radius+a.radius-.0002f)}
        }
        animals.forEach{a->
            val p=world.requireNode(a.id).transform().position
            assertEquals(a.point.x,p.x,.00001f);assertEquals(a.point.z,p.z,.00001f)
        }
        samples++
    }
    private fun await(label:String,timeout:Long,predicate:()->Boolean) {
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<timeout) {
            var done=false
            main {checkBodies();done=predicate()}
            if(done){Log.i("WildlifeJourney","PASS $label elapsed=${SystemClock.elapsedRealtime()-start} samples=$samples");return}
            SystemClock.sleep(100)
        }
        main {
            Log.e("WildlifeJourney","TIMEOUT $label")
            wildlife.bodies.values.forEach{Log.e("WildlifeJourney","body=$it order=${wildlife.order(it.id)}")}
            effects.living.houses.values.forEach{Log.e("WildlifeJourney","house=$it")}
            effects.living.jobs.forEach{(id,j)->Log.e("WildlifeJourney","job=$id $j")}
            Log.e("WildlifeJourney","wood=${effects.simulation.woods}")
        }
        fail("Timed out: $label")
    }
    private fun place(id:String,near:Point) {
        val b=wildlife.bodies.getValue(id)
        check(b.alive) { "Fixture cannot reposition a fallen animal: $id" }
        val p=mind(id).navigation.points.filter{wildlife.open(id,it)}.minBy{it.distance(near)}
        b.point=p;b.target=null;b.attackTime=0f;b.goal=null;b.decision=20f
        // Match presentation immediately so the first sample measures the fixture consistently.
        val e=world.requireNode(id);e.transform().position=Vector3(p.x,requireNotNull(effects.support(p.x,p.z)),p.z)
    }
    private fun distantAnimals() {
        place("MammothAdult_0",Point(.35f,-.20f))
        place("MammothAdult_1",Point(.36f,.24f))
        place("Sabertooth_0",Point(-.35f,-.22f))
        place("Sabertooth_1",Point(-.35f,-.12f))
        wildlife.bodies.values.filter{it.species==WildlifeWorld.Species.TIGER}.forEach{it.rest=60f;it.hunger=0f}
    }
    /** Observe the existing village after an upgrade, without changing an animal or its orders. */
    @Test fun restoredBankAnimalsActuallyTravel() {
        val ctx=ins.targetContext
        check(ctx.packageName.endsWith(".living.acceptance"))
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val started=SystemClock.elapsedRealtime()
        var ready=false
        while(!ready && SystemClock.elapsedRealtime()-started<90_000) {
            main {ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
            SystemClock.sleep(100)
        }
        assertTrue("Scene ready",ready)
        val ids=listOf("MammothAdult_1","Sabertooth_0")
        val starts=mutableMapOf<String,Point>()
        val peak=mutableMapOf<String,Float>()
        val firstDrinks=mutableMapOf<String,Int>()
        var seenReturned=false
        var ticks=0
        val until=SystemClock.elapsedRealtime()+100_000
        while(SystemClock.elapsedRealtime()<until) {
            main {
                runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
                world=field(runtime,"world")
                effects=field(runtime,"localEffects")
                wildlife=effects.simulation.wildlife
                val calf=wildlife.bodies.getValue("MammothCalf")
                if(calf.returnedToSoil){
                    assertFalse(world.requireNode(calf.id).enabled)
                    assertFalse(calf.alive)
                    seenReturned=true
                }
                ids.forEach { id->
                    val b=wildlife.bodies.getValue(id)
                    val node=world.requireNode(id)
                    val pos=node.transform().position
                    assertTrue(node.enabled && b.alive)
                    assertEquals(b.point.x,pos.x,.00001f)
                    assertEquals(b.point.z,pos.z,.00001f)
                    assertEquals(requireNotNull(effects.drySupport(pos.x,pos.z)),pos.y,.00002f)
                    val distance=b.point.distance(starts.getOrPut(id){b.point})
                    peak[id]=maxOf(peak[id]?:0f,distance)
                    val state=effects.living.animalLife.states.getValue(id)
                    firstDrinks.putIfAbsent(id,state.drinks)
                    if(ticks%16==0)Log.i("BankAnimals","id=$id foot=$pos displacement=$distance order=${wildlife.order(id)} intent=${state.intent} drinks=${state.drinks}")
                }
                ticks++
            }
            if(ticks>85 && ids.all{(peak[it]?:0f)>.012f})break
            SystemClock.sleep(450)
        }
        Log.i("BankAnimals","result displacement=$peak returnedHidden=$seenReturned samples=$ticks")
        assertTrue("Each visible animal must travel over 12mm in model space: $peak",ids.all{(peak[it]?:0f)>.012f})
        if(effects.living.animalLife.states.values.any{it.carcassReturned})assertTrue(seenReturned)
    }

    @Test fun youngsterLeavesGrazingAdultsOnFoot() {
        val ctx=ins.targetContext
        check(ctx.packageName.contains(".qa175."))
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<90_000) {
            var ready=false
            main { ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true }
            if(ready)break
            SystemClock.sleep(120)
        }
        var origin=Point(0f,0f)
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get());world=field(runtime,"world")
            effects=field(runtime,"localEffects");wildlife=effects.simulation.wildlife
            actors=field(field<PrimitiveFaunaRuntime>(runtime,"fauna"),"actors")
            // Skip the initial quiet interval. Movement, pasture choice and all body collisions remain live.
            origin=wildlife.bodies.getValue("MammothCalf").point
            effects.living.life.clock+=72f
        }
        var left=false
        await("calf travelled away from both adults to a reachable pasture",85_000) {
            val calf=wildlife.bodies.getValue("MammothCalf")
            val adults=wildlife.bodies.values.filter { it.species==WildlifeWorld.Species.ADULT }
            val order=wildlife.order(calf.id)
            if(order?.brisk==true && order.point?.distance(origin)?.let { it>.08f }==true)left=true
            val gap=adults.minOf { it.point.distance(calf.point) }
            val result=left && calf.point.distance(origin)>.04f && gap>.12f
            if(result)Log.i("HerdRest","OUTING origin=$origin at=${calf.point} nearestAdult=$gap")
            result
        }
    }

    @Test fun shelteredRestAndHerdReturn() {
        val ctx=ins.targetContext
        check(ctx.packageName.contains(".qa175."))
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<90_000) {
            var ready=false
            main {ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
            if(ready)break
            SystemClock.sleep(120)
        }
        val id="PrimitiveResident_1"
        lateinit var seat:Point
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get());world=field(runtime,"world")
            effects=field(runtime,"localEffects");wildlife=effects.simulation.wildlife
            actors=field(field<PrimitiveFaunaRuntime>(runtime,"fauna"),"actors")
            val v=effects.living
            seat=v.restSeats.getValue("Shelter_0")
            val lead=wildlife.bodies.getValue("MammothAdult_0")
            assertTrue(wildlife.bodies.getValue("MammothAdult_1").point.distance(lead.point)<.12f)
            assertTrue(wildlife.bodies.getValue("MammothCalf").point.distance(lead.point)<.12f)
            val door=v.houses.getValue("Shelter_0").workPoint
            val at=effects.simulation.navigation.points.filter { wildlife.open(id,it) && it.z>=door.z && it.distance(seat)>.020f }.minBy { it.distance(door) }
            val r=wildlife.bodies.getValue(id);r.point=at
            world.requireNode(id).transform().position=Vector3(at.x,requireNotNull(effects.support(at.x,at.z)),at.z)
            v.interrupt(id,at)
            v.life.person(id).apply { fatigue=.99f;hunger=.05f;thirst=.05f;fear=0f }
            Log.i("HerdRest","start=$at door=$door seat=$seat")
        }
        await("arrived under eave and native sit clip active",45_000) {
            val job=effects.living.jobs[id]
            if(job?.event!=VillageEvent.SLEEP_HOME || job.elapsed !in 1.3f..8.5f)false else {
                val r=field<PrimitiveResidentsRuntime>(runtime,"residents")
                val actor=field<List<Any>>(r,"actors").single { field<String>(it,"id")==id }
                val diag=field<PrimitiveSkeletalPlayer>(actor,"player").diagnostic()
                val at=wildlife.bodies.getValue(id).point
                Log.i("HerdRest","SEATED actual=$at goal=$seat work=${job.elapsed} $diag")
                at.distance(seat)<.002f && diag.contains("clip=SIT_REST")
            }
        }
        await("rest restored fatigue and resident walked back out",45_000) {
            effects.living.life.person(id).fatigue<.4f && wildlife.bodies.getValue(id).point.distance(seat)>.015f
        }
        main {
            distantAnimals()
            place("MammothCalf",Point(-.29f,-.18f))
            val hunter=wildlife.bodies.getValue("Sabertooth_0")
            val calf=wildlife.bodies.getValue("MammothCalf")
            val close=mind(hunter.id).navigation.points.filter { wildlife.open(hunter.id,it) &&
                it.distance(calf.point) in (hunter.radius+calf.radius+.001f)..(hunter.radius+calf.radius+.014f) }.minBy { it.distance(calf.point) }
            place(hunter.id,close);hunter.hunger=1f;hunter.rest=0f
        }
        await("hungry tiger caught separated calf",60_000) { !wildlife.bodies.getValue("MammothCalf").alive }
        var due=0f
        main {
            due=effects.living.animalLife.states.getValue("MammothCalf").nextCalfAt
            assertTrue(due-effects.living.life.clock in 29f..30.01f)
        }
        await("carcass retired",30_000) { wildlife.bodies.getValue("MammothCalf").returnedToSoil }
        await("new calf appeared at safe edge on time",65_000) {
            val c=wildlife.bodies.getValue("MammothCalf")
            if(!c.alive)false else {
                val now=effects.living.life.clock
                Log.i("HerdRest","ENTRY clock=$now deadline=$due point=${c.point} health=${c.health}")
                assertTrue(now-due in 0f..1.5f)
                assertTrue(abs(c.point.x)>.335f || abs(c.point.z)>.246f)
                assertEquals(100f,c.health,0f)
                true
            }
        }
    }

    @Test fun nativeFearPredationImpactRepairAndRestore() {
        val ctx=ins.targetContext;check(ctx.packageName.endsWith(".acceptance"))
        java.io.File(ctx.filesDir,"living-village-v11.json").delete()
        kotlinx.coroutines.runBlocking{FireSliceRepository(ctx.filesDir).save(
            FireSliceState(fieldEstablished=true,shelter=ShelterPhase.COMPLETE,buildMillis=12_000,spentWood=8,sourceWood=0))}
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<75_000) {
            var ready=false;main{ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
            if(ready)break
            SystemClock.sleep(100)
        }
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get());assertTrue(runtime.ready)
            world=field(runtime,"world");effects=field(runtime,"localEffects");wildlife=effects.simulation.wildlife
            actors=field(field<PrimitiveFaunaRuntime>(runtime,"fauna"),"actors")
            assertEquals(14,wildlife.bodies.size)
            field<PrimitiveResidentsRuntime>(runtime,"residents").logDiagnostics()
            distantAnimals()
            val resident=wildlife.bodies.getValue("PrimitiveResident_7")
            val encounter=effects.simulation.navigation.points.filter { it.x<-.22f && it.z<-.10f && wildlife.open(resident.id,it) }
                .minBy { it.distance(Point(-.25f,-.12f)) }
            resident.point=encounter
            world.requireNode(resident.id).transform().position=Vector3(encounter.x,requireNotNull(effects.support(encounter.x,encounter.z)),encounter.z)
            val a=wildlife.bodies.getValue("MammothAdult_1")
            val near=mind(a.id).navigation.points.filter{wildlife.open(a.id,it) &&
                it.distance(resident.point) in (a.radius+resident.radius+.004f)..(a.radius+.025f)}.minBy{it.distance(resident.point)}
            place(a.id,near)
            Log.i("WildlifeJourney","FIXTURE fear resident=${resident.point} mammoth=${a.point} reach=${a.radius+.037f}")
        }
        await("resident fears mammoth and retreats without crossing",15_000) {
            val r=wildlife.bodies.getValue("PrimitiveResident_7");val a=wildlife.bodies.getValue("MammothAdult_1")
            r.point.distance(a.point)>=a.radius+.037f
        }
        main {
            distantAnimals()
            val resident=wildlife.bodies.getValue("PrimitiveResident_7")
            val t=wildlife.bodies.getValue("Sabertooth_1")
            val near=mind(t.id).navigation.points.filter{wildlife.open(t.id,it) &&
                it.distance(resident.point) in (t.radius+resident.radius+.004f)..(t.radius+.050f)}.minBy{it.distance(resident.point)}
            place(t.id,near);t.rest=60f
        }
        await("resident fears tiger and retreats without crossing",15_000) {
            val r=wildlife.bodies.getValue("PrimitiveResident_7");val t=wildlife.bodies.getValue("Sabertooth_1")
            r.point.distance(t.point)>=t.radius+.062f
        }
        main {
            distantAnimals()
            place("MammothCalf",Point(.245f,-.18f))
            place("MammothAdult_0",Point(.19f,-.18f))
            val mother=wildlife.bodies.getValue("MammothAdult_0")
            val calf=wildlife.bodies.getValue("MammothCalf")
            val hunter=wildlife.bodies.getValue("Sabertooth_0")
            val guarded=mind(hunter.id).navigation.points.filter { site ->
                wildlife.open(hunter.id,site) && site.distance(mother.point)<.065f &&
                    site.distance(calf.point) in .060f.. .100f
            }.minBy { it.distance(mother.point) }
            place(hunter.id,guarded)
            val t=wildlife.bodies.getValue("Sabertooth_0");t.rest=0f;t.hunger=1f
        }
        await("adult protects calf during actual hungry tiger approach",18_000) {wildlife.protections>0}
        main {assertTrue(wildlife.bodies.getValue("MammothCalf").alive)}
        await("tiger yields to adult protector",20_000) {
            val tiger=wildlife.bodies.getValue("Sabertooth_0")
            tiger.rest>0f && tiger.kills==0 && wildlife.bodies.getValue("MammothCalf").alive
        }
        main {
            distantAnimals()
            // Keep adults across the river: the earlier fixture let the fleeing calf
            // reach its adult and correctly become protected again before the final attack.
            place("MammothCalf",Point(-.22f,-.20f))
            place("Sabertooth_0",Point(-.275f,-.20f))
            val t=wildlife.bodies.getValue("Sabertooth_0");t.rest=0f;t.hunger=1f
            Log.i("WildlifeJourney","CAPTURE_HUNT calf=${wildlife.bodies.getValue("MammothCalf").point} tiger=${t.point}")
        }
        await("tiger runs after unprotected calf and makes contact",30_000) {wildlife.bodies.getValue("MammothCalf").health<100f}
        await("predation produces prey consequence and feeding",75_000) {
            !wildlife.bodies.getValue("MammothCalf").alive && wildlife.bodies.getValue("Sabertooth_0").kills==1
        }
        await("calf lies on its flank with its head level and its side on the ground",2_000) {
            val calf=world.requireNode("MammothCalf")
            val pivot=calf.requireNode("CalfFlankPivot")
            val origin=pivot.convertPositionTo(Vector3.ZERO,world)
            fun rise(axis:Vector3)=abs((pivot.convertPositionTo(axis,world)-origin).y)
            val spine=rise(Vector3(1f,0f,0f))
            val back=rise(Vector3(0f,1f,0f))
            val flank=rise(Vector3(0f,0f,1f))
            val at=wildlife.bodies.getValue("MammothCalf").point
            // Extremal side vertices from the shipped GLB POSITION accessor,
            // in model space and scaled by the actual calf height (0.021 m).
            val halfWidth=.22640108f*.021f
            val low=minOf(pivot.convertPositionTo(Vector3(0f,0f,halfWidth),world).y,
                pivot.convertPositionTo(Vector3(0f,0f,-halfWidth),world).y)
            val gap=low-requireNotNull(effects.support(at.x,at.z))
            val passed=spine<.015f && back<.015f && flank>.985f && abs(gap)<.00025f
            Log.i("WildlifeJourney","FLANK spineRise=$spine backRise=$back sideRise=$flank supportGap=$gap")
            passed
        }
        // Optional close-up of the same native animal after the unscaled posture was verified.
        if (InstrumentationRegistry.getArguments().getString("inspectCalf")=="1") {
            check(ctx.packageName.contains(".qa174."))
            lateinit var calf:Entity
            lateinit var saved:Vector3
            main {
                calf=world.requireNode("MammothCalf")
                saved=calf.transform().position
                calf.transform().scaleVector=Vector3(8f)
                val support=requireNotNull(effects.support(saved.x,saved.z))
                calf.transform().position=saved+Vector3(0f,(saved.y-support)*7f,0f)
                Log.i("WildlifeJourney","CALF_CLOSEUP")
            }
            try { SystemClock.sleep(7_000) }
            finally { main { calf.transform().scaleVector=Vector3(1f);calf.transform().position=saved } }
        }
        main {
            // Even inviting grass at a roof cannot pull a living animal into the village.
            val house=effects.living.houses.getValue("Shelter_2")
            val region=Point(house.center.x+.025f,house.center.z)
            for (id in listOf("MammothAdult_0","MammothCalf","Sabertooth_0")) {
                assertFalse(mind(id).navigation.open(region))
                assertFalse(effects.simulation.wildFoodAllowed(wildlife.bodies.getValue(id),region))
            }
            field<LivingVillageStore>(runtime,"villageStore").tick(0f,true)
        }
        await("hunting history saved",8_000) {
            java.io.File(ctx.filesDir,"living-village-v11.json").takeIf{it.exists()}?.readText()?.contains("\"kills\":1")==true
        }
        val old=runtime
        main {
            androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED).first().recreate()
        }
        val restoreStart=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-restoreStart<75_000) {
            var done=false;main{done=PrimitivePreviewRuntime.acceptanceInstance.get()?.let{it!==old&&it.ready}==true}
            if(done)break
            SystemClock.sleep(100)
        }
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get());assertNotSame(old,runtime);assertTrue(runtime.ready)
            world=field(runtime,"world");effects=field(runtime,"localEffects");wildlife=effects.simulation.wildlife
            assertFalse(wildlife.bodies.getValue("MammothCalf").alive)
            assertEquals(1,wildlife.bodies.getValue("Sabertooth_0").kills)
            checkBodies()
            Log.i("WildlifeJourney","PASS full native wildlife story samples=$samples residentDeaths=0 clock=real fixtures=programmatic handTracking=UNOBSERVED")
        }
    }
}
