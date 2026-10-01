package io.github.am4er.worldreplica.fabric.command

import com.mojang.brigadier.builder.LiteralArgumentBuilder
import net.minecraft.commands.CommandSourceStack

interface Subcommand {
    val usage: String
    val description: String
    val permission: String

    fun build(): LiteralArgumentBuilder<CommandSourceStack>
}
