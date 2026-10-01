package io.github.am4er.worldreplica.api.event

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.nio.file.Path
import java.util.UUID

data class SchematicSaveEvent(
    val player: UUID,
    val playerName: String,
    val name: String,
    val file: Path,
    val dimension: ResourceKey<Level>,
    val min: BlockPos,
    val max: BlockPos,
    val blocks: Long,
    val bytes: Long,
    val durationNanos: Long,
)
