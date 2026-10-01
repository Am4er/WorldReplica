package io.github.am4er.worldreplica.fabric.command

import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component

fun CommandSourceStack.reply(message: String): Int {
    sendSuccess({ Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE) }, false)
    return 1
}

fun CommandSourceStack.fail(message: String): Int {
    sendFailure(Component.literal(message))
    return 0
}
