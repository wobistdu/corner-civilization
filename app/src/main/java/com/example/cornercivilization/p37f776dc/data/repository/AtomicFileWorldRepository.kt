package com.example.cornercivilization.p37f776dc.data.repository

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/** One application writer, same-directory atomic renames, and one previous valid snapshot. */
open class AtomicFileWorldRepository(private val directory: File) : WorldRepository {
    private val codec = WorldSnapshotCodec()
    private val primary get() = File(directory, "world.bin")
    private val backup get() = File(directory, "world.bin.bak")

    @Synchronized
    override fun load(): LoadResult {
        val current = inspect(primary)
        if (current is FileState.Valid) return LoadResult.Loaded(current.snapshot)
        if (current is FileState.Unsupported) return LoadResult.UnsupportedVersion(current.version)
        val previous = inspect(backup)
        if (previous is FileState.Valid) return LoadResult.Loaded(previous.snapshot, recoveredFromBackup = true)
        if (previous is FileState.Unsupported) return LoadResult.UnsupportedVersion(previous.version)
        if (current == FileState.Missing && previous == FileState.Missing) return LoadResult.Empty
        return LoadResult.Corrupt("No valid snapshot: primary=$current; backup=$previous")
    }

    @Synchronized
    override fun save(snapshot: WorldSnapshot): SaveResult {
        val temporary = File(directory, "world.bin.tmp")
        val backupTemporary = File(directory, "world.bin.bak.tmp")
        return try {
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create save directory" }
            val bytes = codec.encode(snapshot)
            val current = inspect(primary)
            val previous = inspect(backup)
            check(current !is FileState.Unsupported && previous !is FileState.Unsupported) {
                "UNSUPPORTED_EXISTING_VERSION"
            }
            val latest = (current as? FileState.Valid) ?: (previous as? FileState.Valid)
            if (latest != null) {
                check(snapshot.state.revision >= latest.snapshot.state.revision) { "STALE_REVISION" }
                if (snapshot.state.revision == latest.snapshot.state.revision) {
                    check(snapshot == latest.snapshot) { "REVISION_CONFLICT" }
                    // A recovered backup still needs the primary repaired atomically.
                    if (current is FileState.Valid) return SaveResult.Saved(snapshot.state.revision)
                }
            }
            writeSynced(temporary, bytes)
            if (current is FileState.Invalid) archiveCorrupt(primary)
            if (previous is FileState.Invalid) archiveCorrupt(backup)
            // Never rotate a corrupt primary over the last good backup.
            if (current is FileState.Valid) {
                writeSynced(backupTemporary, current.bytes)
                atomicReplace(backupTemporary, backup)
            }
            beforePrimaryReplace()
            atomicReplace(temporary, primary)
            SaveResult.Saved(snapshot.state.revision)
        } catch (error: Exception) {
            SaveResult.Failed(error.message ?: error.javaClass.simpleName)
        } finally {
            temporary.delete()
            backupTemporary.delete()
        }
    }

    /** Fault-injection point; production leaves this empty. The old primary remains intact here. */
    protected open fun beforePrimaryReplace() = Unit

    private fun inspect(file: File): FileState {
        if (!file.exists()) return FileState.Missing
        return try {
            val bytes = readBounded(file)
            FileState.Valid(codec.decode(bytes), bytes)
        } catch (error: UnsupportedSnapshotVersionException) {
            FileState.Unsupported(error.version)
        } catch (error: Exception) {
            FileState.Invalid(error.message ?: error.javaClass.simpleName)
        }
    }

    private fun readBounded(file: File): ByteArray {
        require(file.length() <= WorldSnapshotCodec.MAX_BYTES) { "Snapshot too large" }
        val output = ByteArrayOutputStream()
        file.inputStream().use { input ->
            val buffer = ByteArray(8_192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= WorldSnapshotCodec.MAX_BYTES) { "Snapshot grew beyond limit" }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun writeSynced(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output -> output.write(bytes); output.fd.sync() }
    }

    private fun atomicReplace(source: File, destination: File) {
        // If this filesystem cannot guarantee atomic replacement, fail and keep the old snapshot.
        Files.move(source.toPath(), destination.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
    }

    private fun archiveCorrupt(file: File) {
        var candidate = File(directory, "${file.name}.corrupt")
        var suffix = 1
        while (candidate.exists()) {
            check(suffix <= 1_000) { "Too many preserved corrupt snapshots" }
            candidate = File(directory, "${file.name}.corrupt.${suffix++}")
        }
        Files.copy(file.toPath(), candidate.toPath())
        FileOutputStream(candidate, true).use { it.fd.sync() }
    }

    private sealed class FileState {
        data object Missing : FileState()
        data class Valid(val snapshot: WorldSnapshot, val bytes: ByteArray) : FileState()
        data class Invalid(val reason: String) : FileState()
        data class Unsupported(val version: Int) : FileState()
    }
}
