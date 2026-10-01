package io.github.am4er.worldreplica.api

object WorldReplicaProvider {
    @Volatile
    private var instance: WorldReplica? = null

    internal fun get(): WorldReplica = checkNotNull(instance) { "WorldReplica is not registered" }

    fun register(worldReplica: WorldReplica) {
        instance = worldReplica
    }
}
