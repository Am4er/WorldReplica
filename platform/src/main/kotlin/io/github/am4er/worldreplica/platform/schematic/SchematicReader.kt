package io.github.am4er.worldreplica.platform.schematic

import io.github.am4er.worldreplica.platform.job.Progress
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.TagTypes
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.zip.GZIPInputStream

class SchematicReader(private val storage: PlatformSchematicStorage) {
    fun read(file: Path, cancelled: () -> Boolean, progress: Progress): LoadedSchematic =
        Parser(storage, cancelled, progress).use { it.parse(file) }
}

private class Parser(
    private val storage: PlatformSchematicStorage,
    private val cancelled: () -> Boolean,
    private val progress: Progress,
) : AutoCloseable {
    private val accounter = NbtAccounter.unlimitedHeap()
    private var version = 0
    private var width = -1
    private var height = -1
    private var length = -1
    private var offset = intArrayOf(0, 0, 0)
    private var weOffset: IntArray? = null
    private var palette: CompoundTag? = null
    private var blocks: BlockIndexFile? = null
    private var spill: Path? = null
    private var spillLength = 0L
    private val blockEntityFile = storage.tempFile(".nbt")
    private val blockEntityOut = DataOutputStream(BufferedOutputStream(Files.newOutputStream(blockEntityFile), IO_BUFFER))
    private var blockEntityCount = 0
    private var handedOver = false

    fun parse(file: Path): LoadedSchematic {
        DataInputStream(BufferedInputStream(GZIPInputStream(Files.newInputStream(file), IO_BUFFER), IO_BUFFER)).use { input ->
            if (input.readByte().toInt() != TAG_COMPOUND) throw IOException("not a schematic file")
            input.readUTF()
            fields(input) { type, name ->
                if (name == "Schematic" && type == TAG_COMPOUND) {
                    fields(input) { t, n -> schematicField(input, t, n) }
                    true
                } else {
                    schematicField(input, type, name)
                }
            }
        }
        if (width <= 0 || height <= 0 || length <= 0) throw IOException("missing size")
        val paletteTag = palette ?: throw IOException("missing block palette")
        spill?.let { data -> BufferedInputStream(Files.newInputStream(data), IO_BUFFER).use { decode(it, spillLength) } }
        val blocks = blocks ?: throw IOException("missing block data")
        blockEntityOut.close()

        val ids = paletteTag.keySet().associateWith { paletteTag.getIntOr(it, -1) }
        val paletteSize = (ids.values.maxOrNull() ?: -1) + 1
        if (paletteSize > 65_536) throw IOException("palette is too big")
        val names = MutableList(paletteSize) { "minecraft:air" }
        ids.forEach { (name, id) -> if (id >= 0) names[id] = name }

        val pasteOffset = if (version >= 3) BlockPos(offset[0], offset[1], offset[2])
        else weOffset?.let { BlockPos(it[0], it[1], it[2]) } ?: BlockPos.ZERO

        handedOver = true
        return LoadedSchematic(width, height, length, pasteOffset, names, blocks, blockEntityFile, blockEntityCount)
    }

    override fun close() {
        runCatching { blockEntityOut.close() }
        spill?.let { runCatching { Files.deleteIfExists(it) } }
        if (!handedOver) {
            blocks?.close()
            runCatching { Files.deleteIfExists(blockEntityFile) }
        }
    }

    private fun fields(input: DataInputStream, handle: (type: Int, name: String) -> Boolean) {
        while (true) {
            val type = input.readByte().toInt()
            if (type == TAG_END) return
            val name = input.readUTF()
            if (!handle(type, name)) TagTypes.getType(type).skip(input, accounter)
        }
    }

    private fun schematicField(input: DataInputStream, type: Int, name: String): Boolean = when {
        name == "Version" && type == TAG_INT -> { version = input.readInt(); true }
        name == "Width" && type == TAG_SHORT -> { width = input.readUnsignedShort(); true }
        name == "Height" && type == TAG_SHORT -> { height = input.readUnsignedShort(); true }
        name == "Length" && type == TAG_SHORT -> { length = input.readUnsignedShort(); true }
        name == "Offset" && type == TAG_INT_ARRAY -> {
            IntArray(input.readInt()) { input.readInt() }.takeIf { it.size == 3 }?.let { offset = it }
            true
        }
        name == "Metadata" && type == TAG_COMPOUND -> {
            val metadata = CompoundTag.TYPE.load(input, accounter)
            if (metadata.contains("WEOffsetX")) {
                weOffset = intArrayOf(metadata.getIntOr("WEOffsetX", 0), metadata.getIntOr("WEOffsetY", 0), metadata.getIntOr("WEOffsetZ", 0))
            }
            true
        }
        name == "Blocks" && type == TAG_COMPOUND -> { fields(input) { t, n -> blockField(input, t, n) }; true }
        else -> blockField(input, type, name)
    }

    private fun blockField(input: DataInputStream, type: Int, name: String): Boolean = when {
        name == "Palette" && type == TAG_COMPOUND -> { palette = CompoundTag.TYPE.load(input, accounter); true }
        (name == "Data" || name == "BlockData") && type == TAG_BYTE_ARRAY -> { blockData(input); true }
        (name == "BlockEntities" || name == "TileEntities") && type == TAG_LIST -> { blockEntities(input); true }
        else -> false
    }

    private fun blockData(input: DataInputStream) {
        if (blocks != null || spill != null) throw IOException("duplicate block data")
        val byteCount = input.readInt().toLong()
        if (byteCount < 0) throw IOException("invalid block data length")
        if (width > 0 && height > 0 && length > 0) {
            decode(input, byteCount)
        } else {
            val data = storage.tempFile(".data")
            spill = data
            spillLength = byteCount
            Files.newOutputStream(data).use { copy(input, it, byteCount) }
        }
    }

    private fun decode(input: InputStream, byteCount: Long) {
        val volume = width.toLong() * height * length
        storage.ensureSpace(volume * 2)
        val blocks = BlockIndexFile(storage.tempFile(".blocks"), volume).also { blocks = it }
        progress.total = volume
        val buffer = ByteArray(IO_BUFFER)
        var remaining = byteCount
        var index = 0L
        var value = 0
        var shift = 0
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("block data is cut short")
            remaining -= read
            for (i in 0 until read) {
                val b = buffer[i].toInt()
                value = value or ((b and 0x7F) shl shift)
                if (b and 0x80 != 0) {
                    shift += 7
                    if (shift > 21) throw IOException("invalid block data")
                    continue
                }
                if (index >= volume) throw IOException("block data is longer than the schematic")
                if (value > 0xFFFF) throw IOException("block id out of range")
                blocks[index++] = value
                value = 0
                shift = 0
            }
            progress.done = index
            if (cancelled()) throw CancellationException("cancelled")
        }
        if (index != volume) throw IOException("block data is shorter than the schematic")
    }

    private fun blockEntities(input: DataInputStream) {
        val elementType = input.readByte().toInt()
        val count = input.readInt()
        if (elementType != TAG_COMPOUND) {
            val type = TagTypes.getType(elementType)
            repeat(count) { type.skip(input, accounter) }
            return
        }
        repeat(count) {
            val tag = CompoundTag.TYPE.load(input, accounter)
            val pos = tag.getIntArray("Pos").orElse(null)
            if (pos != null && pos.size == 3) {
                val data = tag.getCompound("Data").orElseGet { tag.copy().apply { remove("Pos"); remove("Id") } }
                CompoundTag().apply {
                    putIntArray("Pos", pos)
                    put("Data", data)
                }.write(blockEntityOut)
                blockEntityCount++
            }
        }
    }

    private fun copy(input: InputStream, output: OutputStream, byteCount: Long) {
        val buffer = ByteArray(IO_BUFFER)
        var remaining = byteCount
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("block data is cut short")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }
}
