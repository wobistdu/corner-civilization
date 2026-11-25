package com.example.cornercivilization.p37f776dc.data.repository

import com.example.cornercivilization.p37f776dc.domain.model.*
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldEngine
import com.example.cornercivilization.p37f776dc.domain.usecase.WorldFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.time.LocalDate
import java.util.zip.CRC32

class WorldRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val date = LocalDate.of(2026, 11, 10)
    private val engine = WorldEngine()
    private val codec = WorldSnapshotCodec()
    private fun fresh() = WorldFactory.create(date, seed = 51)
    private fun snapshot(state: WorldState = fresh()) = WorldSnapshot(state, "Asia/Shanghai")
    private fun advance(state: WorldState, millis: Long) = engine.dispatch(state,
        WorldCommand.Tick(TimeSlice(state.day.date, millis, true, true))).state
    private fun loaded(repository: WorldRepository) = repository.load() as LoadResult.Loaded

    @Test fun `binary roundtrip preserves battle city resident seeds and dates deterministically`() {
        var state = advance(fresh().copy(era = Era.SPACE), 305_000)
        state = engine.dispatch(state, WorldCommand.Intervene(InterventionKind.WATER, "plot-2", 10)).state
        repeat(9) { sequence ->
            val invasion = requireNotNull(state.invasion)
            state = engine.dispatch(state, WorldCommand.TargetHit(invasion.eventId, invasion.targetId,
                sequence.toLong(), true, true)).state
        }
        state = advance(state, 13_000)
        val original = snapshot(state)
        val bytes = codec.encode(original)
        assertArrayEquals(bytes, codec.encode(original))
        val decoded = codec.decode(bytes)
        assertEquals(original, decoded)
        assertEquals(9, decoded.state.invasion?.hits)
        assertEquals(7_000L, decoded.state.invasion?.remainingMillis)
        // No wall-clock interval is applied merely by decoding or opening a later date.
        val resumed = engine.dispatch(decoded.state, WorldCommand.Open(date.plusDays(2))).state
        assertEquals(decoded.state.invasion, resumed.invasion)
        assertEquals(0L, resumed.day.growthMillis)
    }

    @Test fun `atomic file saves restore current snapshot and corruption falls back to previous`() {
        val directory = temporary.newFolder()
        val repository = AtomicFileWorldRepository(directory)
        assertEquals(LoadResult.Empty, repository.load())
        val first = snapshot()
        val second = snapshot(advance(first.state, 601_000))
        assertTrue(repository.save(first) is SaveResult.Saved)
        assertTrue(repository.save(second) is SaveResult.Saved)
        assertEquals(second, loaded(repository).snapshot)
        File(directory, "world.bin").writeBytes(byteArrayOf(1, 2, 3))
        val recovered = loaded(repository)
        assertTrue(recovered.recoveredFromBackup)
        assertEquals(first, recovered.snapshot)
        assertTrue(repository.save(first) is SaveResult.Saved)
        assertFalse(loaded(repository).recoveredFromBackup)
        assertEquals(first, loaded(repository).snapshot)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(directory, "world.bin.corrupt").readBytes())
        assertEquals(first, codec.decode(File(directory, "world.bin.bak").readBytes()))
    }

    @Test fun `warning with two seconds left survives new repository and next-day open`() {
        val directory = temporary.newFolder()
        val warning = advance(fresh().copy(era = Era.SPACE), 303000)
        assertEquals(InvasionPhase.WARNING, warning.invasion!!.phase)
        assertEquals(2000L, warning.invasion!!.remainingMillis)
        assertTrue(AtomicFileWorldRepository(directory).save(snapshot(warning)) is SaveResult.Saved)
        val restored = loaded(AtomicFileWorldRepository(directory)).snapshot.state
        assertEquals(warning, restored)
        val resumed = engine.dispatch(restored, WorldCommand.Open(date.plusDays(1))).state
        assertEquals(warning.invasion, resumed.invasion)
        assertEquals(0L, resumed.day.growthMillis)
        assertTrue(AtomicFileWorldRepository(directory).save(snapshot(resumed)) is SaveResult.Saved)
        val reopened = loaded(AtomicFileWorldRepository(directory)).snapshot.state
        assertEquals(resumed, reopened)
        val paused = engine.dispatch(reopened,
            WorldCommand.Tick(TimeSlice(reopened.day.date, 1000, false, false))).state
        assertEquals(reopened, paused)
        val almost = advance(paused, 1999)
        assertEquals(InvasionPhase.WARNING, almost.invasion!!.phase)
        assertEquals(1L, almost.invasion!!.remainingMillis)
        val fighting = advance(almost, 1)
        assertEquals(InvasionPhase.FIGHTING, fighting.invasion!!.phase)
        assertEquals(20000L, fighting.invasion!!.remainingMillis)
    }

    @Test fun `interrupted primary replace keeps complete old snapshot and permits retry`() {
        val directory = temporary.newFolder()
        val repository = AtomicFileWorldRepository(directory)
        val old = snapshot()
        repository.save(old)
        val next = snapshot(advance(old.state, 601_000))
        val failing = object : AtomicFileWorldRepository(directory) {
            override fun beforePrimaryReplace() { throw IOException("injected rename failure") }
        }
        assertTrue(failing.save(next) is SaveResult.Failed)
        assertEquals(old, loaded(repository).snapshot)
        assertTrue(repository.save(next) is SaveResult.Saved)
        assertEquals(next, loaded(repository).snapshot)
    }

    @Test fun `dual corruption reports error and explicitly new world preserves both originals`() {
        val directory = temporary.newFolder()
        val repository = AtomicFileWorldRepository(directory)
        repository.save(snapshot())
        repository.save(snapshot(advance(fresh(), 1_000)))
        val badPrimary = byteArrayOf(7, 2, 4)
        val badBackup = byteArrayOf(4, 3)
        File(directory, "world.bin").writeBytes(badPrimary)
        File(directory, "world.bin.bak").writeBytes(badBackup)
        assertTrue(repository.load() is LoadResult.Corrupt)
        val explicitlyNew = snapshot(WorldFactory.create(date, lineageId = "recovered"))
        assertTrue(repository.save(explicitlyNew) is SaveResult.Saved)
        assertEquals(explicitlyNew, loaded(repository).snapshot)
        assertArrayEquals(badPrimary, File(directory, "world.bin.corrupt").readBytes())
        assertArrayEquals(badBackup, File(directory, "world.bin.bak.corrupt").readBytes())
    }

    @Test fun `unknown version is reported and is never overwritten by a new world`() {
        val directory = temporary.newFolder()
        val bytes = codec.encode(snapshot())
        ByteBuffer.wrap(bytes).putInt(4, 99)
        File(directory, "world.bin").writeBytes(bytes)
        val repository = AtomicFileWorldRepository(directory)
        assertEquals(LoadResult.UnsupportedVersion(99), repository.load())
        assertTrue(repository.save(snapshot()) is SaveResult.Failed)
        assertArrayEquals(bytes, File(directory, "world.bin").readBytes())
    }

    @Test fun `future rule configuration is unsupported rather than recoverable corruption`() {
        val directory = temporary.newFolder()
        val bytes = codec.encode(snapshot())
        ByteBuffer.wrap(bytes).putInt(12, 2)
        reseal(bytes)
        File(directory, "world.bin").writeBytes(bytes)
        val repository = AtomicFileWorldRepository(directory)
        assertEquals(LoadResult.UnsupportedVersion(2), repository.load())
        assertTrue(repository.save(snapshot()) is SaveResult.Failed)
        assertArrayEquals(bytes, File(directory, "world.bin").readBytes())
    }

    @Test fun `truncated checksum damaged trailing and oversized inputs are rejected`() {
        val valid = codec.encode(snapshot())
        val corrupt = valid.copyOf().also { it[20] = (it[20].toInt() xor 1).toByte() }
        val malformed = listOf(valid.copyOf(17), corrupt, valid + byteArrayOf(0),
            ByteArray(WorldSnapshotCodec.MAX_BYTES + 1))
        malformed.forEach { bytes ->
            try { codec.decode(bytes); fail("malformed snapshot accepted") }
            catch (_: IllegalArgumentException) { /* Expected before unbounded allocation. */ }
        }
    }

    @Test fun `valid checksum does not bypass collection string or enum bounds`() {
        val original = snapshot()
        val textLength = codec.encode(original)
        // Header(12), rule version(4), then timezone byte length.
        ByteBuffer.wrap(textLength).putInt(16, Int.MAX_VALUE)
        reseal(textLength)
        try { codec.decode(textLength); fail("unbounded text accepted") }
        catch (_: IllegalArgumentException) { }
        val enumValue = codec.encode(original)
        val eraOffset = 12 + 4 + 4 + original.timezoneId.toByteArray().size +
            4 + original.state.civilizationId.toByteArray().size
        ByteBuffer.wrap(enumValue).putInt(eraOffset, Int.MAX_VALUE)
        reseal(enumValue)
        try { codec.decode(enumValue); fail("invalid enum accepted") }
        catch (_: IllegalArgumentException) { }
        val excessive = original.copy(state = original.state.copy(city = original.state.city.copy(
            plots = List(257) { PlotState("plot-$it") })))
        try { codec.encode(excessive); fail("oversized collection accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun `stale writes conflicts and abandoned temp cannot overwrite committed world`() {
        val directory = temporary.newFolder()
        val repository = AtomicFileWorldRepository(directory)
        val old = snapshot()
        val next = snapshot(advance(old.state, 600_001))
        repository.save(old)
        repository.save(next)
        assertTrue(repository.save(old) is SaveResult.Failed)
        val conflict = next.copy(state = next.state.copy(city = next.state.city.copy(resources = 999)))
        assertTrue(repository.save(conflict) is SaveResult.Failed)
        File(directory, "world.bin.tmp").writeBytes(codec.encode(old))
        assertEquals(next, loaded(repository).snapshot)
    }

    @Test fun `snapshot rejects inconsistent qualification and impossible dead-style state instead of loading it`() {
        val state = advance(fresh(), 600_001)
        val invalidQualification = state.copy(qualification = state.qualification?.copy(civilizationId = "old"))
        try { codec.encode(snapshot(invalidQualification)); fail("foreign qualification accepted") }
        catch (_: IllegalArgumentException) { }
        val invalidCity = state.copy(city = state.city.copy(residents = emptyList()))
        try { codec.encode(snapshot(invalidCity)); fail("empty resident population accepted") }
        catch (_: IllegalArgumentException) { }
    }

    private fun reseal(bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes, 0, bytes.size - 8) }.value
        ByteBuffer.wrap(bytes).putLong(bytes.size - 8, crc)
    }
}
