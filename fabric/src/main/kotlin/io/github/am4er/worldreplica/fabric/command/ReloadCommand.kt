package io.github.am4er.worldreplica.fabric.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class ReloadCommand(private val platform: WorldReplicaFabricPlatform) : Subcommand {
    override val usage = "/wr reload"
    override val description = "reload the config"
    override val permission = "command/reload"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("reload").executes { execute(it.source) }

    private fun execute(source: CommandSourceStack): Int {
        platform.moduleContainer.reload()
        return source.reply("Reloaded.")
    }
}
