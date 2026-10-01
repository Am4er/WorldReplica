package io.github.am4er.worldreplica.fabric.module.schematic.command

import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.fabric.command.Subcommand
import io.github.am4er.worldreplica.fabric.command.fail
import io.github.am4er.worldreplica.fabric.command.reply
import io.github.am4er.worldreplica.platform.schematic.LoadJob
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider

class LoadCommand(private val platform: WorldReplicaFabricPlatform) : Subcommand {
    override val usage = "/wr load <name>"
    override val description = "paste a schematic at your position"
    override val permission = "command/load"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> = Commands.literal("load").then(
        Commands.argument("name", StringArgumentType.string())
            .suggests { _, builder ->
                SharedSuggestionProvider.suggest(platform.schematics.names().map(StringArgumentType::escapeIfRequired), builder)
            }
            .executes { execute(it.source, StringArgumentType.getString(it, "name")) }
    )

    private fun execute(source: CommandSourceStack, name: String): Int {
        val player = source.playerOrException
        if (platform.jobs.isRunning(player.uuid)) return source.fail("You already have a save or load running, use /wr cancel to stop it.")
        val file = platform.schematics.find(name) ?: return source.fail("Couldn't find $name.schem.")
        val displayName = name.removeSuffix(".schem")

        platform.jobs.start(LoadJob(platform, player, source, player.level(), player.blockPosition(), displayName, file))
        return source.reply("Loading $displayName.schem...")
    }
}
