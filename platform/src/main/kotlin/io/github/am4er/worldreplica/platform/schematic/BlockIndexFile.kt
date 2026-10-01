package io.github.am4er.worldreplica.platform.schematic

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class BlockIndexFile(private val path: Path, val size: Long) : AutoCloseable {
    private val arena = Arena.ofShared()
    private val segment: MemorySegment = try {
        FileChannel.open(path, StandardOpenOption.READ, StandardOpenOption.WRITE).use {
            it.map(FileChannel.MapMode.READ_WRITE, 0, size * 2, arena)
        }
    } catch (e: Throwable) {
        arena.close()
        Files.deleteIfExists(path)
        throw e
    }
    private var closed = false

    operator fun get(index: Long): Int = segment.get(ValueLayout.JAVA_SHORT, index * 2).toInt() and 0xFFFF

    operator fun set(index: Long, id: Int) = segment.set(ValueLayout.JAVA_SHORT, index * 2, id.toShort())

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        arena.close()
        Files.deleteIfExists(path)
    }
}
