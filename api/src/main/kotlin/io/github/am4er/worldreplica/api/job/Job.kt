package io.github.am4er.worldreplica.api.job

import net.minecraft.network.chat.Component
import java.util.UUID

interface Job {
    val owner: UUID
    val cancelled: Boolean

    fun status(): Component
}
