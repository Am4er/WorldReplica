package io.github.am4er.worldreplica.api.job

import java.util.UUID

interface JobService {
    fun isRunning(player: UUID): Boolean

    fun cancel(player: UUID): Boolean

    fun cancelAll()
}
