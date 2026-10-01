pluginManagement {
    includeBuild("build-logic")

    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "worldreplica"

include(
    "api",
    "platform",
    "fabric",
)
