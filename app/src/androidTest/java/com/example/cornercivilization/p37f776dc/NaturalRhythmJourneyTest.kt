package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.ecs.Entity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Passive native observation: no weather, needs, resources, routes or consequences injected. */
@RunWith(AndroidJUnit4::class)
class NaturalRhythmJourneyTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private val ctx get()=ins.targetContext
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner:Any,name:String):T =
        owner.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(owner) as T}

    @Test fun naturalConsequencesStayVisibleAcrossTwentySecondWindows() {
        check(ctx.packageName.endsWith(".living.acceptance"))
        val fresh=InstrumentationRegistry.getArguments().getString("fresh")=="true"
        if(fresh) {
            File(ctx.filesDir,"living-village-v11.json").delete()
            kotlinx.coroutines.runBlocking { FireSliceRepository(ctx.filesDir).save(FireSliceState()) }
        }
        val boot=SystemClock.elapsedRealtime()
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var runtime:PrimitivePreviewRuntime?=null
        while(SystemClock.elapsedRealtime()-boot<75_000) {
            ins.runOnMainSync {runtime=PrimitivePreviewRuntime.acceptanceInstance.get()?.takeIf{it.ready}}
            if(runtime!=null)break
            SystemClock.sleep(100)
        }
        assertNotNull("Native world ready",runtime)
        lateinit var local:PrimitiveLocalEffects
        lateinit var world:Entity
        var sceneStart=0f
        ins.runOnMainSync {
            local=field(runtime!!,"localEffects");world=field(runtime!!,"world")
            sceneStart=local.living.life.clock
        }
        val seen=mutableSetOf<String>()
        val moments=JSONArray()
        val samples=JSONArray()
        val start=SystemClock.elapsedRealtime()
        val bootSeconds=(start-boot)/1000.0
        val firstPositions=mutableMapOf<String,LocalInterventionWorld.Point>()
        val purposefulResidents=mutableSetOf<String>()
        Log.i("NaturalRhythm","START fresh=$fresh sceneStart=$sceneStart bootSeconds=$bootSeconds observeSeconds=180")
        var last=0.0
        var maxGap=0.0
        var previousFire=""
        var previousHarvests:Int?=null
        val houseHealth=mutableMapOf<String,Float>()
        val animalHealth=mutableMapOf<String,Float>()
        val duration=180_000L
        while(SystemClock.elapsedRealtime()-start<duration) {
            val wall=(SystemClock.elapsedRealtime()-start)/1000.0
            ins.runOnMainSync {
                val village=local.living;val life=village.life
                fun physical(event:String,subject:String,result:String) {
                    maxGap=maxOf(maxGap,wall-last);last=wall
                    val item=JSONObject().put("wall",wall).put("scene",life.clock)
                        .put("event",event).put("subject",subject).put("result",result)
                    moments.put(item);Log.i("NaturalRhythm","OUTCOME $item")
                }
                assertEquals(life.foodBaseline+life.foodProduced,
                    village.ecology.food+village.foodBundles.size+life.foodEaten+life.foodLost+life.foodInvested)
                assertTrue(local.simulation.wildlife.bodies.values
                    .filter{it.species==WildlifeWorld.Species.RESIDENT}.all{it.alive})
                val claimed=local.simulation.woods.mapNotNull{it.carrier}+village.foodBundles.mapNotNull{it.carrier}
                assertTrue("A resident cannot reserve two physical cargo items",
                    claimed.groupingBy{it}.eachCount().values.all{it==1})
                assertTrue("Finite food stock",village.ecology.food in 0..village.ecology.foodCapacity)
                val snapshot=JSONObject().put("wall",wall).put("clock",life.clock)
                    .put("stock",village.ecology.food).put("water",life.water)
                    .put("foodBundles",village.foodBundles.size).put("wood",local.simulation.woods.size)
                    .put("harvests",village.harvests).put("depotDanger",local.simulation.dangerous(local.simulation.depot))
                snapshot.put("cargo",JSONArray(village.foodBundles.map {
                    JSONObject().put("x",it.point.x).put("z",it.point.z).put("carrier",it.carrier)
                        .put("age",it.age).put("origin",it.origin)
                }))
                // These visible settlements predate VillageJournal. Observe
                // their actual counters/health, rather than missing deliveries,
                // rebuilding and predation merely because they lack journal IDs.
                previousHarvests?.let { old ->
                    if(village.harvests>old)physical("FOOD_DELIVERED","depot",
                        "${village.harvests-old}束粮实际入库；库存=${village.ecology.food}")
                }
                previousHarvests=village.harvests
                val houses=JSONArray()
                village.houses.values.forEach { h ->
                    houseHealth.put(h.id,h.health)?.let { old ->
                        if(h.health<old)physical("HOUSE_DAMAGED",h.id,"${h.damageCause}；${old}→${h.health}")
                        else if(old<99.99f&&h.health>=99.99f)physical("HOUSE_REBUILT",h.id,"实际取材施工后恢复")
                    }
                    houses.put(JSONObject().put("id",h.id).put("health",h.health).put("cause",h.damageCause))
                }
                val animals=JSONArray()
                local.simulation.wildlife.bodies.values.filter{it.species!=WildlifeWorld.Species.RESIDENT}.forEach { a ->
                    animalHealth.put(a.id,a.health)?.let { old ->
                        if(a.health<old)physical(if(a.alive)"ANIMAL_INJURED"else"PREDATION",a.id,
                            "连续实际接触；${old}→${a.health}；alive=${a.alive}")
                    }
                    animals.put(JSONObject().put("id",a.id).put("x",a.point.x).put("z",a.point.z)
                        .put("health",a.health).put("alive",a.alive).put("hunger",a.hunger))
                }
                snapshot.put("houses",houses).put("animals",animals)
                val actors=JSONArray()
                village.residents.forEach{(id,at)->
                    val position=world.requireNode(id).transform().position
                    assertEquals("$id X",at.x,position.x,.0002f);assertEquals("$id Z",at.z,position.z,.0002f)
                    val job=village.jobs[id]
                    val needs=life.person(id)
                    val origin=firstPositions.getOrPut(id){at}
                    if(bootSeconds+wall<=30&&job!=null&&(origin.distance(at)>.005f||job.elapsed>.5f))
                        purposefulResidents+=id
                    job?.food?.let{assertEquals("Cargo belongs to the active job",id,it.carrier)}
                    var previous=at
                    var remaining=0f
                    job?.path?.forEach { next -> remaining+=previous.distance(next);previous=next }
                    actors.put(JSONObject().put("id",id).put("x",at.x).put("z",at.z)
                        .put("job",job?.kind).put("work",job?.elapsed?:0f)
                        .put("event",job?.event).put("peer",job?.peer).put("carrying",job?.carrying?:false)
                        .put("hunger",needs.hunger).put("thirst",needs.thirst).put("fatigue",needs.fatigue)
                        .put("fear",needs.fear).put("lonely",needs.lonely).put("lastSocial",needs.lastSocial)
                        .put("danger",local.simulation.dangerous(at))
                        .put("goalX",job?.goal?.x).put("goalZ",job?.goal?.z)
                        .put("remainingPath",remaining).put("blocked",job?.blocked?:0f))
                }
                snapshot.put("actors",actors);samples.put(snapshot)
                for(e in life.journal.recent) {
                    if(e.at<sceneStart||e.event in setOf(VillageEvent.WAIT_ROUTE,VillageEvent.STOP_EXHAUSTED,VillageEvent.RESUME_RECOVERED))continue
                    val key="${e.event}:${e.subject}:${e.at}"
                    if(!seen.add(key))continue
                    maxGap=maxOf(maxGap,wall-last);last=wall
                    val item=JSONObject().put("wall",wall).put("scene",e.at).put("event",e.event.name)
                        .put("subject",e.subject).put("result",e.result)
                    moments.put(item)
                    Log.i("NaturalRhythm","OUTCOME $item")
                }
                val fire=field<FireSliceState>(runtime!!,"fireState")
                val stage="${fire.phase}/${fire.shelter}/${fire.fieldEstablished}"
                if(previousFire.isNotEmpty()&&previousFire!=stage) {
                    maxGap=maxOf(maxGap,wall-last);last=wall
                    moments.put(JSONObject().put("wall",wall).put("event","HEARTH_STAGE").put("result",stage))
                }
                previousFire=stage
            }
            SystemClock.sleep(200)
        }
        val elapsed=(SystemClock.elapsedRealtime()-start)/1000.0
        maxGap=maxOf(maxGap,elapsed-last)
        val windows=JSONArray()
        for(index in 0..8) {
            val count=(0 until moments.length()).count {
                moments.getJSONObject(it).getDouble("wall") in (index*20.0)..<(index*20.0+20.0)
            }
            windows.put(JSONObject().put("from",index*20).put("to",index*20+20).put("outcomes",count))
        }
        val result=JSONObject().put("fresh",fresh).put("wallSeconds",elapsed).put("maxOutcomeGapSeconds",maxGap)
            .put("bootSeconds",bootSeconds)
            .put("firstOutcomeFromLaunchSeconds",if(moments.length()>0)bootSeconds+moments.getJSONObject(0).getDouble("wall")else JSONObject.NULL)
            .put("purposefulResidentsWithinThirtySeconds",JSONArray(purposefulResidents.toList()))
            .put("events",moments).put("windows",windows).put("samples",samples)
        File(ctx.filesDir,"v16-natural-rhythm.json").writeText(result.toString())
        Log.i("NaturalRhythm","RESULT fresh=$fresh elapsed=$elapsed maxGap=$maxGap windows=$windows outcomes=${moments.length()}")
        assertTrue("No visible consequence gap above 20 seconds: $maxGap",maxGap<=20.5)
        assertTrue("Every complete twenty second window contains an outcome",
            (0 until windows.length()).all{windows.getJSONObject(it).getInt("outcomes")>0})
        if(fresh) {
            assertTrue("New player sees a real consequence within thirty seconds of launch, including loading",
                moments.length()>0&&bootSeconds+moments.getJSONObject(0).getDouble("wall")<=30)
            assertTrue("At least two residents visibly pursue work within thirty seconds of launch",
                purposefulResidents.size>=2)
        }
    }
}
