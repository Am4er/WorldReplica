package io.github.am4er.worldreplica.platform.schematic

import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.github.am4er.worldreplica.platform.WorldReplicaPlatform
import io.github.am4er.worldreplica.platform.job.BoxWalker
import io.github.am4er.worldreplica.platform.job.PlatformJob
import io.github.am4er.worldreplica.platform.job.Progress
import io.github.am4er.worldreplica.platform.utils.formatCount
import io.github.am4er.worldreplica.platform.utils.percent
import io.github.am4er.worldreplica.platform.utils.unwrap
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.arguments.blocks.BlockStateParser
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtAccounter
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.ProblemReporter
import net.minecraft.util.Util
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.TagValueInput
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture

class LoadJob(
    platform: WorldReplicaPlatform,
    player: ServerPlayer,
    source: CommandSourceStack,
    private val level: ServerLevel,
    private val anchor: BlockPos,
    private val name: String,
    file: Path,
) : PlatformJob(player.uuid, source) {
    private enum class Phase { READ, PLACE, BLOCK_ENTITIES }

    private val logger = platform.logger

    private val reading = Progress()
    private val readTask: CompletableFuture<LoadedSchematic> =
        CompletableFuture.supplyAsync({ SchematicReader(platform.schematics).read(file, { cancelled }, reading) }, Util.ioPool())

    private var phase = Phase.READ
    private var schematic: LoadedSchematic? = null
    private var palette = emptyArray<BlockState>()
    private var unknownBlocks = 0

    private var origin = BlockPos.ZERO
    private var walker: BoxWalker? = null
    private var clippedLayers = 0
    private var toPlace = 0L
    private var placed = 0L
    private var changed = 0L
    private val pos = BlockPos.MutableBlockPos()

    private var blockEntityInput: DataInputStream? = null
    private val accounter = NbtAccounter.unlimitedHeap()
    private var blockEntitiesRead = 0
    private var blockEntitiesApplied = 0

    init {
        background = readTask
    }

    override fun work(deadline: Long): Boolean = when (phase) {
        Phase.READ -> readTask.isDone && startPlacing()
        Phase.PLACE -> {
            if (walker!!.walk(deadline, ::placeRow)) {
                logger.info("Placed {} blocks of {} in {}", formatCount(placed), name, elapsed())
                blockEntityInput = DataInputStream(BufferedInputStream(Files.newInputStream(schematic!!.blockEntities), IO_BUFFER))
                phase = Phase.BLOCK_ENTITIES
            }
            false
        }
        Phase.BLOCK_ENTITIES -> applyBlockEntities(deadline)
    }

    override fun status(): Component = Component.literal(
        when (phase) {
            Phase.READ -> "Loading $name: reading file ${percent(reading.done, reading.total)}"
            Phase.PLACE -> "Loading $name: placing ${percent(placed, toPlace)} (${formatCount(placed)} / ${formatCount(toPlace)} blocks)"
            Phase.BLOCK_ENTITIES -> "Loading $name: block entities $blockEntitiesRead / ${schematic!!.blockEntityCount}"
        }
    ).withStyle(ChatFormatting.LIGHT_PURPLE)

    override fun release() {
        runCatching { blockEntityInput?.close() }
        (schematic ?: readTask.takeIf { it.isDone && !it.isCompletedExceptionally }?.join())?.close()
    }

    private fun startPlacing(): Boolean {
        val loaded = try {
            readTask.join()
        } catch (e: Exception) {
            val cause = unwrap(e)
            if (cause !is CancellationException) {
                logger.error("Failed to read schematic {}", name, cause)
                reportFailure("Couldn't read $name.schem: ${cause.message}")
            }
            return true
        }
        schematic = loaded
        logger.info("Read {} ({} blocks) in {}, placing", name, formatCount(loaded.width.toLong() * loaded.height * loaded.length), elapsed())
        palette = Array(loaded.palette.size) { i ->
            try {
                BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, loaded.palette[i], false).blockState()
            } catch (e: CommandSyntaxException) {
                unknownBlocks++
                Blocks.AIR.defaultBlockState()
            }
        }

        origin = anchor.offset(loaded.pasteOffset)
        val bottom = maxOf(origin.y, level.minY)
        val top = minOf(origin.y + loaded.height - 1, level.maxY)
        if (bottom > top) {
            reportFailure("That would paste outside the world height (y ${origin.y} to ${origin.y + loaded.height - 1}).")
            return true
        }
        clippedLayers = loaded.height - (top - bottom + 1)
        toPlace = loaded.width.toLong() * loaded.length * (top - bottom + 1)
        walker = BoxWalker(BlockPos(origin.x, bottom, origin.z), BlockPos(origin.x + loaded.width - 1, top, origin.z + loaded.length - 1))
        phase = Phase.PLACE
        return false
    }

    private fun placeRow(y: Int, z: Int, x0: Int, x1: Int) {
        val schematic = schematic!!
        val chunk = level.getChunk(x0 shr 4, z shr 4)
        var index = (x0 - origin.x) + (z - origin.z).toLong() * schematic.width + (y - origin.y).toLong() * schematic.width * schematic.length
        for (x in x0..x1) {
            val id = schematic.blocks[index++]
            val state = if (id < palette.size) palette[id] else Blocks.AIR.defaultBlockState()
            if (chunk.getBlockState(pos.set(x, y, z)) !== state) {
                level.setBlock(pos.immutable(), state, PLACE_FLAGS)
                changed++
            }
        }
        placed += x1 - x0 + 1
    }

    private fun applyBlockEntities(deadline: Long): Boolean {
        val schematic = schematic!!
        val input = blockEntityInput!!
        var worked = false
        while (blockEntitiesRead < schematic.blockEntityCount) {
            if (worked && System.nanoTime() >= deadline) return false
            worked = true
            val tag = CompoundTag.TYPE.load(input, accounter)
            blockEntitiesRead++
            val rel = tag.getIntArray("Pos").orElse(null) ?: continue
            if (rel[0] !in 0 until schematic.width || rel[1] !in 0 until schematic.height || rel[2] !in 0 until schematic.length) continue
            val target = origin.offset(rel[0], rel[1], rel[2])
            if (level.isOutsideBuildHeight(target)) continue
            val blockEntity = level.getBlockEntity(target) ?: continue
            ProblemReporter.ScopedCollector(logger).use { reporter ->
                val data = tag.getCompoundOrEmpty("Data")
                blockEntity.loadWithComponents(TagValueInput.create(reporter.forChild(blockEntity.problemPath()), level.registryAccess(), data))
            }
            blockEntity.setChanged()
            level.chunkSource.blockChanged(target)
            blockEntitiesApplied++
        }
        reportDone()
        return true
    }

    private fun reportDone() {
        val schematic = schematic!!
        val end = origin.offset(schematic.width - 1, schematic.height - 1, schematic.length - 1)
        val message = Component.literal("Loaded $name.schem: ").withStyle(ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal("${formatCount(placed)} blocks").withStyle(ChatFormatting.WHITE))
            .append(Component.literal(" from ${origin.toShortString()} to ${end.toShortString()}").withStyle(ChatFormatting.LIGHT_PURPLE))
            .append(Component.literal(" (${formatCount(changed)} changed, $blockEntitiesApplied block entities, ${elapsed()})").withStyle(ChatFormatting.GRAY))
        if (unknownBlocks > 0) {
            message.append(Component.literal("\n$unknownBlocks unknown block types were replaced with air.").withStyle(ChatFormatting.YELLOW))
        }
        if (clippedLayers > 0) {
            message.append(Component.literal("\nSkipped $clippedLayers layers outside the world height.").withStyle(ChatFormatting.YELLOW))
        }
        reportSuccess(message)
    }

    private companion object {
        const val PLACE_FLAGS = Block.UPDATE_CLIENTS or Block.UPDATE_SKIP_ALL_SIDEEFFECTS
    }
}
