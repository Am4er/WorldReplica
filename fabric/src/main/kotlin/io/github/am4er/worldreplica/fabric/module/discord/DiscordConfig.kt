package io.github.am4er.worldreplica.fabric.module.discord

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.nio.file.Files
import java.nio.file.Path

data class DiscordConfig(val enabled: Boolean, val webhookUrl: String) {
    companion object {
        private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

        fun load(file: Path): DiscordConfig {
            if (!Files.exists(file)) {
                Files.createDirectories(file.parent)
                val defaults = JsonObject().apply {
                    addProperty("enabled", false)
                    addProperty("webhookUrl", "")
                }
                Files.writeString(file, GSON.toJson(defaults) + "\n")
                return DiscordConfig(enabled = false, webhookUrl = "")
            }

            val json = JsonParser.parseString(Files.readString(file)).asJsonObject
            return DiscordConfig(
                enabled = json.get("enabled")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
                webhookUrl = json.get("webhookUrl")?.takeIf { it.isJsonPrimitive }?.asString?.trim().orEmpty(),
            )
        }
    }
}
