package io.github.am4er.worldreplica.fabric.module.schematic.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.fabric.command.Subcommand
import io.github.am4er.worldreplica.fabric.command.fail
import io.github.am4er.worldreplica.fabric.command.reply
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class CancelCommand(private val platform: WorldReplicaFabricPlatform) : Subcommand {
    override val usage = "/wr cancel"
    override val description = "stop your running save or load"
    override val permission = "command/cancel"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("cancel").executes { execute(it.source) }

    private fun execute(source: CommandSourceStack): Int {
        if (!platform.jobs.cancel(source.playerOrException.uuid)) return source.fail("You have nothing running.")
        return source.reply("Cancelled.")
    }
}
