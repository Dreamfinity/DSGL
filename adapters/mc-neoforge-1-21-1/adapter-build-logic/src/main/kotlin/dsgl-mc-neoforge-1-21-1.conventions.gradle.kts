plugins {
    kotlin("jvm")
    id("net.neoforged.moddev")
}

val modId: String by properties
val modVersion: String by properties
val neoVersion: String by properties
val parchmentVersion: String by properties
val mcVersion: String by properties

neoForge {
    version = neoVersion

    parchment {
        mappingsVersion = parchmentVersion
        minecraftVersion = mcVersion
    }

    runs {
        create("client") {
            client()
            gameDirectory = project.file("runs/client")
            programArguments.addAll("--username", "Developer")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
        create("client2") {
            client()
            gameDirectory = project.file("runs/client2")
            programArguments.addAll("--username", "Developer2")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        create("server") {
            server()
            gameDirectory = project.file("runs/server")
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        // This run config launches GameTestServer and runs all registered gametests, then exits.
        // By default, the server will crash when no gametests are provided.
        // The gametest system is also enabled by default for other run configs under the /test command.
        create("gameTestServer") {
            type = "gameTestServer"
            gameDirectory = project.file("runs/gameTestServer")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        create("data") {
            data()
            gameDirectory = project.file("runs/data")

            programArguments.addAll("--mod", modId, "--all", "--output", file("src/generated/resources/").absolutePath, "--existing", file("src/main/resources/").absolutePath)
        }

        configureEach {
            // The markers can be added/remove as needed separated by commas.
            // "SCAN": For mods scan.
            // "REGISTRIES": For firing of registry events.
            // "REGISTRYDUMP": For getting the contents of all registries.
            systemProperty("forge.logging.markers", "REGISTRIES")

            //logLevel = org.slf4j.event.Level.DEBUG

            // Colorful logs
            jvmArgument("-XX:+AllowEnhancedClassRedefinition")
            systemProperty("terminal.jline", "true")
            loggingConfigFile.set(project.file("log4j2_config.xml"))
        }
    }

    mods {
        create(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    jvmToolchain(21)
}
