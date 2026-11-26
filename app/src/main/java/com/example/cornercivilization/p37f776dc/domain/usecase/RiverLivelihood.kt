package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point
import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.WoodPhase

/** Work starts from an offer, travels through normal navigation, and commits only on arrival. */
internal class RiverLivelihood(private val world: LocalInterventionWorld, private val village: LivingVillage) {
    data class State(val softBank: Float = 0f, val blank: Boolean = false, val cistern: Boolean = false,
        val trapUses: Int = 0, val catchProgress: Float = 0f) {
        init {
            require(softBank in 0f..90f && !(blank && cistern))
            require(trapUses in 0..3 && catchProgress in 0f..1f && (trapUses > 0 || catchProgress == 0f))
        }
    }
    var state = State()
        private set
    fun restore(saved: State) { state = saved }

    private val life get() = village.life
    private val bank get() = village.ecology.bank
    private fun dryWood() = world.woods.firstOrNull { it.phase == WoodPhase.STACKED && it.carrier == null }
    val rules = listOf(
        VillageWorkRule(VillageEvent.MOULD_CLAY, { bank }, 75f, 3f,
            { state.softBank > 0f && !state.blank && !state.cistern }, ::carryClay, ::deliverClay),
        VillageWorkRule(VillageEvent.FIRE_CISTERN, { world.depot }, 77f, 6f,
            { state.blank && life.woodWet < .6f && dryWood() != null }, { id, _, _ ->
                world.woods.remove(requireNotNull(dryWood()))
                state = state.copy(blank = false, cistern = true)
                life.completeRule(id, VillageEvent.FIRE_CISTERN, "耗去一捆干柴，烧成的陶缸可以接雨。")
            }),
        VillageWorkRule(VillageEvent.WEAVE_FISH_TRAP, { bank }, 68f, 5f,
            { life.clock >= 32f && state.trapUses == 0 && life.bankReeds >= 2f && village.ecology.food <= 3 }, { id, _, _ ->
                life.bankReeds -= 2f
                state = state.copy(trapUses = 3)
                life.completeRule(id, VillageEvent.WEAVE_FISH_TRAP, "用了两束岸苇，编成可收三回的鱼篓。")
            }),
        VillageWorkRule(VillageEvent.LIFT_FISH_TRAP, { bank }, 76f, 2.5f,
            { state.catchProgress >= 1f && village.ecology.food + village.foodBundles.size < village.ecology.foodCapacity }, { id, _, at ->
                state = state.copy(trapUses = state.trapUses - 1, catchProgress = 0f)
                village.foodBundles += LivingVillage.FoodBundle(at, origin = "riverFish")
                life.foodProduced++
                life.completeRule(id, VillageEvent.LIFT_FISH_TRAP, "岸边多了一份鲜鱼，须搬回村里才能入库。")
                if (state.trapUses == 0) world.tell("老鱼篓散了架，河岸上又得有人割苇编篓了。")
            })
    )
    fun advance(dt: Float) {
        val wet = world.wetAt(bank)
        val soft = if (wet > .15f) 90f else (state.softBank - dt).coerceAtLeast(0f)
        if (soft > 0f && state.softBank == 0f && !state.blank && !state.cistern)
            world.tell("雨把浅岸的硬泥泡软了。等云散开，这泥正好盘一口缸。")
        val progress = if (state.trapUses == 0) 0f else
            (state.catchProgress + dt * (1f + 2f * wet) / 50f).coerceAtMost(1f)
        if (state.cistern && life.water < 2f) {
            val old = life.water
            life.water = (old + world.rainAt(world.depot) * dt * .12f).coerceAtMost(2f)
            if (old < 1f && life.water >= 1f) world.tell("雨水顺着陶缸口流下来，村里又攒下了饮水。")
        }
        if (state.catchProgress < 1f && progress >= 1f) world.tell("浅岸的篓浮动了两下，有鱼钻进去了。")
        if (soft != state.softBank || progress != state.catchProgress)
            state = state.copy(softBank = soft, catchProgress = progress)
    }
    private fun deliverClay(id: String) {
        if (!state.blank && !state.cistern) {
            state = state.copy(blank = true)
            life.completeRule(id, VillageEvent.MOULD_CLAY, "把河泥盘成的缸坯送到柴堆旁。")
        } else village.jobs.remove(id)
    }
    private fun carryClay(id: String, job: LivingVillage.Job, at: Point) {
        val goal = village.nearestOpen(world.depot)
        val path = goal?.takeUnless(world::dangerous)?.let { world.navigation.path(at, it, world.residentRouteAllowed(id)) }
        if (path == null) {
            village.jobs.remove(id)
            life.defer(id, job)
        } else village.jobs[id] = job.copy(goal = goal, path = path.toMutableList(), carrying = true, elapsed = 0f)
    }
}
