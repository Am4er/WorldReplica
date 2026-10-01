package io.github.am4er.worldreplica.fabric.command

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.permissions.PermissionLevel

class WorldReplicaCommand(private val version: String) {
    private val subcommands = mutableListOf<Subcommand>()

    fun add(subcommand: Subcommand) {
        subcommands += subcommand
    }

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val root = Commands.literal("wr")
            .requires { source -> subcommands.any { allowed(source, it) } }
            .executes { help(it.source) }
        for (subcommand in subcommands) {
            root.then(subcommand.build().requires { allowed(it, subcommand) })
        }
        dispatcher.register(root)
    }

    private fun allowed(source: CommandSourceStack, subcommand: Subcommand): Boolean =
        source.checkPermission(Identifier.fromNamespaceAndPath(NAMESPACE, subcommand.permission), PermissionLevel.GAMEMASTERS)

    private fun help(source: CommandSourceStack): Int {
        source.sendSuccess({
            val message = Component.literal("WorldReplica $version").withStyle(ChatFormatting.GOLD)
            for (subcommand in subcommands.filter { allowed(source, it) }) {
                message.append(Component.literal("\n${subcommand.usage}").withStyle(ChatFormatting.YELLOW))
                message.append(Component.literal(" - ${subcommand.description}").withStyle(ChatFormatting.GRAY))
            }
            message
        }, false)
        return 1
    }

    private companion object {
        const val NAMESPACE = "worldreplica"
    }
}
