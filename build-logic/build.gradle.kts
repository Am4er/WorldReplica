plugins {
    `kotlin-dsl`
}

repositories {
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation(libs.fabric.loom.gradle.plugin)
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.shadow.gradle.plugin)
}
