plugins {
    id("worldreplica-conventions")
    id("com.gradleup.shadow")
}

val shade = configurations.create("shade") {
    isTransitive = false
}

tasks {
    shadowJar {
        configurations.set(listOf(shade))
        archiveClassifier.set("")
    }

    jar {
        archiveClassifier.set("dev")
    }

    assemble {
        dependsOn(shadowJar)
    }
}
