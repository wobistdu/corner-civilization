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
import java.io.File

/** Device ecology acceptance. Only initial needs and locations are fixtures.
 * Rain uses the player tool; native frames alone move, grow, drink and settle events. */
@RunWith(AndroidJUnit4::class)
class EcologyJourneyTest {
    private val ins = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = ins.targetContext
    private fun main(block: () -> Unit) = ins.runOnMainSync(block)
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(owner: Any, name: String): T =
        owner.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(owner) as T }
    private lateinit var runtime: PrimitivePreviewRuntime
    private lateinit var world: Entity
    private lateinit var effects: PrimitiveLocalEffects
    private lateinit var actors: List<Any>
    private val village get() = effects.living
    private val life get() = village.life
    private val wildlife get() = effects.simulation.wildlife
    private var samples = 0
    private fun mind(id: String): PrimitiveFaunaMind =
        field(actors.single { field<FaunaPlacement>(it, "placement").name == id }, "mind")
    private fun count(event: VillageEvent) = life.journal.counts[event] ?: 0
    private fun state(id: String) = village.animalLife.states.getValue(id)
    private fun place(id: String, near: Point): Point {
        val b = wildlife.bodies.getValue(id)
        val m = mind(id)
        val at = m.navigation.points.filter { wildlife.open(id, it) }.minBy { it.distance(near) }
        b.point = at; b.target = null; b.goal = at; b.decision = 60f
        b.hunger = 0f; b.rest = 60f; b.attackTime = 0f
        m.javaClass.getDeclaredField("home").apply { isAccessible = true; set(m, at) }
        village.animalLife.states[id] = VillageWildlifeLife.State(thirst = 0f, fatigue = 0f)
        world.requireNode(id).transform().position =
            Vector3(at.x, requireNotNull(effects.support(at.x, at.z)), at.z)
        return at
    }
    private fun invariant() {
        val people = wildlife.bodies.values.filter { it.species == WildlifeWorld.Species.RESIDENT }
        val animals = wildlife.bodies.values.filter { it.species != WildlifeWorld.Species.RESIDENT && !it.returnedToSoil }
        assertEquals(9, people.size)
        people.forEach { r ->
            assertTrue(r.alive)
            val p = world.requireNode(r.id).transform().position
            assertEquals(r.point.x, p.x, .00001f); assertEquals(r.point.z, p.z, .00001f)
            animals.forEach { a ->
                assertTrue("body crossing ${r.id}/${a.id}",
                    r.point.distance(a.point) >= r.radius + a.radius - .0002f)
            }
        }
        animals.filter { it.alive }.forEach { a ->
            val p = world.requireNode(a.id).transform().position
            assertEquals(a.point.x, p.x, .00001f); assertEquals(a.point.z, p.z, .00001f)
        }
        assertEquals(life.foodBaseline + life.foodProduced, village.ecology.food +
            village.foodBundles.size + life.foodEaten + life.foodLost + life.foodInvested)
        assertTrue(effects.simulation.wetSpots.all { it.wet in 0f..1f && it.growth in 0f..1f })
        samples++
    }
    private fun await(label: String, seconds: Long = 90, predicate: () -> Boolean) {
        val start = SystemClock.elapsedRealtime()
        val clockStart = life.clock
        while (SystemClock.elapsedRealtime() - start < seconds * 1000) {
            var done = false
            main { invariant(); done = predicate() }
            if (done) {
                Log.i("EcologyJourney", "PASS $label wallMs=${SystemClock.elapsedRealtime()-start} sceneSeconds=${life.clock-clockStart} samples=$samples")
                return
            }
            SystemClock.sleep(100)
        }
        main {
            Log.e("EcologyJourney", "TIMEOUT $label clock=${life.clock} counts=${life.journal.counts}")
            wildlife.bodies.values.forEach { Log.e("EcologyJourney", "body=$it order=${wildlife.order(it.id)}") }
            village.animalLife.states.forEach { (id,s) -> Log.e("EcologyJourney", "$id state=$s") }
        }
        fail("Timed out: $label")
    }
    @Test fun rainRegrowthAndAnimalNeeds() {
        check(ctx.packageName.endsWith(".living.acceptance"))
        File(ctx.filesDir, "living-village-v11.json").delete()
        kotlinx.coroutines.runBlocking {
            FireSliceRepository(ctx.filesDir).save(FireSliceState(knowsFire=true,
                fieldEstablished=true, shelter=ShelterPhase.COMPLETE,
                buildMillis=12_000, spentWood=8, sourceWood=0))
        }
        ctx.startActivity(ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val start = SystemClock.elapsedRealtime()
        while (SystemClock.elapsedRealtime()-start < 75_000) {
            var ready = false
            main { ready=PrimitivePreviewRuntime.acceptanceInstance.get()?.ready==true }
            if (ready) break
            SystemClock.sleep(100)
        }
        var rain = Point(0f,0f)
        var meadowBefore = 0
        main {
            runtime=requireNotNull(PrimitivePreviewRuntime.acceptanceInstance.get())
            assertTrue(runtime.ready)
            world=field(runtime,"world"); effects=field(runtime,"localEffects")
            actors=field(field<PrimitiveFaunaRuntime>(runtime,"fauna"),"actors")
            place("MammothAdult_0",Point(.36f,-.20f))
            place("MammothAdult_1",Point(.36f,.24f))
            place("MammothCalf",Point(.38f,.20f))
            place("Sabertooth_0",Point(-.37f,-.22f))
            place("Sabertooth_1",Point(-.37f,-.10f))
            village.ecology.food=8
            life.foodBaseline=8+village.foodBundles.size-life.foodProduced+
                life.foodEaten+life.foodLost+life.foodInvested
            life.water=2f
            for (i in 0..8) life.person("PrimitiveResident_$i").apply {
                hunger=.12f; thirst=.12f; fatigue=.12f
            }
            val m=mind("MammothAdult_0")
            rain=m.navigation.points.filter { wildlife.open("MammothAdult_0",it) }
                .minBy { it.distance(Point(.28f,-.20f)) }
            assertTrue("rain starts on dry, ungrown ground",
                effects.simulation.wetSpots.none { it.point.distance(rain)<.035f })
            meadowBefore=count(VillageEvent.MEADOW_RECOVER)
            assertTrue(effects.cast(FireTool.WATER,
                Vector3(rain.x,requireNotNull(effects.support(rain.x,rain.z)),rain.z)))
            Log.i("EcologyJourney","FIXTURE rain=$rain using actual water tool; no growth or event injected")
        }
        await("rain produces wet ground and grass") {
            effects.simulation.hazards.none { it.kind==LocalInterventionWorld.Kind.RAIN } &&
                effects.simulation.wetSpots.any { it.point.distance(rain)<.01f && it.wet>.5f && it.growth>.4f }
        }
        var mudStart=Point(0f,0f)
        var mudBefore=0
        main {
            val id="MammothAdult_0"; val nav=mind(id).navigation
            val at=nav.points.filter { wildlife.open(id,it) && it.distance(rain) in .065f.. .09f }
                .sortedBy { it.distance(wildlife.bodies.getValue(id).point) }
                .take(32).first { nav.path(it,rain) { p -> wildlife.open(id,p) } != null }
            mudStart=place(id,at)
            state(id).fatigue=.5f
            mudBefore=count(VillageEvent.ELEPHANT_MUD)
            Log.i("EcologyJourney","FIXTURE tired adult at=$mudStart; real rain-created mud=$rain")
        }
        await("recovered meadow changes grazing destination") {
            count(VillageEvent.MEADOW_RECOVER)>meadowBefore &&
                effects.simulation.wetSpots.any { it.point.distance(rain)<.01f && it.growth>.55f } &&
                wildlife.bodies.values.any { it.species==WildlifeWorld.Species.ADULT && it.goal?.distance(rain)?.let { d->d<.01f }==true }
        }
        await("adult walks to wet ground and completes mud bath") {
            count(VillageEvent.ELEPHANT_MUD)>mudBefore &&
                state("MammothAdult_0").coat>.8f &&
                state("MammothAdult_0").fatigue<.3f &&
                wildlife.bodies.getValue("MammothAdult_0").point.distance(mudStart)>.02f
        }
        var drinkBefore=0
        var tigerStart=Point(0f,0f)
        main {
            val id="Sabertooth_0"; val nav=mind(id).navigation; val bank=village.ecology.bank
            val shore=nav.points.filter { wildlife.open(id,it) }.minBy { it.distance(bank) }
            assertTrue("existing drinking bank is accessible",
                shore.distance(bank)<.016f+wildlife.bodies.getValue(id).radius)
            // Nearby candidates may all lie on the wrong connected bank, or
            // residents may surround the closest shore after the mud-bath leg.
            // Find an initial position connected to an actual drinkable shore;
            // do not cap the search to the first 32 Euclidean neighbours.
            val shores=nav.points.filter { wildlife.open(id,it) &&
                it.distance(bank)<.016f+wildlife.bodies.getValue(id).radius }
                .sortedBy { it.distance(bank) }
            val at=shores.firstNotNullOfOrNull { destination->
                nav.pathToNearest(destination,
                    { it.distance(bank) in .075f.. .11f },
                    { wildlife.open(id,it) })?.lastOrNull()
            }
            assertNotNull("Initial tiger position must have a real route to a drinking shore",at)
            Log.i("EcologyJourney","FIXTURE connected drinking shores=${shores.size} start=$at")
            tigerStart=place(id,requireNotNull(at));state(id).thirst=.9f
            drinkBefore=state(id).drinks
            Log.i("EcologyJourney","FIXTURE thirsty tiger at=$tigerStart bank=$bank; no drink or route injected")
        }
        await("thirsty tiger travels to existing river bank before drinking") {
            state("Sabertooth_0").drinks>drinkBefore && state("Sabertooth_0").thirst<.1f &&
                count(VillageEvent.TIGER_DRINK)>0 &&
                wildlife.bodies.getValue("Sabertooth_0").point.distance(tigerStart)>.025f
        }
        main {
            life.validate(life.snapshot())
            Log.i("EcologyJourney","PASS final ecology clock=${life.clock} samples=$samples counts=${life.journal.counts} rain=$rain mudCoat=${state("MammothAdult_0").coat} tigerDrinks=${state("Sabertooth_0").drinks}")
            field<LivingVillageStore>(runtime,"villageStore").tick(0f,true)
        }
        await("ecology consequences reach atomic village save",10) {
            val file=File(ctx.filesDir,"living-village-v11.json")
            if (!file.exists()) false else {
                val json=org.json.JSONObject(file.readText())
                val saved=VillageLifeCodec.decode(json.getJSONObject("life"))
                (saved.counts[VillageEvent.MEADOW_RECOVER]?:0)>meadowBefore &&
                    (saved.counts[VillageEvent.ELEPHANT_MUD]?:0)>mudBefore &&
                    (saved.counts[VillageEvent.TIGER_DRINK]?:0)>0
            }
        }
    }
}
