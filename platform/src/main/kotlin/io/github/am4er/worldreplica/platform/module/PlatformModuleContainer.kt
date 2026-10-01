package io.github.am4er.worldreplica.platform.module

import io.github.am4er.worldreplica.api.module.Module
import io.github.am4er.worldreplica.api.module.ModuleContainer
import org.slf4j.LoggerFactory

class PlatformModuleContainer : ModuleContainer {
    private val modules = LinkedHashMap<Class<out Module>, Module>()

    override val name = "module-container"

    override fun registerModule(module: Module): Boolean {
        synchronized(modules) {
            require(modules.putIfAbsent(module.javaClass, module) == null) { "Module ${module.name} is already registered" }
        }

        try {
            module.load()
            return true
        } catch (e: RuntimeException) {
            synchronized(modules) {
                modules.remove(module.javaClass, module)
            }
            unload(module)
            throw e
        }
    }

    override fun <T : Module> getModule(moduleClass: Class<T>): T? = synchronized(modules) {
        modules[moduleClass]?.let(moduleClass::cast)
    }

    override fun unload() {
        val loaded = loaded()
        loaded.asReversed().forEach(::unload)

        synchronized(modules) {
            modules.values.removeAll(loaded.toSet())
        }
    }

    override fun reload() {
        for (module in loaded()) {
            try {
                module.reload()
            } catch (e: RuntimeException) {
                LOGGER.error("Module {} failed to reload", module.name, e)
            }
        }
    }

    private fun loaded(): List<Module> = synchronized(modules) {
        modules.values.toList()
    }

    private fun unload(module: Module) {
        try {
            module.unload()
        } catch (e: RuntimeException) {
            LOGGER.error("Module {} did not unload cleanly", module.name, e)
        }
    }

    private companion object {
        val LOGGER = LoggerFactory.getLogger("WorldReplica/modules")
    }
}
