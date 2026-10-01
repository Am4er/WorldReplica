package io.github.am4er.worldreplica.platform.schematic

import io.github.am4er.worldreplica.platform.job.Progress
import net.minecraft.SharedConstants
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.state.BlockState
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.zip.GZIPOutputStream

class SchematicWriter(
    private val storage: PlatformSchematicStorage,
    private val width: Int,
    private val height: Int,
    private val length: Int,
    private val min: BlockPos,
    private val origin: BlockPos,
    private val name: String,
    private val author: String,
    private val states: List<BlockState>,
    counts: LongArray,
) {
    private val remap = IntArray(states.size)

    val dataLength: Long

    init {
        var total = 0L
        states.indices.sortedByDescending { counts[it] }.forEachIndexed { newId, oldId ->
            remap[oldId] = newId
            total += counts[oldId] * varIntSize(newId)
        }
        dataLength = total
    }

    fun write(file: Path, blocks: BlockIndexFile, blockEntities: Path, blockEntityCount: Int, cancelled: () -> Boolean, progress: Progress) {
        progress.total = blocks.size
        val temp = storage.tempFile(".schem")
        try {
            DataOutputStream(BufferedOutputStream(GZIPOutputStream(Files.newOutputStream(temp), IO_BUFFER), IO_BUFFER)).use { out ->
                out.writeByte(TAG_COMPOUND)
                out.writeUTF("")
                out.writeByte(TAG_COMPOUND)
                out.writeUTF("Schematic")
                writeEntries(out, header())

                out.writeByte(TAG_COMPOUND)
                out.writeUTF("Blocks")
                writeEntries(out, CompoundTag().apply { put("Palette", palette()) })
                out.writeByte(TAG_BYTE_ARRAY)
                out.writeUTF("Data")
                out.writeInt(dataLength.toInt())
                writeData(out, blocks, cancelled, progress)
                out.writeByte(TAG_LIST)
                out.writeUTF("BlockEntities")
                out.writeByte(TAG_COMPOUND)
                out.writeInt(blockEntityCount)
                Files.copy(blockEntities, out)
                out.writeByte(TAG_END)
                out.writeByte(TAG_END)
                out.writeByte(TAG_END)
            }
            Files.move(temp, file)
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private fun header() = CompoundTag().apply {
        putInt("Version", 3)
        putInt("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version())
        put("Metadata", CompoundTag().apply {
            putString("Name", name)
            putString("Author", author)
            putLong("Date", System.currentTimeMillis())
            put("WorldEdit", CompoundTag().apply { putIntArray("Origin", intArrayOf(origin.x, origin.y, origin.z)) })
        })
        putShort("Width", width.toShort())
        putShort("Height", height.toShort())
        putShort("Length", length.toShort())
        putIntArray("Offset", intArrayOf(min.x - origin.x, min.y - origin.y, min.z - origin.z))
    }

    private fun palette() = CompoundTag().apply {
        states.forEachIndexed { id, state -> putInt(BlockStateParser.serialize(state), remap[id]) }
    }

    private fun writeData(out: DataOutputStream, blocks: BlockIndexFile, cancelled: () -> Boolean, progress: Progress) {
        val buffer = ByteArray(IO_BUFFER)
        var used = 0
        var written = 0L
        for (i in 0L until blocks.size) {
            if (used > IO_BUFFER - 5) {
                out.write(buffer, 0, used)
                written += used
                used = 0
                progress.done = i
                if (cancelled()) throw CancellationException("cancelled")
            }
            var value = remap[blocks[i]]
            while (value and 0x7F.inv() != 0) {
                buffer[used++] = ((value and 0x7F) or 0x80).toByte()
                value = value ushr 7
            }
            buffer[used++] = value.toByte()
        }
        out.write(buffer, 0, used)
        written += used
        progress.done = blocks.size
        check(written == dataLength) { "wrote $written bytes of block data, expected $dataLength" }
    }

    private fun writeEntries(out: DataOutputStream, tag: CompoundTag) {
        for (key in tag.keySet()) {
            val value = tag.get(key)!!
            out.writeByte(value.id.toInt())
            out.writeUTF(key)
            value.write(out)
        }
    }

    private fun varIntSize(value: Int): Int = when {
        value < 1 shl 7 -> 1
        value < 1 shl 14 -> 2
        else -> 3
    }
}
