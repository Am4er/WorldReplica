package io.github.am4er.worldreplica.api.selection

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.UUID

interface SelectionService {
    operator fun get(player: UUID): Selection?

    fun setCorner(player: UUID, corner: Int, dimension: ResourceKey<Level>, pos: BlockPos): Selection?

    fun remove(player: UUID)

    fun clear()
}
