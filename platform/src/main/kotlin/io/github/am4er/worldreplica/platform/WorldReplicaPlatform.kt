package io.github.am4er.worldreplica.platform

import io.github.am4er.worldreplica.api.WorldReplica
import io.github.am4er.worldreplica.api.WorldReplicaProvider
import io.github.am4er.worldreplica.platform.event.PlatformEvents
import io.github.am4er.worldreplica.platform.job.PlatformJobs
import io.github.am4er.worldreplica.platform.module.PlatformModuleContainer
import io.github.am4er.worldreplica.platform.schematic.PlatformSchematicStorage
import io.github.am4er.worldreplica.platform.selection.PlatformSelections
import org.slf4j.Logger
import java.nio.file.Path

abstract class WorldReplicaPlatform(
    val logger: Logger,
    gameDirectory: Path,
    extraSchematicDirectories: List<Path> = emptyList(),
) : WorldReplica {
    final override val moduleContainer = PlatformModuleContainer()
    final override val selections = PlatformSelections()
    final override val schematics = PlatformSchematicStorage(gameDirectory.resolve("schematics"), extraSchematicDirectories)
    final override val jobs = PlatformJobs(logger)
    final override val events = PlatformEvents(logger)

    fun load() {
        WorldReplicaProvider.register(this)
    }

    fun start() {
        onStart()
    }

    fun stop() {
        jobs.cancelAll()
        moduleContainer.unload()
    }

    protected abstract fun onStart()
}
