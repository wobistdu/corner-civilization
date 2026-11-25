package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.FireSliceState
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.math.Vector3
import kotlin.math.abs

/** Existing meshes are retained by the scene; no allocation during apply/recomposition. */
internal class PrimitiveWorldMemoryRuntime(
    private val world: Entity,
    hourglass: Entity,
    private val field: Entity,
    private val ground: PrimitiveGroundSurface,
) {
    private val source = requireNotNull(world.requireNode("Resources_0").requireNode("NativeModel").components.get<ModelComponent>())
    private val pending = List(3) { makePile("PendingWood_$it", -.088f + (it-1)*.005f, .227f) }
    private val stored = List(6) { makePile("StoredWood_$it", -.13f + (it%3-1)*.004f, .199f + (it/3)*.003f) }
    private var last: PrimitiveMemoryPresentation? = null
    val maxEnvelope: BoundingBox

    init {
        hourglass.enabled = false
        field.setName("FirstField")
        world.addChild(field)
        field.enableTree()
        field.transform().scaleVector = Vector3(.7f)
        place(field, -.055f, .285f, .0015f)
        // Original soil surface is authored at y=5.4mm; bury the thick slab, leave seedlings above ground.
        val fieldBounds=field.getVisualBounds(world,recursive=true,enabledOnly=true)
        val soilTop=fieldBounds.min.y+.0054f*.7f
        val targetSoilTop=ground.heightAt(-.055f,.285f)+.00015f
        field.transform().position+=Vector3(0f,targetSoilTop-soilTop,0f)
        Log.i("WorldMemory","fieldSoil embedded=true soilTop=$targetSoilTop burial=${soilTop-targetSoilTop}")
        // All potential states are visible only during the single initial envelope measurement.
        val all = pending + stored + field
        maxEnvelope = all.map { it.getVisualBounds(world, recursive=true, enabledOnly=true) }.reduce(::unionBounds)
        all.forEach { it.enabled=false }
        Log.i("WorldMemory", "ready pendingInstances=3 storedInstances=6 fieldInstances=1 geometryShared=true maxEnvelope=$maxEnvelope")
    }

    private fun makePile(name: String, x: Float, z: Float): Entity =
        ModelEntity(source.mesh,source.materials.toList().toTypedArray()).apply {
            setName(name)
            world.addChild(this)
            // Same authored resource size as the existing source pile (current props are x.35).
            transform().scaleVector=Vector3(.35f)
            place(this,x,z,.0015f)
        }

    private fun place(entity: Entity, x: Float, z: Float, reliefLimit: Float) {
        check(entity.getParent() == world)
        val b=entity.getVisualBounds(world,recursive=true,enabledOnly=true)
        check(!b.isEmpty())
        val heights=listOf(-b.halfExtent.x,0f,b.halfExtent.x).flatMap { dx ->
            listOf(-b.halfExtent.z,0f,b.halfExtent.z).map { dz -> ground.heightAt(x+dx,z+dz) }
        }
        check(heights.all { it.isFinite() })
        val high=heights.max();val low=heights.min()
        check(high-low <= reliefLimit) { "Unsupported ground relief for ${entity.getName()}: ${high-low}" }
        // Sink the soil/log bottom at the low sample, so it never floats above the support.
        entity.transform().position += Vector3(x-b.center.x,low-b.min.y,z-b.center.z)
        val final=entity.getVisualBounds(world,recursive=true,enabledOnly=true)
        check(abs(final.min.y-low)<.00003f)
        Log.i("WorldMemory","grounded=${entity.getName()} owner=PrimitiveWorld supportMin=$low supportMax=$high finalVisualBounds=$final epsilon=.00003 numericStatus=PASS visualStatus=UNOBSERVED")
    }

    fun apply(state: FireSliceState, active: Boolean) {
        val p=PrimitiveMemoryPresentation.from(state)
        pending.forEachIndexed { i,e -> e.enabled=i<p.pendingPiles }
        stored.forEachIndexed { i,e -> e.enabled=i<p.storedPiles }
        field.enabled=p.fieldVisible
        if (last != p) {
            Log.i("WorldMemory","pendingWood=${state.pendingWood} storedWood=${state.storedWood} haulingWood=${state.haulingWood} deliveries=${state.deliveries} field=${p.fieldVisible}")
            last=p
        }
    }
}
