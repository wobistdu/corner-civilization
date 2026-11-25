package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.model.ShelterPhase
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Only initial needs/resources/animal placement are fixtures. Consequences use live frames.
 * The second method must run in a separate instrumentation process after external force-stop. */
@RunWith(AndroidJUnit4::class)
class LivingJourneyTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=ins.targetContext
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner:Any,name:String):T=owner.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(owner) as T}
    private lateinit var runtime:PrimitivePreviewRuntime
    private lateinit var world:Entity
    private lateinit var effects:PrimitiveLocalEffects
    private val village get()=effects.living
    private val life get()=village.life
    private var samples=0
    private fun launch() {
        check(ctx.packageName.endsWith(".living.acceptance"))
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<75_000) {
            var ready=false
            main { ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true }
            if(ready)break
            SystemClock.sleep(100)
        }
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get());assertTrue(runtime.ready)
            world=field(runtime,"world");effects=field(runtime,"localEffects")
            assertTrue(life.enabled)
            field<PrimitiveResidentsRuntime>(runtime,"residents").logDiagnostics()
        }
    }
    private fun invariant() {
        val bodies=effects.simulation.wildlife.bodies.values
        val residents=bodies.filter{it.species==WildlifeWorld.Species.RESIDENT}
        assertEquals(9,residents.size)
        assertTrue(residents.all{it.alive})
        val animals=bodies.filter{it.species!=WildlifeWorld.Species.RESIDENT}
        residents.forEach { r->
            val p=world.requireNode(r.id).transform().position
            assertEquals(r.point.x,p.x,.00001f);assertEquals(r.point.z,p.z,.00001f)
            animals.forEach { a->assertTrue("crossing ${r.id}/${a.id}",r.point.distance(a.point)>=r.radius+a.radius-.0002f) }
        }
        assertEquals("food conservation",life.foodBaseline+life.foodProduced,
            village.ecology.food+village.foodBundles.size+life.foodEaten+life.foodLost+life.foodInvested)
        assertTrue(village.ecology.food in 0..16)
        assertTrue(village.foodBundles.size+village.ecology.food<=16)
        assertTrue(effects.simulation.woods.size<=6)
        assertTrue(life.patches.all{it.berries in 0f..3f && it.branches in 0..2})
        assertTrue(life.water in 0f..2f && life.reeds in 0..6 && life.bankReeds in 0f..6f)
        assertTrue(life.journal.recent.size<=96)
        samples++
    }
    private fun await(label:String,timeout:Long=90_000,predicate:()->Boolean) {
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<timeout) {
            var done=false
            main {invariant();done=predicate()}
            if(done){Log.i("LivingJourney","PASS $label elapsed=${SystemClock.elapsedRealtime()-start} samples=$samples");return}
            SystemClock.sleep(100)
        }
        main {
            Log.e("LivingJourney","TIMEOUT $label t=${life.clock} counts=${life.journal.counts}")
            village.jobs.forEach{(id,j)->Log.e("LivingJourney","job=$id pos=${village.residents[id]} $j")}
            life.people.forEach{(id,p)->Log.e("LivingJourney","person=$id $p")}
        }
        fail("Timed out: $label")
    }
    private fun calmAnimals() {
        val fauna=field<PrimitiveFaunaRuntime>(runtime,"fauna")
        val actors=field<List<Any>>(fauna,"actors")
        val spots=mapOf("MammothAdult_0" to Point(.36f,-.22f),"MammothAdult_1" to Point(.35f,.24f),
            "MammothCalf" to Point(.37f,.21f),"Sabertooth_0" to Point(-.37f,-.22f),"Sabertooth_1" to Point(-.37f,-.12f))
        spots.forEach { (id,near)->
            val b=effects.simulation.wildlife.bodies.getValue(id)
            val actor=actors.single{field<FaunaPlacement>(it,"placement").name==id}
            val mind=field<PrimitiveFaunaMind>(actor,"mind")
            val p=mind.navigation.points.filter{effects.simulation.wildlife.open(id,it)}.minBy{it.distance(near)}
            b.point=p;b.target=null;b.goal=p;b.decision=1000f;b.hunger=0f;b.rest=60f
            // A moved animal's ordinary home also belongs to the initial placement fixture.
            mind.javaClass.getDeclaredField("home").apply {isAccessible=true;set(mind,p)}
            village.animalLife.states[id]=VillageWildlifeLife.State(thirst=0f,fatigue=0f)
            world.requireNode(id).transform().position=Vector3(p.x,requireNotNull(effects.support(p.x,p.z)),p.z)
        }
    }
    @Test fun liveNeedsResourcesAndMemories() {
        check(ctx.packageName.endsWith(".living.acceptance"))
        File(ctx.filesDir,"living-village-v11.json").delete()
        kotlinx.coroutines.runBlocking { FireSliceRepository(ctx.filesDir).save(
            FireSliceState(knowsFire=true,fieldEstablished=true,shelter=ShelterPhase.COMPLETE,
                buildMillis=12_000,spentWood=8,sourceWood=0)) }
        launch()
        var mealStart=0;var drinkStart=0;var patchStart=0;var dryStart=0
        main {
            calmAnimals()
            village.ecology.food=8;life.foodBaseline=8+village.foodBundles.size-life.foodProduced+life.foodEaten+life.foodLost+life.foodInvested
            life.water=2f;life.reeds=3
            for(i in 0..8)life.person("PrimitiveResident_$i").apply { hunger=.12f;thirst=.12f;fatigue=.12f }
            life.person("PrimitiveResident_0").hunger=.86f
            life.person("PrimitiveResident_1").thirst=.9f
            life.person("PrimitiveResident_2").fatigue=.91f
            life.person("PrimitiveResident_4").hunger=.91f
            life.person("PrimitiveResident_7").thirst=.91f
            mealStart=life.foodEaten;drinkStart=life.people.values.sumOf{it.drinks}
            Log.i("LivingJourney","CAPTURE_NEEDS initial needs; no consequence injected")
        }
        await("food eaten only after real travel"){life.foodEaten>=mealStart+2}
        await("two residents drink and regain thirst"){life.people.values.sumOf{it.drinks}>=drinkStart+2}
        await("fatigued resident rests"){life.person("PrimitiveResident_2").fatigue<.45f}
        main {
            life.homes.getValue("Shelter_0").leak=.8f
            life.dampGrain=.75f
            life.reeds=3
            patchStart=life.journal.counts[VillageEvent.PATCH_ROOF]?:0
            dryStart=life.journal.counts[VillageEvent.DRY_WET_GRAIN]?:0
            Log.i("LivingJourney","CAPTURE_MAINTENANCE leak and damp stock fixtures")
        }
        await("roof repaired using real reeds"){(life.journal.counts[VillageEvent.PATCH_ROOF]?:0)>patchStart&&life.homes.getValue("Shelter_0").leak<.3f}
        await("damp grain dried at stock"){(life.journal.counts[VillageEvent.DRY_WET_GRAIN]?:0)>dryStart&&life.dampGrain<.35f}
        var gift=0;var carried=false;var giftStatus=""
        main {
            val at=requireNotNull(village.nearestOpen(Point(effects.simulation.depot.x+.045f,effects.simulation.depot.z+.025f)))
            assertTrue(effects.simulation.dropWood(at));gift=effects.simulation.woods.last().id
            Log.i("LivingJourney","CAPTURE_GIFT unclaimed physical wood gift id=$gift")
        }
        await("daily hauler carries and stacks actual gift") {
            val pile=effects.simulation.woods.firstOrNull{it.id==gift}
            if(pile?.phase==LocalInterventionWorld.WoodPhase.CARRIED)carried=true
            // A builder can take the just-stacked pile in the next resident
            // update, before this 100ms observer runs. Such jobs acquire wood
            // only from STACKED depot stock; retain identity and carrier checks.
            val depotHandoff=pile?.phase==LocalInterventionWorld.WoodPhase.CARRIED&&
                village.jobs.any{(id,j)->j.wood==gift&&j.carrying&&pile.carrier==id}
            val status="${pile?.phase}/${pile?.carrier}/depotHandoff=$depotHandoff"
            if(status!=giftStatus){Log.i("LivingJourney","GIFT id=$gift $status");giftStatus=status}
            carried&&(pile?.phase==LocalInterventionWorld.WoodPhase.STACKED||depotHandoff)
        }
        var started=0f
        main {started=life.clock;Log.i("LivingJourney","CAPTURE_DAILY live daily continuation")}
        await("100 scene seconds sustained daily life",180_000){life.clock-started>=100f}
        main {
            assertTrue("at least eight event kinds in native loop: ${life.journal.counts}",life.journal.counts.size>=8)
            assertTrue("finite food gathered",life.foodProduced>0)
            assertTrue("physical food returned to store",village.harvests>0)
            assertTrue("actual mutual-help memory",life.debts.values.any{it>0})
            life.validate(life.snapshot())
            Log.i("LivingJourney","PASS final clock=${life.clock} samples=$samples counts=${life.journal.counts} produced=${life.foodProduced} eaten=${life.foodEaten} lost=${life.foodLost} invested=${life.foodInvested} baseline=${life.foodBaseline} stock=${village.ecology.food} bundles=${village.foodBundles.size}")
            Log.i("LivingJourney","CAPTURE_SETTLED daily consequences complete")
        }
        await("atomic village save contains all measured events",10_000) {
            val save=File(ctx.filesDir,"living-village-v11.json")
            if(!save.exists())false else {
                val json=JSONObject(save.readText())
                val encoded=VillageLifeCodec.decode(json.getJSONObject("life"))
                encoded.counts.all{it.value>0}&&life.journal.counts.all{(e,n)->(encoded.counts[e]?:0)>=n}
            }
        }
        main {
            val json=JSONObject(File(ctx.filesDir,"living-village-v11.json").readText())
            val saved=VillageLifeCodec.decode(json.getJSONObject("life"))
            val expectation=JSONObject().put("pid",Process.myPid()).put("clock",saved.floats.getValue("clock"))
                .put("counts",JSONObject(saved.counts.mapKeys{it.key.name}))
                .put("produced",saved.ints.getValue("foodProduced")).put("eaten",saved.ints.getValue("foodEaten"))
                .put("debts",saved.debts.size)
            File(ctx.filesDir,"v15-restart-expectation.json").writeText(expectation.toString())
        }
    }
    @Test fun verifyAfterExternalProcessRestart() {
        check(ctx.packageName.endsWith(".living.acceptance"))
        val expected=JSONObject(File(ctx.filesDir,"v15-restart-expectation.json").readText())
        assertNotEquals("must use a new Android process",expected.getInt("pid"),Process.myPid())
        launch()
        main {
            invariant()
            assertTrue("life clock restored",life.clock>=expected.getDouble("clock"))
            val counts=expected.getJSONObject("counts")
            counts.keys().forEach { name->assertTrue("restored $name",(life.journal.counts[VillageEvent.valueOf(name)]?:0)>=counts.getInt(name)) }
            assertTrue(life.foodProduced>=expected.getInt("produced"))
            assertTrue(life.foodEaten>=expected.getInt("eaten"))
            assertTrue(life.debts.size>=expected.getInt("debts"))
            life.validate(life.snapshot())
            Log.i("LivingJourney","PASS cross-process restored oldPid=${expected.getInt("pid")} newPid=${Process.myPid()} kinds=${life.journal.counts.size} clock=${life.clock}")
            Log.i("LivingJourney","CAPTURE_RESTART resumed world")
        }
        var start=0f
        main {start=life.clock}
        await("restored simulation continues ten seconds",30_000){life.clock>=start+10f}
    }
}
