package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.model.*

/** Small deterministic simulation: resource gathering, construction and non-lethal reactions. */
class CityUseCase(private val config: RuleConfig = RuleConfig()) {
    fun intervene(state: WorldState, command: WorldCommand.Intervene): Transition {
        val city = state.city
        if (command.inputSequence < 0 || command.inputSequence <= city.lastInterventionSequence)
            return rejected(state, "DUPLICATE_INTERVENTION")
        val plot = city.plots.firstOrNull { it.id == command.plotId }
            ?: return rejected(state, "UNKNOWN_PLOT")
        var resources = city.resources
        val changedPlot = when (command.kind) {
            InterventionKind.WATER -> plot.copy(
                moisture = (plot.moisture + config.waterMoisture).coerceAtMost(100),
                fire = (plot.fire - config.waterFireReduction).coerceAtLeast(0),
            )
            InterventionKind.FIRE -> plot.copy(fire = (plot.fire + config.fireIncrease).coerceAtMost(100))
            InterventionKind.GRANT_RESOURCE -> {
                resources = saturatedAdd(resources, config.resourceGrant)
                plot.copy(resourceUnits = saturatedAdd(plot.resourceUnits, config.resourceGrant))
            }
        }
        val plots = city.plots.map { if (it.id == plot.id) changedPlot else it }
        val residents = city.residents.mapIndexed { index, resident ->
            resident.copy(action = when {
                plots.any { it.fire > 0 } -> if (index % 3 == 0) ResidentAction.RESCUE else ResidentAction.EVADE
                command.kind == InterventionKind.GRANT_RESOURCE -> ResidentAction.BUILD
                else -> ResidentAction.GATHER
            })
        }
        val nextCity = city.copy(resources = resources, plots = plots, residents = residents,
            lastInterventionSequence = command.inputSequence)
        return updated(state, state.copy(city = nextCity), Persistence.CRITICAL, listOf(
            WorldEvent(WorldEventType.INTERVENTION_APPLIED, state.civilizationId, 0,
                plotId = plot.id, intervention = command.kind),
        ))
    }

    fun advance(state: WorldState, slice: TimeSlice): Transition {
        if (!slice.growthActive || slice.elapsedMillis <= 0) return Transition(state)
        val city = state.city
        // Compute without a per-second loop, even for a long injected slice.
        val total = saturatedAdd(city.autonomyMillis, slice.elapsedMillis)
        val steps = total / config.autonomyStepMillis
        val remainder = total % config.autonomyStepMillis
        if (steps == 0L) return updated(state, state.copy(city = city.copy(autonomyMillis = remainder)))
        val decay = steps.coerceAtMost(100).toInt()
        val plots = city.plots.map {
            it.copy(moisture = (it.moisture - decay).coerceAtLeast(0),
                fire = (it.fire - decay).coerceAtLeast(0))
        }
        val danger = plots.any { it.fire > 0 }
        val dangerSteps = (city.plots.maxOfOrNull { it.fire } ?: 0).minus(1).coerceAtLeast(0)
            .toLong().coerceAtMost(steps)
        val calmSteps = steps - dangerSteps
        val collected = saturatedMultiply(calmSteps, city.residents.size.toLong())
        val available = saturatedAdd(city.resources, collected)
        val built = minOf(calmSteps, available / config.buildResourceCost)
        val residents = city.residents.mapIndexed { index, resident ->
            val seed = resident.actionSeed + steps
            resident.copy(actionSeed = seed, action = when {
                danger -> if (index % 3 == 0) ResidentAction.RESCUE else ResidentAction.EVADE
                built > 0 && index % 2 == 0 -> ResidentAction.BUILD
                Math.floorMod(seed, 3L) == 0L -> ResidentAction.REST
                else -> ResidentAction.GATHER
            })
        }
        return updated(state, state.copy(city = city.copy(
            resources = available - built * config.buildResourceCost,
            plots = plots,
            residents = residents,
            autonomyMillis = remainder,
            constructionProgress = saturatedAdd(city.constructionProgress, built),
        )))
    }
}
