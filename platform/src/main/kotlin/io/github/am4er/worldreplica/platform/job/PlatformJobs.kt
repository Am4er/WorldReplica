package io.github.am4er.worldreplica.platform.job

import io.github.am4er.worldreplica.api.job.JobService
import net.minecraft.server.MinecraftServer
import org.slf4j.Logger
import java.util.UUID

class PlatformJobs(private val logger: Logger) : JobService {
    private val running = LinkedHashMap<UUID, PlatformJob>()
    private var ticks = 0

    override fun isRunning(player: UUID) = player in running

    fun start(job: PlatformJob) {
        check(job.owner !in running) { "player already has a job running" }
        running[job.owner] = job
    }

    override fun cancel(player: UUID): Boolean {
        val job = running.remove(player) ?: return false
        job.cancel()
        return true
    }

    override fun cancelAll() {
        running.values.forEach(PlatformJob::cancel)
        running.clear()
    }

    fun tick(server: MinecraftServer) {
        if (running.isEmpty()) return
        val showStatus = ++ticks % 20 == 0
        val slice = TICK_BUDGET_NANOS / running.size
        for (job in running.values.toList()) {
            val finished = try {
                job.work(System.nanoTime() + slice)
            } catch (e: Exception) {
                logger.error("WorldReplica job failed", e)
                job.reportFailure("Job failed: ${e.message ?: e.javaClass.simpleName}")
                running.remove(job.owner)
                job.cancel()
                continue
            }
            if (finished) {
                running.remove(job.owner)
                job.releaseOnce()
            } else if (showStatus) {
                server.playerList.getPlayer(job.owner)?.sendSystemMessage(job.status(), true)
            }
        }
    }

    private companion object {
        const val TICK_BUDGET_NANOS = 20_000_000L
    }
}
