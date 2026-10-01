package io.github.am4er.worldreplica.platform.job

import io.github.am4er.worldreplica.api.job.Job
import io.github.am4er.worldreplica.platform.utils.formatDuration
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean

abstract class PlatformJob(
    final override val owner: UUID,
    protected val source: CommandSourceStack,
) : Job {
    @Volatile
    final override var cancelled = false
        private set

    protected var background: CompletableFuture<*>? = null
    protected val startedAt = System.nanoTime()
    private val released = AtomicBoolean()

    abstract fun work(deadline: Long): Boolean

    protected abstract fun release()

    fun cancel() {
        cancelled = true
        val task = background
        if (task == null || task.isDone) releaseOnce() else task.whenComplete { _, _ -> releaseOnce() }
    }

    fun releaseOnce() {
        if (released.compareAndSet(false, true)) release()
    }

    fun reportFailure(message: String) = source.sendFailure(Component.literal(message))

    protected fun reportSuccess(message: Component) = source.sendSuccess({ message }, true)

    protected fun elapsed(): String = formatDuration(System.nanoTime() - startedAt)
}
