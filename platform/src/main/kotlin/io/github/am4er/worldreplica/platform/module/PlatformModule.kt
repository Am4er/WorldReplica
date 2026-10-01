package io.github.am4er.worldreplica.platform.module

import io.github.am4er.worldreplica.api.module.Module
import io.github.am4er.worldreplica.platform.WorldReplicaPlatform
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class PlatformModule(
    protected val worldReplica: WorldReplicaPlatform,
    final override val name: String,
) : Module {
    protected val logger: Logger = LoggerFactory.getLogger("WorldReplica/$name")

    final override fun load() {
        logger.info("Loading module {}", name)
        onEnable()
    }

    final override fun reload() {
        onReload()
    }

    final override fun unload() {
        logger.info("Unloading module {}", name)
        try {
            onDisable()
        } catch (e: RuntimeException) {
            logger.error("Module {} did not unload cleanly", name, e)
        }
    }

    protected abstract fun onEnable()

    protected abstract fun onDisable()

    protected open fun onReload() {
    }
}
