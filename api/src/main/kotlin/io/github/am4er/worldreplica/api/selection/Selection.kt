package io.github.am4er.worldreplica.api.selection

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import kotlin.math.abs

data class Selection(
    val dimension: ResourceKey<Level>,
    val pos1: BlockPos? = null,
    val pos2: BlockPos? = null,
) {
    fun volume(): Long? {
        val a = pos1 ?: return null
        val b = pos2 ?: return null
        return (abs(a.x - b.x) + 1L) * (abs(a.y - b.y) + 1L) * (abs(a.z - b.z) + 1L)
    }
}
