package io.github.am4er.worldreplica.platform.job

import net.minecraft.core.BlockPos

class BoxWalker(private val from: BlockPos, private val to: BlockPos) {
    private val firstChunkX = from.x shr 4
    private val firstChunkZ = from.z shr 4
    private val chunksAlongX = (to.x shr 4) - firstChunkX + 1
    private val totalChunks = chunksAlongX.toLong() * ((to.z shr 4) - firstChunkZ + 1)
    private var chunk = 0L
    private var inColumn = false
    private var y = 0
    private var z = 0

    fun walk(
        deadline: Long,
        row: (y: Int, z: Int, x0: Int, x1: Int) -> Unit,
        columnDone: (chunkX: Int, chunkZ: Int) -> Unit = { _, _ -> },
    ): Boolean {
        var worked = false
        while (chunk < totalChunks) {
            val chunkX = firstChunkX + (chunk % chunksAlongX).toInt()
            val chunkZ = firstChunkZ + (chunk / chunksAlongX).toInt()
            val x0 = maxOf(from.x, chunkX shl 4)
            val x1 = minOf(to.x, (chunkX shl 4) + 15)
            val z0 = maxOf(from.z, chunkZ shl 4)
            val z1 = minOf(to.z, (chunkZ shl 4) + 15)
            if (!inColumn) {
                inColumn = true
                y = from.y
                z = z0
            }
            while (y <= to.y) {
                if (worked && System.nanoTime() >= deadline) return false
                worked = true
                row(y, z, x0, x1)
                if (++z > z1) {
                    z = z0
                    y++
                }
            }
            columnDone(chunkX, chunkZ)
            inColumn = false
            chunk++
        }
        return true
    }
}
