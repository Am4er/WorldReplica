package io.github.am4er.worldreplica.api

import io.github.am4er.worldreplica.api.event.EventService
import io.github.am4er.worldreplica.api.job.JobService
import io.github.am4er.worldreplica.api.module.ModuleContainer
import io.github.am4er.worldreplica.api.schematic.SchematicStorage
import io.github.am4er.worldreplica.api.selection.SelectionService

interface WorldReplica {
    val moduleContainer: ModuleContainer
    val selections: SelectionService
    val schematics: SchematicStorage
    val jobs: JobService
    val events: EventService

    companion object {
        @JvmStatic
        fun get(): WorldReplica = WorldReplicaProvider.get()
    }
}
