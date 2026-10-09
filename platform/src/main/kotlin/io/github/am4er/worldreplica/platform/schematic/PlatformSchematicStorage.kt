package io.github.am4er.worldreplica.platform.schematic

import io.github.am4er.worldreplica.api.schematic.SchematicStorage
import io.github.am4er.worldreplica.platform.utils.formatBytes
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension

class PlatformSchematicStorage(
    override val directory: Path,
    private val extraDirectories: List<Path> = emptyList(),
) : SchematicStorage {
    private val tempDirectory = directory.resolve(".worldreplica-tmp")

    override fun isValidName(name: String) = SAVE_NAME.matches(name)

    override fun file(name: String): Path = directory.resolve("$name.schem")

    override fun find(name: String): Path? {
        val base = name.removeSuffix(".schem")
        if (base.isEmpty() || base.startsWith('.') || base.any { it == '/' || it == '\\' || it == ':' }) return null
        return searched().map { it.resolve("$base.schem") }.firstOrNull { Files.isRegularFile(it) }
    }

    override fun names(): List<String> = searched()
        .filter { it.isDirectory() }
        .flatMap { folder -> folder.listDirectoryEntries("*.schem").map { it.nameWithoutExtension } }
        .distinct()
        .sorted()

    private fun searched() = listOf(directory) + extraDirectories

    fun tempFile(suffix: String): Path {
        Files.createDirectories(tempDirectory)
        return Files.createTempFile(tempDirectory, "job-", suffix)
    }

    fun ensureSpace(bytes: Long) {
        Files.createDirectories(directory)
        val free = Files.getFileStore(directory).usableSpace
        if (free < bytes + DISK_HEADROOM) {
            throw IOException("Not enough disk space (need about ${formatBytes(bytes + DISK_HEADROOM)}, have ${formatBytes(free)}).")
        }
    }

    fun clearTemp() {
        if (!tempDirectory.isDirectory()) return
        for (file in tempDirectory.listDirectoryEntries()) {
            runCatching { file.deleteIfExists() }
        }
    }

    private companion object {
        val SAVE_NAME = Regex("[A-Za-z0-9_-]{1,64}")
        const val DISK_HEADROOM = 256L shl 20
    }
}
