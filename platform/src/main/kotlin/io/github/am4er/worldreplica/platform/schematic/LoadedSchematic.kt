package io.github.am4er.worldreplica.platform.schematic

import net.minecraft.core.BlockPos
import java.nio.file.Files
import java.nio.file.Path

class LoadedSchematic(
    val width: Int,
    val height: Int,
    val length: Int,
    val pasteOffset: BlockPos,
    val palette: List<String>,
    val blocks: BlockIndexFile,
    val blockEntities: Path,
    val blockEntityCount: Int,
) : AutoCloseable {
    override fun close() {
        blocks.close()
        Files.deleteIfExists(blockEntities)
    }
}
