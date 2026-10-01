package io.github.am4er.worldreplica.platform.selection

import io.github.am4er.worldreplica.api.selection.Selection
import io.github.am4er.worldreplica.api.selection.SelectionService
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.UUID

class PlatformSelections : SelectionService {
    private val selections = HashMap<UUID, Selection>()

    override operator fun get(player: UUID): Selection? = selections[player]

    override fun setCorner(player: UUID, corner: Int, dimension: ResourceKey<Level>, pos: BlockPos): Selection? {
        val old = selections[player]?.takeIf { it.dimension == dimension } ?: Selection(dimension)
        if ((if (corner == 1) old.pos1 else old.pos2) == pos) return null

        val updated = if (corner == 1) old.copy(pos1 = pos.immutable()) else old.copy(pos2 = pos.immutable())
        selections[player] = updated
        return updated
    }

    override fun remove(player: UUID) {
        selections.remove(player)
    }

    override fun clear() = selections.clear()
}
