plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.0.0"
}

group = "de.chaos"
version = "0.2.0"


repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.typewritermc.com/releases")


}





typewriter {
    namespace = "chaos"

    extension {
        name = "Vision"
        shortDescription = "Raycast TypeWriter Addon"
        description =
            "adds a vision raycast for npcs so that they detect if you enter their field of view, you are also able to trigger that"
        engineVersion = "0.9.0-beta-165"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies {
        }

        paper()
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.jar {
    destinationDirectory.set(file("C:/Users/julie/Desktop/dev/plugins/Typewriter/extensions"))
    archiveFileName.set("VisionExtension-${version}.jar")
}
tasks.register<Copy>("copyJarToServer") {
    dependsOn("jar")
    val jar = tasks.named<Jar>("jar").get()
    from(jar.destinationDirectory)
    include(jar.archiveFileName.get())
    into("C:/Users/julie/Desktop/dev/plugins/Typewriter/extensions")

    doFirst {
        println("→ Kopiere ${jar.archiveFileName.get()} von ${jar.destinationDirectory.get().asFile}")
    }

}

