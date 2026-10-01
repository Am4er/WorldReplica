package io.github.am4er.worldreplica.platform.schematic

import io.github.am4er.worldreplica.api.event.SchematicSaveEvent
import io.github.am4er.worldreplica.platform.WorldReplicaPlatform
import io.github.am4er.worldreplica.platform.job.BoxWalker
import io.github.am4er.worldreplica.platform.job.PlatformJob
import io.github.am4er.worldreplica.platform.job.Progress
import io.github.am4er.worldreplica.platform.utils.formatBytes
import io.github.am4er.worldreplica.platform.utils.formatCount
import io.github.am4er.worldreplica.platform.utils.percent
import io.github.am4er.worldreplica.platform.utils.unwrap
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Util
import net.minecraft.world.level.block.state.BlockState
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

class SaveJob(
    private val platform: WorldReplicaPlatform,
    player: ServerPlayer,
    source: CommandSourceStack,
    private val level: ServerLevel,
    private val min: BlockPos,
    private val max: BlockPos,
    private val origin: BlockPos,
    private val name: String,
    private val file: Path,
) : PlatformJob(player.uuid, source) {
    private val storage = platform.schematics
    private val logger = platform.logger
    private val author = player.scoreboardName
    private val width = max.x - min.x + 1
    private val height = max.y - min.y + 1
    private val length = max.z - min.z + 1
    private val volume = width.toLong() * height * length

    private val walker = BoxWalker(min, max)
    private val blocks = BlockIndexFile(storage.tempFile(".blocks"), volume)
    private val blockEntityFile: Path
    private val blockEntityOut: DataOutputStream
    private var blockEntityCount = 0

    private val ids = Reference2IntOpenHashMap<BlockState>().apply { defaultReturnValue(-1) }
    private val states = ArrayList<BlockState>()
    private var counts = LongArray(256)
    private var lastState: BlockState? = null
    private var lastId = -1
    private var scanned = 0L
    private val writing = Progress()
    private val pos = BlockPos.MutableBlockPos()

    init {
        try {
            blockEntityFile = storage.tempFile(".nbt")
            blockEntityOut = DataOutputStream(BufferedOutputStream(Files.newOutputStream(blockEntityFile), IO_BUFFER))
        } catch (e: Throwable) {
            blocks.close()
            throw e
        }
    }

    override fun work(deadline: Long): Boolean {
        val task = background
        if (task == null) {
            if (!walker.walk(deadline, ::scanRow, ::scanBlockEntities)) return false
            return startWriting()
        }
        if (!task.isDone) return false

        when (val error = runCatching { task.join() }.exceptionOrNull()?.let(::unwrap)) {
            null -> saved()
            is FileAlreadyExistsException -> reportFailure("$name.schem already exists.")
            is CancellationException -> {}
            else -> {
                logger.error("Failed to save schematic {}", file, error)
                reportFailure("Couldn't save $name.schem: ${error.message}")
            }
        }
        return true
    }

    private fun saved() {
        val bytes = Files.size(file)
        reportSuccess(
            Component.literal("Saved ${formatCount(volume)} blocks to ").withStyle(ChatFormatting.LIGHT_PURPLE)
                .append(Component.literal("schematics/$name.schem").withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (${formatBytes(bytes)}, ${elapsed()})").withStyle(ChatFormatting.GRAY))
        )
        platform.events.publish(
            SchematicSaveEvent(owner, author, name, file, level.dimension(), min, max, volume, bytes, System.nanoTime() - startedAt)
        )
    }

    override fun status(): Component = Component.literal(
        if (background == null) "Saving $name: scanning ${percent(scanned, volume)} (${formatCount(scanned)} / ${formatCount(volume)} blocks)"
        else "Saving $name: writing file ${percent(writing.done, writing.total)}"
    ).withStyle(ChatFormatting.LIGHT_PURPLE)

    override fun release() {
        runCatching { blockEntityOut.close() }
        blocks.close()
        Files.deleteIfExists(blockEntityFile)
    }

    private fun scanRow(y: Int, z: Int, x0: Int, x1: Int) {
        val chunk = level.getChunk(x0 shr 4, z shr 4)
        var index = (x0 - min.x) + (z - min.z).toLong() * width + (y - min.y).toLong() * width * length
        for (x in x0..x1) {
            val state = chunk.getBlockState(pos.set(x, y, z))
            val id = if (state === lastState) lastId else idOf(state).also { lastState = state; lastId = it }
            blocks[index++] = id
            counts[id]++
        }
        scanned += x1 - x0 + 1
    }

    private fun idOf(state: BlockState): Int {
        val known = ids.getInt(state)
        if (known >= 0) return known
        check(states.size < 65_536) { "more than 65,536 different blocks in the selection" }
        val id = states.size
        ids.put(state, id)
        states.add(state)
        if (id == counts.size) counts = counts.copyOf(id * 2)
        return id
    }

    private fun scanBlockEntities(chunkX: Int, chunkZ: Int) {
        for ((p, blockEntity) in level.getChunk(chunkX, chunkZ).blockEntities) {
            if (p.x !in min.x..max.x || p.y !in min.y..max.y || p.z !in min.z..max.z) continue
            val id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity.type) ?: continue
            CompoundTag().apply {
                putIntArray("Pos", intArrayOf(p.x - min.x, p.y - min.y, p.z - min.z))
                putString("Id", id.toString())
                put("Data", blockEntity.saveWithoutMetadata(level.registryAccess()))
            }.write(blockEntityOut)
            blockEntityCount++
        }
    }

    private fun startWriting(): Boolean {
        blockEntityOut.close()
        logger.info("Scanned {} blocks for {} in {}, writing file", formatCount(volume), name, elapsed())
        val writer = SchematicWriter(storage, width, height, length, min, origin, name, author, states, counts)
        if (writer.dataLength > Int.MAX_VALUE) {
            reportFailure("Too much block data for one .schem file, try a smaller area.")
            return true
        }
        background = CompletableFuture.runAsync(
            { writer.write(file, blocks, blockEntityFile, blockEntityCount, { cancelled }, writing) },
            Util.ioPool(),
        )
        return false
    }
}
