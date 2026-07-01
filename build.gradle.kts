plugins {
    kotlin("jvm") version "2.2.10"
    id("com.typewritermc.module-plugin") version "2.1.0"
    id("org.owasp.dependencycheck") version "12.2.2"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "de.chaos"
version = "0.4.0"

repositories {
    mavenCentral()
    gradlePluginPortal()
    maven("https://maven.typewritermc.com/beta")
}

dependencies {
    compileOnly("com.typewritermc:EntityExtension:0.9.0")
    compileOnly("com.typewritermc:RoadNetworkExtension:0.9.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.110.0")
    testImplementation("com.typewritermc:engine-paper:0.9.0")
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("com.github.retrooper:packetevents-api:2.11.1")
    testImplementation("com.github.retrooper:packetevents-spigot:2.11.1")
    testImplementation("com.typewritermc:EntityExtension:0.9.0")
    testImplementation("com.typewritermc:RoadNetworkExtension:0.9.0")
}

typewriter {
    namespace = "chaos"

    extension {
        name = "Vision"
        shortDescription = "Raycast & Vision system for NPCs"
        description =
            "Provides an advanced vision system for NPCs. Supports raycasts, line-of-sight checks, and " +
            "OnPlayerSeen criteria to simulate realistic perception and player detection within Typewriter."
        engineVersion = "0.9.0-beta-174"
        channel = com.typewritermc.moduleplugin.ReleaseChannel.BETA

        dependencies { }

        paper()
    }
}

kotlin {
    jvmToolchain(21)
}

ktlint {
    version.set("1.0.1")
}

tasks.jar {
    archiveFileName.set("VisionExtension-$version.jar")
}

val typewriterExtensionDir =
    providers.gradleProperty("typewriterExtensionDir")
        .orElse(providers.environmentVariable("TYPEWRITER_EXTENSION_DIR"))

tasks.register<Copy>("copyJarToServer") {
    val jar = tasks.named<Jar>("jar")
    val missingTargetFallback = layout.buildDirectory.dir("copyJarToServerMissingTarget").map { it.asFile }
    dependsOn(jar)
    from(jar.flatMap { it.archiveFile })
    into(typewriterExtensionDir.map { file(it) }.orElse(missingTargetFallback))

    doFirst {
        val targetDir =
            typewriterExtensionDir.orNull ?: throw GradleException(
                "Set -PtypewriterExtensionDir=<path> or TYPEWRITER_EXTENSION_DIR before running copyJarToServer.",
            )
        if (targetDir.isBlank()) {
            throw GradleException(
                "Set -PtypewriterExtensionDir=<path> or TYPEWRITER_EXTENSION_DIR before running copyJarToServer.",
            )
        }
        println("Copying ${jar.get().archiveFileName.get()} to $targetDir")
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("check") {
    dependsOn(tasks.named("ktlintCheck"))
}
