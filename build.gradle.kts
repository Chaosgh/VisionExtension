plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.0.0"
}

group = "de.chaos"
version = "0.3.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.typewritermc.com/beta")
}

dependencies {
    compileOnly("com.typewritermc:EntityExtension:0.9.0")
    compileOnly("com.typewritermc:RoadNetworkExtension:0.9.0")
}

typewriter {
    namespace = "chaos"

    extension {
        name = "Vision"
        shortDescription = "Raycast & Vision system for NPCs"
        description = "Provides an advanced vision system for NPCs. Supports raycasts, line-of-sight checks, and OnPlayerSeen criteria to simulate realistic perception and player detection within Typewriter."
        engineVersion = "0.9.0-beta-166"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies { }

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
        println("Copying ${jar.archiveFileName.get()} from ${jar.destinationDirectory.get().asFile}")
    }
}

