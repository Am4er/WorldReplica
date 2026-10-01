package io.github.am4er.worldreplica.fabric.module.discord

import com.google.gson.JsonObject
import org.slf4j.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DiscordWebhook(private val logger: Logger) {
    private val client: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(TIMEOUT)
        .build()

    fun post(url: URI, payload: JsonObject) {
        val request = HttpRequest.newBuilder(url)
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()

        client.sendAsync(request, HttpResponse.BodyHandlers.discarding()).whenComplete { response, error ->
            when {
                error != null -> logger.warn("Couldn't post to Discord: {}", error.message)
                response.statusCode() !in 200..299 -> logger.warn("Discord rejected the message (HTTP {})", response.statusCode())
            }
        }
    }

    private companion object {
        val TIMEOUT: Duration = Duration.ofSeconds(10)
    }
}
