package io.github.am4er.worldreplica.fabric

import net.fabricmc.api.EnvType
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.loader.api.FabricLoader
import org.slf4j.LoggerFactory

class WorldReplicaFabricMod : ModInitializer {
    override fun onInitialize() {
        val loader = FabricLoader.getInstance()
        val version = loader.getModContainer("worldreplica").map { it.metadata.version.friendlyString }.orElse("dev")
        val platform = WorldReplicaFabricPlatform(
            LoggerFactory.getLogger("WorldReplica"),
            loader.gameDir,
            loader.configDir.resolve("worldreplica"),
            version,
            listOf(
                loader.configDir.resolve("worldedit").resolve("schematics"),
                loader.gameDir.resolve("plugins").resolve("FastAsyncWorldEdit").resolve("schematics"),
            ),
        )
        platform.load()
        platform.start()

        if (loader.environmentType == EnvType.SERVER) {
            ServerLifecycleEvents.SERVER_STOPPED.register { platform.stop() }
        }
    }
}
