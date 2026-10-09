package io.github.am4er.worldreplica.fabric

import io.github.am4er.worldreplica.fabric.command.ReloadCommand
import io.github.am4er.worldreplica.fabric.command.WorldReplicaCommand
import io.github.am4er.worldreplica.fabric.module.discord.DiscordModule
import io.github.am4er.worldreplica.fabric.module.schematic.SchematicModule
import io.github.am4er.worldreplica.fabric.module.selection.SelectionModule
import io.github.am4er.worldreplica.platform.WorldReplicaPlatform
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import org.slf4j.Logger
import java.nio.file.Path

class WorldReplicaFabricPlatform(
    logger: Logger,
    gameDirectory: Path,
    val configDirectory: Path,
    version: String,
    extraSchematicDirectories: List<Path> = emptyList(),
) : WorldReplicaPlatform(logger, gameDirectory, extraSchematicDirectories) {
    val command = WorldReplicaCommand(version)

    override fun onStart() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> command.register(dispatcher) }

        moduleContainer.registerModule(SelectionModule(this))
        moduleContainer.registerModule(SchematicModule(this))
        moduleContainer.registerModule(DiscordModule(this))
        command.add(ReloadCommand(this))
    }
}
