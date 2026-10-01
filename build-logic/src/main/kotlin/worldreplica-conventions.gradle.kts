plugins {
    id("net.fabricmc.fabric-loom")
    id("org.jetbrains.kotlin.jvm")
}

group = "io.github.am4er"
version = project.property("worldReplicaVersion") as String

val catalog = the<VersionCatalogsExtension>().named("libs")

dependencies {
    "minecraft"(catalog.findLibrary("minecraft").get())
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
