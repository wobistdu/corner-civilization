package com.example.cornercivilization.p37f776dc.data.repository

import android.util.AtomicFile
import com.example.cornercivilization.p37f776dc.domain.model.*
import java.io.File
import org.json.JSONObject

interface FireSliceStore {
    suspend fun load(): FireSliceState
    suspend fun save(state: FireSliceState)
}

/** Own file and schema; neither reads nor migrates the old world save. */
class FireSliceRepository(directory: File) : FireSliceStore {
    private val file = AtomicFile(File(directory, "primitive-fire-slice-v1.json"))
    override suspend fun load(): FireSliceState {
        val text = try { file.openRead().use { it.bufferedReader().readText() } }
        catch (_: java.io.FileNotFoundException) { return FireSliceState() }
        val j = JSONObject(text)
        val schema = j.getInt("schema")
        check(schema in 1..4) { "Unsupported fire slice save" }
        return FireSliceState(FirePhase.valueOf(j.getString("phase")), j.getLong("phaseMillis"),
            j.getLong("activeMillis"), j.getInt("strength"), j.getBoolean("knowsFire"),
            j.getInt("ignitions"), j.getLong("waterSequence"), j.getLong("lastWaterAt"),
            shelter = ShelterPhase.valueOf(j.optString("shelter", if (j.getBoolean("knowsFire") && j.getLong("waterSequence") > 0) "NEEDED" else "NONE")),
            sourceWood = j.optInt("sourceWood", 8), carriedWood = j.optInt("carriedWood", 0),
            spentWood = j.optInt("spentWood", 0), buildMillis = j.optLong("buildMillis", 0),
            rainDemonstrated = j.optBoolean("rainDemonstrated", false), protectedDoses = j.optInt("protectedDoses", 0),
            evolutionMillis = j.optLong("evolutionMillis", 0),
            resourcePhase = ResourcePhase.valueOf(j.optString("resourcePhase", "WAITING")),
            pendingWood = j.optInt("pendingWood", 4), haulingWood = j.optInt("haulingWood", 0),
            storedWood = j.optInt("storedWood", 0), usedWood = j.optInt("usedWood", 0),
            suppliedWood = j.optInt("suppliedWood", 4), deliveries = j.optInt("deliveries", 0),
            weatherCycle = j.optLong("weatherCycle", 0), forageCycle = j.optLong("forageCycle", 0),
            fuelCycle = j.optLong("fuelCycle", 0), lastGiftAt = j.optLong("lastGiftAt", -10_000),
            fieldEstablished = FireSliceMigration.fieldEstablished(schema, j.optLong("evolutionMillis", 0), j.optBoolean("fieldEstablished", false)),
            seenTools = j.optInt("seenTools", 0))
    }
    override suspend fun save(state: FireSliceState) {
        val j = JSONObject().put("schema", 4).put("phase", state.phase.name)
            .put("phaseMillis", state.phaseMillis).put("activeMillis", state.activeMillis)
            .put("strength", state.strength).put("knowsFire", state.knowsFire)
            .put("ignitions", state.ignitions).put("waterSequence", state.waterSequence)
            .put("lastWaterAt", state.lastWaterAt).put("shelter", state.shelter.name)
            .put("sourceWood", state.sourceWood).put("carriedWood", state.carriedWood).put("spentWood", state.spentWood)
            .put("buildMillis", state.buildMillis).put("rainDemonstrated", state.rainDemonstrated).put("protectedDoses", state.protectedDoses)
            .put("evolutionMillis", state.evolutionMillis).put("resourcePhase", state.resourcePhase.name)
            .put("pendingWood", state.pendingWood).put("haulingWood", state.haulingWood)
            .put("storedWood", state.storedWood).put("usedWood", state.usedWood).put("suppliedWood", state.suppliedWood)
            .put("deliveries", state.deliveries).put("weatherCycle", state.weatherCycle)
            .put("forageCycle", state.forageCycle).put("fuelCycle", state.fuelCycle).put("lastGiftAt", state.lastGiftAt)
            .put("fieldEstablished", state.fieldEstablished).put("seenTools", state.seenTools)
        val stream = file.startWrite()
        try { stream.write(j.toString().toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream); throw error }
    }
}
