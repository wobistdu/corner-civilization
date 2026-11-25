package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ParticleComponent
import com.pico.spatial.core.math.Vector3

/** Consumes the completed Editor kit; no recreation of its materials or emitters. */
internal class ShelterFireKit(world: Entity, kit: Entity, center: Vector3, emissionCenter: Vector3, ground: PrimitiveGroundSurface) {
    private val shelter = kit.requireNode("RainShelter")
    private val frame = shelter.requireNode("Frame")
    private val canopy = shelter.requireNode("Canopy")
    private val fire = kit.requireNode("CampfireParticles")
    private val normal = fire.requireNode("NormalFire")
    private val ember = fire.requireNode("LowEmber")
    private val smoke = fire.requireNode("ExtinguishedSmoke")
    private val sourceWood = world.requireNode("Resources_0")
    private val emitters = mutableMapOf<Entity, List<ParticleComponent>>()
    private var mode: Entity? = null
    private var lastStrength: Int? = null
    private var smokeAt = -10_000L
    private var lastShelter: ShelterPhase? = null
    val roofY: Float

    init {
        world.addChild(kit)
        kit.enabled = true
        // Placement deltas below are measured in world, which must own these transforms.
        world.addChild(shelter)
        world.addChild(fire)
        shelter.enableTree()
        // Measured source edge .017138m. Uniform 1.19 gives .02039m before terrain relief.
        shelter.transform().scaleVector = Vector3(1.19f)
        val bounds = shelter.getVisualBounds(world, recursive = true, enabledOnly = true)
        val dx = center.x - bounds.center.x
        val dz = center.z - bounds.center.z
        val terrain = listOf(-.0161f, 0f, .0161f).flatMap { x ->
            listOf(-.0133f, 0f, .0133f).map { z -> ground.heightAt(center.x+x, center.z+z) }
        }
        // Bury pole tips slightly at higher supports; never float an entire frame on its pivot.
        val baseY = terrain.min()
        shelter.transform().position += Vector3(dx, baseY-bounds.min.y, dz)
        val final = shelter.getVisualBounds(world, recursive = true, enabledOnly = true)
        val clearance = baseY + .017138f * 1.19f - terrain.max()
        check(clearance >= .01701f) { "Shelter action clearance failed: $clearance" }
        roofY = final.max.y
        // Use the source flame's measured base, not the buried bottom of the stone ring.
        fire.transform().position = emissionCenter
        fire.transform().scaleVector = Vector3(.7f)
        for (group in listOf(normal, ember, smoke)) {
            group.enableTree()
            fun collect(entity: Entity): List<ParticleComponent> {
                val component = entity.components.get<ParticleComponent>()
                return listOfNotNull(component) + entity.getChildren().flatMap(::collect)
            }
            emitters[group] = collect(group)
            emitters.getValue(group).forEach {
                Log.i("ShelterPrototype", "emitter=${group.getName()} main=${it.isMainSimulationEnabled} loop=${it.isLoop} rate=${it.birthRateFull} size=${it.mainSimulation.startSize} speed=${it.mainSimulation.startSpeed} lifetime=${it.mainSimulation.lifetime} emitting=${it.isEmitting} space=${it.mainSimulation.simulationSpace}/${it.mainSimulation.descriptionSpace} fade=${it.mainSimulation.isCameraFadeEnabled} visible=${it.mainSimulation.isVisible}")
                it.isEmitting = false
            }
            group.enabled = false
        }
        fire.enabled = true
        check(emitters.values.sumOf { it.size } == 6) { "Managed kit must contain six native emitters" }
        val capacity = emitters.values.flatten().sumOf { it.mainSimulation.maxParticleCount }
        check(capacity <= 132) { "Particle budget changed: $capacity" }
        Log.i("ShelterPrototype", "kit ready emitters=6 particleCap=$capacity shelterBounds=$final minimumActionClearance=$clearance sourceScale=1.19 fireOrigin=${fire.transform().position} ground=${ground.heightAt(center.x, center.z)}")
    }

    fun apply(state: FireSliceState) {
        frame.enabled = state.shelter == ShelterPhase.BUILDING || state.shelter == ShelterPhase.COMPLETE
        canopy.enabled = state.shelter == ShelterPhase.COMPLETE
        sourceWood.enabled = state.sourceWood > 0
        if (lastStrength != null && lastStrength!! > 0 && state.strength == 0) smokeAt = state.activeMillis
        lastStrength = state.strength
        val elapsed = state.activeMillis - smokeAt
        val desired = when {
            state.strength > 55 -> normal
            state.strength > 0 -> ember
            elapsed in 0..4_000 -> smoke
            else -> null
        }
        if (desired != mode) {
            emitters.forEach { (entity, parts) ->
                entity.enabled = entity == desired
                parts.forEach { it.isEmitting = entity == desired }
            }
            mode = desired
            Log.i("ShelterPrototype", "particle=${desired?.getName() ?: "cold"} strength=${state.strength}")
        }
        if (desired == smoke && elapsed >= 1_000) emitters.getValue(smoke).forEach { it.isEmitting = false }
        if (lastShelter != state.shelter) {
            Log.i("ShelterPrototype", "shelter=${state.shelter} sourceWood=${state.sourceWood} carried=${state.carriedWood} spent=${state.spentWood} work=${state.buildMillis} protectedDoses=${state.protectedDoses}")
            lastShelter = state.shelter
        }
    }
}
