package io.github.am4er.worldreplica.fabric.module.schematic

import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.fabric.module.schematic.command.CancelCommand
import io.github.am4er.worldreplica.fabric.module.schematic.command.LoadCommand
import io.github.am4er.worldreplica.fabric.module.schematic.command.SaveCommand
import io.github.am4er.worldreplica.platform.module.PlatformModule
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents

class SchematicModule(private val platform: WorldReplicaFabricPlatform) : PlatformModule(platform, "schematic") {
    override fun onEnable() {
        ServerTickEvents.END_SERVER_TICK.register { platform.jobs.tick(it) }
        ServerLifecycleEvents.SERVER_STARTED.register { platform.schematics.clearTemp() }
        ServerLifecycleEvents.SERVER_STOPPING.register { platform.jobs.cancelAll() }

        platform.command.add(SaveCommand(platform))
        platform.command.add(LoadCommand(platform))
        platform.command.add(CancelCommand(platform))
    }

    override fun onDisable() {
        platform.jobs.cancelAll()
    }
}
