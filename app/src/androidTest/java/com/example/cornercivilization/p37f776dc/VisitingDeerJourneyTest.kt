package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.example.cornercivilization.p37f776dc.domain.usecase.*
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.*

/** Preserve the existing village. Set visitor needs only; rain and native frames do the work. */
@RunWith(AndroidJUnit4::class)
class VisitingDeerJourneyTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private fun main(block:()->Unit)=ins.runOnMainSync(block)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner:Any,name:String):T=
        owner.javaClass.getDeclaredField(name).let{it.isAccessible=true;it.get(owner) as T}
    private lateinit var world:Entity
    private lateinit var effects:PrimitiveLocalEffects
    private lateinit var deer:VisitingDeerLife
    private var samples=0
    private fun invariant() {
        val sim=effects.simulation
        deer.states.values.forEach { s->
            assertEquals(WildlifeWorld.Species.DEER,s.body.species)
            assertTrue(s.body.alive)
            val entity=world.requireNode(s.body.id)
            val p=entity.transform().position
            assertEquals(s.body.point.x,p.x,.00001f);assertEquals(s.body.point.z,p.z,.00001f)
            assertEquals("visitor uses the same support as its real route",
                requireNotNull(effects.support(s.body.point.x,s.body.point.z)),p.y,.00001f)
            val bounds=entity.getVisualBounds(entity,recursive=true,enabledOnly=true)
            assertFalse(bounds.isEmpty())
            val extent=hypot(maxOf(abs(bounds.min.x),abs(bounds.max.x)),maxOf(abs(bounds.min.z),abs(bounds.max.z)))
            assertTrue("collision disc excludes an animated part",extent<=s.body.radius+.00001f)
            sim.wildlife.bodies.values.filter{it.id!=s.body.id&&!it.returnedToSoil}.forEach { other->
                assertTrue("visitor crosses ${other.id}",
                    s.body.point.distance(other.point)>=s.body.radius+other.radius-.0002f)
            }
            assertTrue(sim.navigation.open(s.body.point))
        }
        assertEquals(5,sim.wildlife.save().size)
        assertTrue(sim.wildlife.save().all{it.id.startsWith("Mammoth")||it.id.startsWith("Sabertooth")})
        assertTrue(effects.living.animalLife.states.keys.none{it.startsWith("VisitingDeer")})
        val life=effects.living.life
        assertEquals(life.foodBaseline+life.foodProduced,effects.living.ecology.food+
            effects.living.foodBundles.size+life.foodEaten+life.foodLost+life.foodInvested)
        samples++
    }
    private fun await(label:String,seconds:Int=120,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+seconds*1000
        while(SystemClock.elapsedRealtime()<end) {
            var done=false
            main { invariant();done=condition() }
            if(done){Log.i("VisitingDeerJourney","PASS $label samples=$samples");return}
            SystemClock.sleep(100)
        }
        main {deer.states.values.forEach{Log.e("VisitingDeerJourney",
            "TIMEOUT $label id=${it.body.id} at=${it.body.point} goal=${it.goal} intent=${it.intent} work=${it.worked} drinks=${it.drinks} grazes=${it.grazes}")}}
        fail("Timed out: $label")
    }
    @Test fun actualDrinkRainEscapeAndFiniteGrazingPreserveVillage() {
        val ctx=ins.targetContext
        check(ctx.packageName.endsWith(".living.acceptance"))
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline=SystemClock.elapsedRealtime()+75_000
        var ready=false
        while(!ready&&SystemClock.elapsedRealtime()<deadline) {
            main {
                val runtime=PrimitivePreviewRuntime.acceptanceInstance.get()
                if(runtime?.ready==true) {
                    val view=field<Any>(runtime,"villageView")
                    deer=field<Lazy<VisitingDeerLife>>(view,"deerLife\$delegate").value
                    if(deer.states.size==2) {
                        world=field(runtime,"world");effects=field(runtime,"localEffects");ready=true
                    }
                }
            }
            if(!ready)SystemClock.sleep(100)
        }
        assertTrue("fully restored scene and both visitors",ready)
        main {
            val ground=field<PrimitiveGroundSurface>(effects,"ground")
            val probes=effects.simulation.navigation.points.flatMap{p->
                listOf(p,Point(p.x-.012f,p.z),Point(p.x+.012f,p.z))
            }
            val start=SystemClock.elapsedRealtimeNanos()
            val reference=probes.map{p->try{ground.heightAt(p.x,p.z)}
                catch(_:IllegalArgumentException){null}catch(_:IllegalStateException){null}}
            val referenceNanos=SystemClock.elapsedRealtimeNanos()-start
            val fastStart=SystemClock.elapsedRealtimeNanos()
            val actual=probes.map{p->ground.heightOrNull(p.x,p.z)}
            val queryNanos=SystemClock.elapsedRealtimeNanos()-fastStart
            assertEquals("same real terrain support and exact interpolation",reference,actual)
            Log.i("TerrainQueryBenchmark","probes=${probes.size} misses=${reference.count{it==null}} strictMillis=${referenceNanos/1e6} nullableMillis=${queryNanos/1e6} exactMatch=true")
        }
        lateinit var subject:VisitingDeerLife.State
        var drinks=0
        main {
            subject=deer.states.values.first()
            drinks=subject.drinks
            subject.thirst=.95f
            Log.i("VisitingDeerJourney","FIXTURE thirsty visitor at=${subject.body.point}; no route/contact/drink injected")
        }
        // Thirst may saturate while wildlife blocks the bank: 1 - .7 = .30.
        // Permit only the .003/sec growth between two 100ms observations.
        await("visitor travels and completes real river drinking") {subject.drinks>drinks&&subject.thirst<=.301f}
        var rain=Point(0f,0f)
        var escapes=0
        await("reachable dry rain site in the current village",60) {
            val sim=effects.simulation
            val allow=sim.residentRouteAllowed(subject.body.id)
            val candidates=(sim.wetSpots.filter{it.growth<.1f}.map{it.point}+
                sim.navigation.points).asSequence().filter {
                it.distance(subject.body.point) in .025f.. .05f&&allow(it)&&
                    effects.drySupport(it.x,it.z)!=null&&
                    sim.wetSpots.none{patch->patch.point.distance(it)<.035f&&patch.point!=it}
            }
            val chosen=candidates.firstOrNull {sim.navigation.path(subject.body.point,it,allow)!=null}
            if(chosen!=null)rain=chosen
            chosen!=null
        }
        var initialGrowth=0f
        main {
            initialGrowth=effects.simulation.wetSpots.firstOrNull{it.point==rain}?.growth?:0f
            escapes=subject.escapes
            assertTrue(effects.cast(FireTool.WATER,
                Vector3(rain.x,requireNotNull(effects.support(rain.x,rain.z)),rain.z)))
            Log.i("VisitingDeerJourney","FIXTURE real water tool at=$rain initialGrowth=$initialGrowth; no growth/location/escape injected")
        }
        await("rain causes actual visitor escape while it is still raining",12) {
            subject.escapes>escapes&&!effects.simulation.dangerous(subject.body.point)&&
                subject.body.point.distance(rain)>.068f&&
                effects.simulation.hazards.any{it.kind==LocalInterventionWorld.Kind.RAIN&&it.dangerous}
        }
        await("real rain leaves finite grown meadow",45) {
            effects.simulation.hazards.none{it.kind==LocalInterventionWorld.Kind.RAIN}&&
                effects.simulation.wetSpots.any{it.point.distance(rain)<.01f&&it.growth>maxOf(.4f,initialGrowth+.3f)}
        }
        var grazes=0
        main {
            grazes=subject.grazes;subject.hunger=.95f;subject.thirst=.1f
            Log.i("VisitingDeerJourney","FIXTURE hungry visitor at=${subject.body.point}; actual grown patches=${effects.simulation.wetSpots}")
        }
        await("visitor reaches a finite grass patch and consumes it") {subject.grazes>grazes&&subject.hunger<.65f}
        main {
            effects.living.life.validate(effects.living.life.snapshot())
            Log.i("VisitingDeerJourney",
                "PASS final samples=$samples drinks=${subject.drinks-drinks} grazes=${subject.grazes-grazes} escapes=${subject.escapes-escapes} originalVillagePreserved=true")
        }
    }
}
