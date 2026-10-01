package io.github.am4er.worldreplica.api.module

import io.github.am4er.worldreplica.api.service.WorldReplicaService

interface Module : WorldReplicaService {
    val name: String

    fun reload()
}
