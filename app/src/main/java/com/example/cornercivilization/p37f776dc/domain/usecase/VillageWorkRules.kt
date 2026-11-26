package com.example.cornercivilization.p37f776dc.domain.usecase

import com.example.cornercivilization.p37f776dc.domain.usecase.LocalInterventionWorld.Point

/** A pack describes opportunities. The resident scheduler compares their priorities with hunger,
 * danger and ordinary chores, and calls work only after the body reaches its current goal. */
internal data class VillageWorkRule(val event: VillageEvent, val site: () -> Point, val priority: Float,
    val seconds: Float, val ready: () -> Boolean, val commit: (String, LivingVillage.Job, Point) -> Unit,
    val deliver: ((String) -> Unit)? = null)

internal class VillageWorkRules(private val village: LivingVillage, packs: List<List<VillageWorkRule>>) {
    private val entries = packs.flatten().associateBy { it.event }
    init { require(entries.size == packs.sumOf { it.size }) { "Duplicate village work event" } }

    fun offer(submit: (VillageEvent, Point, Float) -> Unit) {
        for (rule in entries.values) {
            if (rule.ready() && village.jobs.values.none { it.event == rule.event })
                submit(rule.event, rule.site(), rule.priority)
        }
    }

    fun work(id: String, job: LivingVillage.Job, at: Point): Boolean {
        val rule = entries[job.event] ?: return false
        if (job.carrying && rule.deliver != null) rule.deliver.invoke(id)
        else if (job.elapsed >= rule.seconds) {
            if (rule.ready()) rule.commit(id, job, at) else village.jobs.remove(id)
        }
        return true
    }
}

internal fun fieldWorkRules(village: LivingVillage, life: VillageLife): List<VillageWorkRule> {
    val field = { village.ecology.field }
    return listOf(
        VillageWorkRule(VillageEvent.PULL_WEEDS, field, 46f, 4f, { village.agriculture && life.weeds > .35f }, { id, _, _ ->
            life.weeds = (life.weeds - .55f).coerceAtLeast(0f)
            val farmer = life.person(id)
            farmer.farmSkill = (farmer.farmSkill + .05f).coerceAtMost(1f)
            life.completeRule(id, VillageEvent.PULL_WEEDS, "除草降低争水")
        }),
        VillageWorkRule(VillageEvent.LOOSEN_SOIL, field, 43f, 4f, { village.agriculture && life.soilHard > .45f }, { id, _, _ ->
            life.soilHard = (life.soilHard - .6f).coerceAtLeast(0f)
            life.completeRule(id, VillageEvent.LOOSEN_SOIL, "松土恢复根系生长")
        }),
        VillageWorkRule(VillageEvent.PICK_PESTS, field, 44f, 4f, { village.agriculture && life.pests > .4f }, { id, _, _ ->
            life.pests = (life.pests - .6f).coerceAtLeast(0f)
            life.completeRule(id, VillageEvent.PICK_PESTS, "虫害减轻")
        })
    )
}
