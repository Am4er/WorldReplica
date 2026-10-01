package io.github.am4er.worldreplica.fabric.module.schematic.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.fabric.command.Subcommand
import io.github.am4er.worldreplica.fabric.command.fail
import io.github.am4er.worldreplica.fabric.command.reply
import io.github.am4er.worldreplica.platform.schematic.SaveJob
import io.github.am4er.worldreplica.platform.utils.formatCount
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class SaveCommand(private val platform: WorldReplicaFabricPlatform) : Subcommand {
    override val usage = "/wr save [name]"
    override val description = "save your selection"
    override val permission = "command/save"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("save")
        .executes { execute(it.source, null) }
        .then(
            Commands.argument("name", StringArgumentType.word())
                .executes { execute(it.source, StringArgumentType.getString(it, "name")) }
        )

    private fun execute(source: CommandSourceStack, requestedName: String?): Int {
        val player = source.playerOrException
        if (platform.jobs.isRunning(player.uuid)) return source.fail("You already have a save or load running, use /wr cancel to stop it.")

        val selection = platform.selections[player.uuid]
        val pos1 = selection?.pos1
        val pos2 = selection?.pos2
        if (selection == null || pos1 == null || pos2 == null) {
            return source.fail("Set both positions with the wand first.")
        }
        val level = source.server.getLevel(selection.dimension)
            ?: return source.fail("That dimension isn't loaded.")

        val name = requestedName?.removeSuffix(".schem") ?: "${player.scoreboardName}-${LocalDateTime.now().format(TIMESTAMP)}"
        if (!platform.schematics.isValidName(name)) {
            return source.fail("Names can only use letters, numbers, _ and -, up to 64 characters.")
        }
        val file = platform.schematics.file(name)
        if (Files.exists(file)) {
            return source.fail("$name.schem already exists.")
        }

        val min = BlockPos.min(pos1, pos2)
        val max = BlockPos.max(pos1, pos2)
        if (max.x - min.x >= MAX_SIDE || max.y - min.y >= MAX_SIDE || max.z - min.z >= MAX_SIDE) {
            return source.fail("Each side can be at most ${formatCount(MAX_SIDE.toLong())} blocks.")
        }
        val volume = selection.volume()!!
        if (volume > MAX_VOLUME) {
            return source.fail("Too many blocks: ${formatCount(volume)} (max ${formatCount(MAX_VOLUME)}).")
        }

        val origin = if (player.level().dimension() == selection.dimension) player.blockPosition() else min
        val job = try {
            platform.schematics.ensureSpace(volume * 2 + volume / 4)
            SaveJob(platform, player, source, level, min, max, origin, name, file)
        } catch (e: IOException) {
            platform.logger.warn("Could not start saving {}", file, e)
            return source.fail(e.message ?: "Couldn't start the save.")
        }
        platform.jobs.start(job)
        return source.reply("Saving ${formatCount(volume)} blocks to $name.schem...")
    }

    private companion object {
        const val MAX_SIDE = 65_535
        const val MAX_VOLUME = Int.MAX_VALUE.toLong()
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
