package com.example.cornercivilization.p37f776dc

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cornercivilization.p37f776dc.data.repository.FireSliceRepository
import com.example.cornercivilization.p37f776dc.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ShelterPersistenceTest {
    @Test fun v1MigratesAndV2RoundTripsWithoutTouchingPlayerSave() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".acceptance")) { "Device tests require the isolated acceptance package" }
        val cache = context.cacheDir
        val directory = File(cache, "shelter-persistence-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(directory, "primitive-fire-slice-v1.json").writeText("""{"schema":1,"phase":"BURNING","phaseMillis":0,"activeMillis":99000,"strength":45,"knowsFire":true,"ignitions":2,"waterSequence":2,"lastWaterAt":60000}""")
            val store = FireSliceRepository(directory)
            val migrated = store.load()
            assertEquals(ShelterPhase.NEEDED, migrated.shelter)
            assertEquals(8, migrated.sourceWood)
            assertEquals(0L, migrated.evolutionMillis)
            assertEquals(4, migrated.pendingWood)
            val building = migrated.copy(shelter=ShelterPhase.BUILDING, sourceWood=0, spentWood=8, buildMillis=6200)
            store.save(building)
            assertEquals(building, FireSliceRepository(directory).load())
            val complete = building.copy(shelter=ShelterPhase.COMPLETE, buildMillis=12000, protectedDoses=3)
            store.save(complete)
            assertEquals(complete, FireSliceRepository(directory).load())
            val evolved = complete.copy(evolutionMillis=600_123, resourcePhase=ResourcePhase.CARRYING,
                pendingWood=0, haulingWood=4, weatherCycle=5, forageCycle=10, fuelCycle=20, lastGiftAt=5_000)
            store.save(evolved)
            val reloaded = FireSliceRepository(directory).load()
            assertEquals(evolved, reloaded)
            assertTrue(reloaded.agricultural)
            assertEquals(4, reloaded.haulingWood)
        } finally { directory.deleteRecursively() }
    }
}
