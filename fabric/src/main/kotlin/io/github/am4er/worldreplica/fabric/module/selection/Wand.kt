package io.github.am4er.worldreplica.fabric.module.selection

import net.minecraft.ChatFormatting
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.component.ItemLore

object Wand {
    private val MARKER = CompoundTag().apply { putBoolean("worldreplica_wand", true) }

    fun create(): ItemStack = ItemStack(Items.GOLDEN_AXE).apply {
        set(DataComponents.ITEM_NAME, Component.literal("WorldReplica Wand").withStyle(ChatFormatting.GOLD))
        set(DataComponents.LORE, ItemLore(listOf(loreLine("Left click: position 1"), loreLine("Right click: position 2"))))
        set(DataComponents.CUSTOM_DATA, CustomData.of(MARKER.copy()))
        set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
    }

    fun isWand(stack: ItemStack): Boolean =
        stack.item === Items.GOLDEN_AXE &&
            stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).matchedBy(MARKER)

    fun giveTo(player: ServerPlayer) {
        val stack = create()
        player.inventory.add(stack)
        if (!stack.isEmpty) {
            player.createItemStackToDrop(stack, false, false)?.let {
                it.setNoPickUpDelay()
                it.setTarget(player.uuid)
                player.level().addFreshEntity(it)
            }
        }
        player.containerMenu.broadcastChanges()
    }

    private fun loreLine(text: String): Component =
        Component.literal(text).withStyle(ChatFormatting.GRAY).withStyle { it.withItalic(false) }
}
