package io.github.am4er.worldreplica.fabric.module.selection.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import io.github.am4er.worldreplica.fabric.command.Subcommand
import io.github.am4er.worldreplica.fabric.command.reply
import io.github.am4er.worldreplica.fabric.module.selection.Wand
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

class SelectCommand : Subcommand {
    override val usage = "/wr select"
    override val description = "get the selection wand"
    override val permission = "command/select"

    override fun build(): LiteralArgumentBuilder<CommandSourceStack> =
        Commands.literal("select").executes { execute(it.source) }

    private fun execute(source: CommandSourceStack): Int {
        Wand.giveTo(source.playerOrException)
        return source.reply("Gave you the selection wand.")
    }
}
