package io.github.am4er.worldreplica.api.module

interface ModuleContainer : Module {
    fun registerModule(module: Module): Boolean

    fun <T : Module> getModule(moduleClass: Class<T>): T?
}
