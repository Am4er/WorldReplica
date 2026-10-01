package io.github.am4er.worldreplica.api.schematic

import java.nio.file.Path

interface SchematicStorage {
    val directory: Path

    fun isValidName(name: String): Boolean

    fun file(name: String): Path

    fun find(name: String): Path?

    fun names(): List<String>
}
