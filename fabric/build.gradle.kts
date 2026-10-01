plugins {
    id("worldreplica-shaded")
}

dependencies {
    api(project(":platform"))
    shade(project(":api"))
    shade(project(":platform"))

    implementation(libs.fabric.loader)
    implementation(libs.fabric.api)
    implementation(libs.fabric.kotlin)
}

fabricApi {
    configureTests {
        createSourceSet.set(true)
        modId.set("worldreplica-gametest")
        enableGameTests.set(true)
        enableClientGameTests.set(false)
        eula.set(false)
    }
}

tasks {
    processResources {
        val version = project.version
        inputs.property("version", version)

        filesMatching("fabric.mod.json") {
            expand("version" to version)
        }

        from(rootProject.file("LICENSE"))
    }

    shadowJar {
        archiveFileName.set("worldreplica-${project.version}+${libs.versions.minecraft.get()}.jar")
    }
}
