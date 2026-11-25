package com.example.cornercivilization.p37f776dc.scene

import android.util.Log
import com.example.cornercivilization.p37f776dc.domain.model.*
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.OpacityControllerComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.*

internal data class PlotEffect(val visible: Boolean, val ghost: Boolean, val scale: Float)

/** Persistent consequences come from the snapshot; selection only adds a ghost. */
internal fun plotEffect(plot: PlotState, kind: InterventionKind, selected: InterventionKind?): PlotEffect {
    val amount = when (kind) {
        InterventionKind.WATER -> plot.moisture.coerceIn(0, 100) / 100f
        InterventionKind.FIRE -> plot.fire.coerceIn(0, 100) / 100f
        InterventionKind.GRANT_RESOURCE -> plot.resourceUnits.coerceIn(0L, 100L) / 100f
    }
    val ghost = amount == 0f && selected == kind
    return PlotEffect(amount > 0f || ghost, ghost, if (ghost) 1f else 1f + amount * .5f)
}

/** Exactly 27 plot effects plus six resource carriers/celebration parts. */
internal class SceneEffects(private val world: Entity, private val plots: List<Entity>) {
    val carrierRoot = Entity().apply { setName("RuntimeResourceCarriers"); world.addChild(this) }
    private data class Effect(val entity: Entity, val opacity: OpacityControllerComponent)
    private fun clone(name: String, parent: Entity, prototype: Entity): Effect {
        val entity = requireNotNull(prototype.clone(Entity.CloneOptions(recursive = true, shouldShareMaterialInstance = true))) { "Could not clone genuine SGS prototype $name" }
        entity.setName(name)
        entity.enableTree()
        parent.addChild(entity)
        val opacity = OpacityControllerComponent(1f).also { entity.components.set(it) }
        return Effect(entity, opacity)
    }
    private val prototypes = mapOf(
        InterventionKind.FIRE to world.requireNode("CampfirePrototype"),
        InterventionKind.WATER to world.requireNode("WaterdropPrototype"),
        InterventionKind.GRANT_RESOURCE to world.requireNode("ResourcePrototype"),
    )
    private val plotEffects = plots.mapIndexed { index, parent ->
        InterventionKind.entries.associateWith { kind -> clone("PlotEffect_${index}_${kind.name}", parent, requireNotNull(prototypes[kind])) }
    }
    private val carriers = List(6) { clone("ResourceCarrier_$it", carrierRoot, requireNotNull(prototypes[InterventionKind.GRANT_RESOURCE])) }

    init {
        world.requireNode("Effects").enabled = false
        Log.i("CivilizationScene", "native-instances managedModels=53 archetypes=20 plotClones=27 carrierClones=6 totalClones=33 totalModelInstances=86 sourceTrianglesPlanning=165348 cloneTrianglesPlanning=22350 maximumTrianglesPlanning=187698 measuredTriangles=UNOBSERVED")
    }

    fun update(state: WorldState, selected: InterventionKind?, seconds: Float, residents: List<Entity>, victory: Float?, invaderOrigin: Vector3) {
        plotEffects.forEachIndexed { index, effects ->
            val plot = state.city.plots.getOrNull(index) ?: return@forEachIndexed
            effects.forEach { (kind, effect) ->
                val visual = plotEffect(plot, kind, selected.takeIf { state.invasion == null })
                effect.entity.enabled = visual.visible
                effect.opacity.opacity = if (visual.ghost) .34f else 1f
                effect.entity.transform().apply {
                    val lateral = when (kind) { InterventionKind.WATER -> -.012f; InterventionKind.FIRE -> 0f; InterventionKind.GRANT_RESOURCE -> .012f }
                    position = Vector3(lateral, if (visual.ghost) .012f + sin(seconds * 2f + index) * .0015f else .001f, 0f)
                    scaleVector = Vector3(visual.scale)
                    eulerAngles = EulerAngles(0f, if (visual.ghost) sin(seconds + index) * 10f else 0f, 0f)
                }
            }
        }
        carriers.forEachIndexed { index, effect ->
            if (victory != null) {
                val progress = (victory / SceneFeedback.VICTORY_SECONDS).coerceIn(0f, 1f)
                val angle = index * PI.toFloat() / 3f
                effect.entity.enabled = true
                effect.opacity.opacity = 1f - progress
                effect.entity.transform().apply {
                    position = invaderOrigin + Vector3(cos(angle) * .035f * progress, .003f + sin(progress * PI.toFloat()) * .006f - .020f * progress, sin(angle) * .028f * progress)
                    scaleVector = Vector3(.55f * (1f - progress * .8f))
                    eulerAngles = EulerAngles(progress * 60f, index * 60f + progress * 120f, progress * 45f)
                }
            } else {
                val action = state.city.residents.getOrNull(index)?.action
                effect.entity.enabled = state.city.resources > 0 && (action == ResidentAction.GATHER || action == ResidentAction.BUILD)
                effect.opacity.opacity = 1f
                effect.entity.transform().apply {
                    position = residents[index].convertPositionTo(Vector3(0f, .012f, .004f), carrierRoot)
                    scaleVector = Vector3(.55f)
                    eulerAngles = EulerAngles(0f, sin(seconds * 2f + index) * 12f, 0f)
                }
            }
        }
    }

    /** Actual source geometry at maximum owner transforms; no invented art bounds. */
    fun measureEnvelope(reference: Entity): BoundingBox {
        val measured = mutableListOf<BoundingBox>()
        plotEffects.forEach { effects -> effects.values.forEach { effect ->
            val transform = effect.entity.transform()
            for (height in listOf(.001f, .0135f)) for (yaw in listOf(-10f, 0f, 10f)) {
                transform.position = Vector3(when (effect) { effects[InterventionKind.WATER] -> -.012f; effects[InterventionKind.GRANT_RESOURCE] -> .012f; else -> 0f }, height, 0f)
                transform.scaleVector = Vector3(1.5f)
                transform.eulerAngles = EulerAngles(0f, yaw, 0f)
                measured += effect.entity.getVisualBounds(reference, recursive = true, enabledOnly = false)
            }
        } }
        // Carriers ride all six residents, including safe retreat and celebration.
        carriers.forEachIndexed { index, effect ->
            val resident = world.requireNode("Resident_$index")
            val center = resident.convertPositionTo(Vector3.ZERO, carrierRoot)
            for (x in listOf(-.0051f, .0051f)) for (z in listOf(-.026f, .0085f)) for (y in listOf(.012f, .0206f)) for (yaw in listOf(-27f, 0f, 27f)) {
                effect.entity.transform().apply { position = center + Vector3(x, y, z); scaleVector = Vector3(.55f); eulerAngles = EulerAngles(0f, yaw, 0f) }
                measured += effect.entity.getVisualBounds(reference, recursive = true, enabledOnly = false)
            }
        }
        val origin = world.requireNode("InvaderOwner").convertPositionTo(Vector3.ZERO, carrierRoot)
        carriers.forEachIndexed { index, effect ->
            for (sample in 0..16) {
                val progress = sample / 16f
                val angle = index * PI.toFloat() / 3f
                effect.entity.transform().apply {
                    position = origin + Vector3(cos(angle) * .035f * progress, .003f + sin(progress * PI.toFloat()) * .006f - .020f * progress, sin(angle) * .028f * progress)
                    scaleVector = Vector3(.55f * (1f - progress * .8f))
                    eulerAngles = EulerAngles(progress * 60f, index * 60f + progress * 120f, progress * 45f)
                }
                measured += effect.entity.getVisualBounds(reference, recursive = true, enabledOnly = false)
            }
        }
        // Continuous rotation between samples has a bounded corner radius. The
        // 0.5mm allowance also covers the parabolic arc's sampling interval.
        val envelope = measured.filterNot { it.isEmpty() }.reduce(::unionBounds)
        val margin = Vector3(.0005f)
        Log.i("CivilizationScene", "effect-motion clones=33 visualBoundsSamples=${measured.size} owner=$reference bounds=$envelope samplingAllowanceMeters=.0005")
        return boundsOf(listOf(envelope.min - margin, envelope.max + margin))
    }
}
