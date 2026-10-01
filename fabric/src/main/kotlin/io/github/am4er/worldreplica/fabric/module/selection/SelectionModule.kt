package io.github.am4er.worldreplica.fabric.module.selection

import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.fabric.module.selection.command.SelectCommand
import io.github.am4er.worldreplica.platform.module.PlatformModule
import io.github.am4er.worldreplica.platform.utils.formatCount
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

class SelectionModule(private val platform: WorldReplicaFabricPlatform) : PlatformModule(platform, "selection") {
    override fun onEnable() {
        AttackBlockCallback.EVENT.register { player, level, hand, pos, _ -> click(player, level, hand, pos, 1) }
        UseBlockCallback.EVENT.register { player, level, hand, hit -> click(player, level, hand, hit.blockPos, 2) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> platform.selections.remove(handler.player.uuid) }
        ServerLifecycleEvents.SERVER_STOPPED.register { platform.selections.clear() }

        platform.command.add(SelectCommand())
    }

    override fun onDisable() {
        platform.selections.clear()
    }

    private fun click(player: Player, level: Level, hand: InteractionHand, pos: BlockPos, corner: Int): InteractionResult {
        if (!Wand.isWand(player.getItemInHand(hand))) return InteractionResult.PASS

        if (player is ServerPlayer) {
            platform.selections.setCorner(player.uuid, corner, level.dimension(), pos)?.let { selection ->
                val message = Component.literal(if (corner == 1) "First position set to " else "Second position set to ")
                    .withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal("${pos.x}, ${pos.y}, ${pos.z}").withStyle(ChatFormatting.WHITE))
                selection.volume()?.let {
                    message.append(Component.literal(" (${formatCount(it)} blocks)").withStyle(ChatFormatting.GRAY))
                }
                player.sendSystemMessage(message)
            }
        }
        return InteractionResult.SUCCESS
    }
}
