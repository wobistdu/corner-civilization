package com.example.cornercivilization.p37f776dc.data.repository

import com.example.cornercivilization.p37f776dc.domain.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.CRC32

class UnsupportedSnapshotVersionException(val version: Int) : IllegalArgumentException("Unsupported schema $version")

/** Deterministic, bounded format. No Java deserialization or Android JSON dependencies. */
class WorldSnapshotCodec {
    fun encode(snapshot: WorldSnapshot): ByteArray {
        require(snapshot.schemaVersion == SCHEMA_VERSION) { "Unsupported schema ${snapshot.schemaVersion}" }
        validate(snapshot)
        val payload = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(snapshot.ruleConfigVersion)
                out.text(snapshot.timezoneId)
                out.world(snapshot.state)
            }
        }.toByteArray()
        require(payload.size <= MAX_BYTES - 20) { "Snapshot too large" }
        val prefix = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(SCHEMA_VERSION)
                out.writeInt(payload.size)
                out.write(payload)
            }
        }.toByteArray()
        val checksum = CRC32().apply { update(prefix) }.value
        return ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out -> out.write(prefix); out.writeLong(checksum) }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): WorldSnapshot {
        require(bytes.size in 20..MAX_BYTES) { "Invalid snapshot size" }
        val input = DataInputStream(ByteArrayInputStream(bytes))
        require(input.readInt() == MAGIC) { "Invalid magic" }
        val version = input.readInt()
        if (version != SCHEMA_VERSION) throw UnsupportedSnapshotVersionException(version)
        val length = input.readInt()
        require(length >= 0 && length == bytes.size - 20) { "Invalid payload length" }
        val expected = ByteBuffer.wrap(bytes, bytes.size - 8, 8).long
        require(expected == CRC32().apply { update(bytes, 0, bytes.size - 8) }.value) { "Checksum mismatch" }
        val payload = ByteArray(length)
        input.readFully(payload)
        input.readLong()
        val body = DataInputStream(ByteArrayInputStream(payload))
        val configVersion = body.readInt()
        if (configVersion != 1) throw UnsupportedSnapshotVersionException(configVersion)
        val timezone = body.text()
        val world = body.world()
        require(body.available() == 0) { "Trailing payload" }
        return WorldSnapshot(world, timezone, configVersion, version).also(::validate)
    }

    private fun validate(snapshot: WorldSnapshot) {
        require(snapshot.ruleConfigVersion == 1) { "Unsupported rule configuration" }
        ZoneId.of(snapshot.timezoneId)
        val state = snapshot.state
        require(state.civilizationId.isNotBlank() && state.lineageId.isNotBlank())
        require(state.lineageId.length <= 128 && state.civilizationGeneration >= 0 && state.revision >= 0)
        require(state.civilizationId == "${state.lineageId}:${state.civilizationGeneration}")
        require(state.day.growthMillis >= 0 && state.day.spaceUseMillis >= 0)
        require(state.day.spaceUseMillis <= state.day.growthMillis)
        require(state.era == Era.SPACE || state.day.spaceUseMillis == 0L)
        listOfNotNull(state.lastOpenDate, state.lastUpgradeDate, state.lastInvasionHandledDate).forEach {
            require(it <= state.day.date) { "Future marker" }
        }
        state.qualification?.let {
            require(it.civilizationId == state.civilizationId && it.sourceEra == state.era &&
                it.sourceEra.next() == it.targetEra && it.earnedOnDate <= state.day.date)
        }
        state.invasion?.let {
            require(state.era == Era.SPACE && it.civilizationId == state.civilizationId)
            require(it.eventId.isNotBlank() && it.targetId.isNotBlank() && it.triggerDate <= state.day.date)
            require(it.remainingMillis > 0 && it.hits in 0 until RuleConfig.REQUIRED_HITS)
            require(it.lastAcceptedInputSequence >= -1)
            require(it.phase != InvasionPhase.WARNING || (it.hits == 0 && it.lastAcceptedInputSequence == -1L))
            require(it.hits == 0 || it.lastAcceptedInputSequence >= 0)
        }
        val city = state.city
        require(city.resources >= 0 && city.constructionProgress >= 0 && city.autonomyMillis in 0..4_999)
        require(city.lastInterventionSequence >= -1)
        require(city.plots.size in 1..MAX_PLOTS && city.residents.size in 1..MAX_RESIDENTS)
        require(city.plots.map { it.id }.distinct().size == city.plots.size)
        require(city.residents.map { it.id }.distinct().size == city.residents.size)
        city.plots.forEach { require(it.id.isNotBlank() && it.moisture in 0..100 && it.fire in 0..100 && it.resourceUnits >= 0) }
        city.residents.forEach { require(it.id.isNotBlank()) }
    }

    private fun DataOutputStream.world(state: WorldState) {
        text(state.civilizationId); writeInt(state.era.ordinal)
        date(state.day.date); writeLong(state.day.growthMillis); writeLong(state.day.spaceUseMillis)
        nullable(state.qualification) { q ->
            text(q.id); text(q.civilizationId); date(q.earnedOnDate)
            writeInt(q.sourceEra.ordinal); writeInt(q.targetEra.ordinal)
        }
        nullableDate(state.lastUpgradeDate); nullableDate(state.lastOpenDate)
        nullable(state.invasion) { invasion ->
            text(invasion.eventId); text(invasion.civilizationId); date(invasion.triggerDate); text(invasion.targetId)
            writeInt(invasion.phase.ordinal); writeLong(invasion.remainingMillis)
            writeInt(invasion.hits); writeLong(invasion.lastAcceptedInputSequence)
        }
        nullableDate(state.lastInvasionHandledDate)
        writeLong(state.revision); text(state.lineageId); writeLong(state.civilizationGeneration)
        val city = state.city
        writeLong(city.seed); writeLong(city.resources); writeLong(city.lastInterventionSequence)
        writeLong(city.autonomyMillis); writeLong(city.constructionProgress)
        writeInt(city.plots.size)
        city.plots.forEach { text(it.id); writeInt(it.moisture); writeInt(it.fire); writeLong(it.resourceUnits) }
        writeInt(city.residents.size)
        city.residents.forEach { text(it.id); writeInt(it.action.ordinal); writeLong(it.actionSeed) }
    }

    private fun DataInputStream.world(): WorldState {
        val civilizationId = text()
        val era = enumValue<Era>()
        val day = DayProgress(date(), readLong(), readLong())
        val qualification = nullable {
            GrowthQualification(text(), text(), date(), enumValue(), enumValue())
        }
        val lastUpgrade = nullableDate()
        val lastOpen = nullableDate()
        val invasion = nullable {
            InvasionState(text(), text(), date(), text(), enumValue(), readLong(), readInt(), readLong())
        }
        val handled = nullableDate()
        val revision = readLong()
        val lineage = text()
        val generation = readLong()
        val seed = readLong()
        val resources = readLong()
        val lastIntervention = readLong()
        val autonomy = readLong()
        val construction = readLong()
        val plots = List(count(MAX_PLOTS)) { PlotState(text(), readInt(), readInt(), readLong()) }
        val residents = List(count(MAX_RESIDENTS)) { ResidentState(text(), enumValue(), readLong()) }
        return WorldState(civilizationId, era, day,
            CityState(seed, resources, plots, residents, lastIntervention, autonomy, construction),
            qualification, lastUpgrade, lastOpen, invasion, handled, revision, lineage, generation)
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TEXT_BYTES) { "Text too long" }
        writeInt(bytes.size); write(bytes)
    }

    private fun DataInputStream.text(): String {
        val size = readInt()
        require(size in 0..MAX_TEXT_BYTES && size <= available()) { "Invalid text size" }
        val bytes = ByteArray(size).also(::readFully)
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }

    private fun DataOutputStream.date(value: LocalDate) = writeLong(value.toEpochDay())
    private fun DataInputStream.date(): LocalDate = LocalDate.ofEpochDay(readLong())
    private fun DataOutputStream.nullableDate(value: LocalDate?) = nullable(value) { date(it) }
    private fun DataInputStream.nullableDate(): LocalDate? = nullable { date() }
    private fun <T> DataOutputStream.nullable(value: T?, writer: DataOutputStream.(T) -> Unit) {
        writeBoolean(value != null); if (value != null) writer(this, value)
    }
    private fun <T> DataInputStream.nullable(reader: DataInputStream.() -> T): T? = when (readUnsignedByte()) {
        0 -> null
        1 -> reader(this)
        else -> throw IllegalArgumentException("Invalid nullable marker")
    }
    private inline fun <reified T : Enum<T>> DataInputStream.enumValue(): T {
        val values = enumValues<T>()
        return values.getOrNull(readInt()) ?: throw IllegalArgumentException("Invalid enum")
    }
    private fun DataInputStream.count(max: Int): Int = readInt().also { require(it in 1..max) { "Invalid count" } }

    companion object {
        const val SCHEMA_VERSION = 1
        const val MAX_BYTES = 4 * 1024 * 1024
        private const val MAGIC = 0x44434C56
        private const val MAX_TEXT_BYTES = 1_024
        private const val MAX_PLOTS = 256
        private const val MAX_RESIDENTS = 128
    }
}
