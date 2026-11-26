package com.example.cornercivilization.p37f776dc.domain.model

enum class FirePhase { QUIET, APPROACH, KINDLING, BURNING, WET }
enum class FireTool { OBSERVE, WATER, FIRE, WOOD, DRUM, SEED }
enum class ResourcePhase { WAITING, FETCHING, CARRYING, IDLE }
enum class ShelterPhase { NONE, NEEDED, FETCHING, CARRYING, BUILDING, COMPLETE }

/** Isolated playtest state. Its clock must never be imported as era growth. */
data class FireSliceState(
    val phase: FirePhase = FirePhase.QUIET,
    val phaseMillis: Long = 0,
    val activeMillis: Long = 0,
    val strength: Int = 0,
    val knowsFire: Boolean = false,
    val ignitions: Int = 0,
    val waterSequence: Long = 0,
    val lastWaterAt: Long = -10_000,
    val shelter: ShelterPhase = ShelterPhase.NONE,
    val sourceWood: Int = 8,
    val carriedWood: Int = 0,
    val spentWood: Int = 0,
    val buildMillis: Long = 0,
    val rainDemonstrated: Boolean = false,
    val protectedDoses: Int = 0,
    // Internal process clock for weather and supply cycles, never a development gate.
    val evolutionMillis: Long = 0,
    val resourcePhase: ResourcePhase = ResourcePhase.WAITING,
    val pendingWood: Int = 4,
    val haulingWood: Int = 0,
    val storedWood: Int = 0,
    val usedWood: Int = 0,
    val suppliedWood: Int = 4,
    val deliveries: Int = 0,
    val weatherCycle: Long = 0,
    val forageCycle: Long = 0,
    val fuelCycle: Long = 0,
    val lastGiftAt: Long = -10_000,
    val fieldEstablished: Boolean = false,
    val seenTools: Int = 0,
) {
    val agricultural: Boolean get() = fieldEstablished
    val resourceRoom: Int get() = 24 - pendingWood - haulingWood - storedWood
    init {
        require(phaseMillis >= 0 && activeMillis >= 0 && strength in 0..100)
        require(ignitions >= 0 && waterSequence >= 0)
        require((phase == FirePhase.BURNING) == (strength > 0))
        require(sourceWood >= 0 && carriedWood >= 0 && spentWood >= 0)
        require(sourceWood + carriedWood + spentWood == 8)
        require(buildMillis in 0..12_000 && protectedDoses >= 0)
        require(shelter != ShelterPhase.COMPLETE || buildMillis == 12_000L)
        require(evolutionMillis >= 0 && weatherCycle >= 0 && forageCycle >= 0 && fuelCycle >= 0)
        require(listOf(pendingWood, haulingWood, storedWood, usedWood, suppliedWood, deliveries).all { it >= 0 })
        require(pendingWood <= 12 && haulingWood in 0..4 && resourceRoom >= 0)
        require(pendingWood.toLong() + haulingWood + storedWood + usedWood == suppliedWood.toLong())
        require((resourcePhase == ResourcePhase.CARRYING) == (haulingWood > 0))
        require(seenTools in 0 until (1 shl FireTool.entries.size))
    }
}
