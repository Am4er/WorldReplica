package io.github.am4er.worldreplica.api.event

interface EventService {
    fun <T : Any> subscribe(type: Class<T>, listener: (T) -> Unit)

    fun publish(event: Any)
}
