package io.github.am4er.worldreplica.fabric.module.discord

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.am4er.worldreplica.api.event.SchematicSaveEvent
import io.github.am4er.worldreplica.fabric.WorldReplicaFabricPlatform
import io.github.am4er.worldreplica.platform.module.PlatformModule
import io.github.am4er.worldreplica.platform.utils.formatBytes
import io.github.am4er.worldreplica.platform.utils.formatCount
import io.github.am4er.worldreplica.platform.utils.formatDuration
import java.net.URI
import java.time.Instant

class DiscordModule(private val platform: WorldReplicaFabricPlatform) : PlatformModule(platform, "discord") {
    private val configFile = platform.configDirectory.resolve("discord.json")
    private val webhook = DiscordWebhook(logger)

    @Volatile
    private var target: URI? = null

    override fun onEnable() {
        readConfig()
        platform.events.subscribe(SchematicSaveEvent::class.java) { event ->
            target?.let { webhook.post(it, savedMessage(event)) }
        }
    }

    override fun onReload() {
        readConfig()
    }

    override fun onDisable() {
        target = null
    }

    private fun readConfig() {
        target = try {
            val config = DiscordConfig.load(configFile)
            if (config.enabled) webhookUri(config.webhookUrl) else null
        } catch (e: Exception) {
            logger.error("Couldn't read {}", configFile, e)
            null
        }
        logger.info(if (target != null) "Posting saves to Discord" else "Discord posts are off")
    }

    private fun webhookUri(url: String): URI? {
        val uri = runCatching { URI(url) }.getOrNull()
        if (uri == null || uri.host == null || (uri.scheme != "https" && uri.scheme != "http")) {
            logger.warn("discord.json is enabled but webhookUrl isn't a valid URL")
            return null
        }
        return uri
    }

    private fun savedMessage(event: SchematicSaveEvent): JsonObject {
        val embed = JsonObject().apply {
            addProperty("title", "Schematic saved")
            addProperty("description", "**${escape(event.playerName)}** saved `${event.name}.schem`")
            addProperty("color", 0xFF55FF)
            add("fields", JsonArray().apply {
                add(field("Blocks", formatCount(event.blocks), inline = true))
                add(field("Size", formatBytes(event.bytes), inline = true))
                add(field("Took", formatDuration(event.durationNanos), inline = true))
                add(field("Area", "`${event.min.toShortString()}` to `${event.max.toShortString()}` in ${event.dimension.identifier()}", inline = false))
            })
            addProperty("timestamp", Instant.now().toString())
        }
        return JsonObject().apply {
            add("embeds", JsonArray().apply { add(embed) })
            add("allowed_mentions", JsonObject().apply { add("parse", JsonArray()) })
        }
    }

    private fun field(name: String, value: String, inline: Boolean) = JsonObject().apply {
        addProperty("name", name)
        addProperty("value", value)
        addProperty("inline", inline)
    }

    private fun escape(text: String) = text.replace(MARKDOWN, """\\$1""")

    private companion object {
        val MARKDOWN = Regex("""([\\*_~`|>])""")
    }
}
