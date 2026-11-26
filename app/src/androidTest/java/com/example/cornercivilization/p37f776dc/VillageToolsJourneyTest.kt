package com.example.cornercivilization.p37f776dc

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cornercivilization.p37f776dc.domain.model.FireTool
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.scene.*
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.*
import com.pico.spatial.core.ecs.simulation.*
import com.pico.spatial.core.math.Vector3
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VillageToolsJourneyTest {
    private val ins=InstrumentationRegistry.getInstrumentation()
    private lateinit var runtime:PrimitivePreviewRuntime
    private lateinit var world:Entity
    private lateinit var effects:PrimitiveLocalEffects
    private lateinit var ground:Entity
    private lateinit var content:SpatialViewContent
    private lateinit var owner:Entity
    private val sim get()=effects.simulation
    private val village get()=effects.living
    private fun main(fn:()->Unit)=ins.runOnMainSync(fn)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(o:Any,n:String):T=o.javaClass.getDeclaredField(n).let{it.isAccessible=true;it.get(o) as T}
    private fun waitFor(label:String,ms:Long=30000,ok:()->Boolean){
        val start=SystemClock.elapsedRealtime()
        while(SystemClock.elapsedRealtime()-start<ms){var success=false;main{success=ok()};if(success){Log.i("VillageTools","PASS $label");return};SystemClock.sleep(60)}
        fail(label)
    }
    private fun pixels(p:Point)=content.convertPosition(world.convertPositionTo(Vector3(p.x,effects.drySupport(p.x,p.z)?:.034f,p.z),owner),
        content.localSpatialCoordinateSpace,ViewCoordinateSpace.Local)
    @Before fun launch(){
        val c=ins.targetContext
        check(c.packageName.endsWith(".qa19.living.acceptance"))
        c.startActivity(c.packageManager.getLaunchIntentForPackage(c.packageName)!!.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        waitFor("scene ready",90000){PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true}
        main{
            runtime=PrimitivePreviewRuntime.acceptanceInstance.get()!!
            world=field(runtime,"world");effects=field(runtime,"localEffects");content=field(runtime,"content");owner=field(runtime,"placementOwner")
            ground=world.requireNode("FloodplainTerrain")
        }
    }
    @Test fun beatAndGather(){
        var listeners=emptySet<String>()
        main{
            val couples=village.residents.entries.filter{(id,p)->sim.localTask(id)==null && !sim.dangerous(p) && !village.life.urgent(id) && village.jobs[id]?.carrying!=true}
            val center=couples.first { e->couples.count{it.value.distance(e.value)<.15f}>=2 }.value
            // Two residents have a free moment; placement, hearing, routing and arrival are live.
            couples.filter{it.value.distance(center)<.15f}.take(2).forEach{village.interrupt(it.key,it.value)}
            assertTrue(runtime.castAtTap(ground,pixels(center),FireTool.DRUM))
            val beat=village.influence.beat!!
            assertTrue(beat.point.distance(center)<.003f)
            listeners=village.jobs.filterValues{it.kind=="drumGather"}.keys.toSet()
            assertTrue("expected a gathering",listeners.isNotEmpty())
            assertFalse(runtime.castAtTap(ground,pixels(center),FireTool.DRUM))
        }
        waitFor("drum ripple"){world.requireNode("DrumRipple_0").enabled}
        waitFor("villager arrived at the drum",85000){listeners.any{village.jobs[it]?.let{j->j.kind=="drumGather" && j.elapsed>0f}==true} || sim.chronicle.snapshot().any{it.text.contains("围着鼓点坐")}}
    }
    @Test fun pickResidentAndFollow(){
        lateinit var id:String
        main{
            val map=field<Map<Long,String>>(runtime,"inputTargets")
            val hit=village.residents.keys.firstNotNullOfOrNull { person->
                val node=world.requireNode(person)
                val b=node.getVisualBounds(world,recursive=true,enabledOnly=true)
                val hits=world.scene!!.rayCast(Vector3(b.center.x,b.max.y+.025f,b.center.z),Vector3(0f,-1f,0f),.16f,
                    CollisionCastHitMode.ALL,CollisionGroup(CollisionGroup.COLLISION_GROUP_DEFAULT),world).results
                val first=hits.minByOrNull{it.distance}?.entity
                if(first?.logicalTarget(map)==person)person to first else null
            } ?: error("resident collision target missing")
            id=hit.first
            assertTrue(runtime.castAtTap(hit.second,pixels(village.residents.getValue(id)),FireTool.OBSERVE))
            assertTrue(runtime.scrollOpen);assertEquals(id,runtime.watchedResident)
            assertTrue(runtime.displayJournal!!.snapshot().isNotEmpty())
        }
        waitFor("resident footprint"){world.requireNode("ResidentTrack_5").enabled}
        SystemClock.sleep(3500)
        waitFor("diary records next activity",65000){runtime.displayJournal!!.snapshot().size>=2}
        main{assertTrue(runtime.castAtTap(world.requireNode(id),pixels(village.residents.getValue(id)),FireTool.OBSERVE));assertNull(runtime.watchedResident)}
    }
    @Test fun herdGrazesSownMeadow(){
        lateinit var site:Point
        main{
            val adult=sim.wildlife.bodies.getValue("MammothAdult_0")
            site=sim.navigation.points.filter{sim.farBank(it) && effects.canSow(it) && it.distance(adult.point)>.042f}
                .minBy{it.distance(adult.point)}
            assertTrue(runtime.seedPointer("grass",ground,pixels(site),true,true))
            assertTrue(runtime.seedPointer("grass",ground,pixels(site),false,false))
            assertTrue(village.influence.plots.single().meadow)
            assertTrue(runtime.rainPointer("rain",ground,pixels(site),true,true))
            assertTrue(runtime.rainPointer("rain",ground,pixels(site),false,false))
        }
        waitFor("far grass ripened",45000){village.influence.plots.any{it.meadow && it.growth>=1f}}
        waitFor("mammoth ate the planted meadow",150000){sim.chronicle.snapshot().any{it.text.contains("啃掉了你种下")}}
    }
    @Test fun dragSeedsAndWater(){
        lateinit var start:Point
        lateinit var end:Point
        main{
            val sites=sim.navigation.points.filter{effects.canSow(it)&& !sim.farBank(it) && !sim.dangerous(it) && it.distance(sim.depot)<.15f}
            start=sites.minBy{it.distance(sim.depot)}
            end=sites.first{it.distance(start) in .022f.. .038f}
            assertTrue(runtime.seedPointer("test",ground,pixels(start),true,true))
            assertTrue(runtime.seedPointer("test",ground,pixels(end),true,false))
            assertTrue(effects.seedPreview.size>=2);assertTrue(village.influence.plots.isEmpty())
            assertTrue(runtime.seedPointer("test",ground,pixels(end),false,false))
            assertTrue(effects.seedPreview.isEmpty())
            assertTrue(village.influence.plots.size>=2)
            assertTrue(runtime.rainPointer("water",ground,pixels(start),true,true))
            assertTrue(runtime.rainPointer("water",ground,pixels(start),false,false))
        }
        waitFor("seed trail sprouted"){world.requireNode("SeedShoot_0_0").enabled}
        waitFor("ripe fruit visible",45000){world.requireNode("SeedBerry_0").enabled}
        waitFor("villager picked planted fruit",120000){sim.chronicle.snapshot().any{it.text.contains("摘下了你种的野果")}}
    }
}
