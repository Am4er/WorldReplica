package io.github.am4er.worldreplica.platform.event

import io.github.am4er.worldreplica.api.event.EventService
import org.slf4j.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

class PlatformEvents(private val logger: Logger) : EventService {
    private val listeners = ConcurrentHashMap<Class<*>, CopyOnWriteArrayList<(Any) -> Unit>>()

    override fun <T : Any> subscribe(type: Class<T>, listener: (T) -> Unit) {
        listeners.computeIfAbsent(type) { CopyOnWriteArrayList() }.add { listener(type.cast(it)) }
    }

    override fun publish(event: Any) {
        val handlers = listeners[event.javaClass] ?: return
        for (handler in handlers) {
            try {
                handler(event)
            } catch (e: RuntimeException) {
                logger.error("A {} listener failed", event.javaClass.simpleName, e)
            }
        }
    }
}
