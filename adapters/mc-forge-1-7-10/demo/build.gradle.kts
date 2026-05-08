plugins {
    id("dsgl-mc-adapter.conventions")
    id("dsgl-mc-forge-1-7-10.conventions")
    id("dsgl-linter.conventions")
    id("dsgl-static-analysis.conventions")
}

val modId: String by project
val modGroup: String by project
val modName: String by project
val modVersion: String by project
val modAuthor: String by project
val modDescription: String by project
val modCredits: String by project
val modIcon: String by project
val gameVersion: String by project

val hotReload: String by project
val msdfDebug: String by project
val msdfDebugDecorations: String by project
val msdfDebugPerformance: String by project
val rebuildTrace: String by project
val perfDebug: String by project
val dsglOverlayDebug: String by project
val dsglOverlayControls: String by project
val dsglColorPickerDebugCounters: String by project
val hotReloadAgentLibraryName: String? by project

val baseModMetadataTokens =
    mapOf(
        "modId" to modId,
        "modGroup" to modGroup,
        "modName" to modName,
        "modAuthor" to modAuthor,
        "modDescription" to modDescription,
        "modCredits" to modCredits,
        "modIcon" to modIcon,
        "gameVersion" to gameVersion,
    )

fun currentModVersion(): String {
    val dynamic = (findProperty("modVersion") as? String)?.trim()
    if (!dynamic.isNullOrEmpty()) return dynamic
    if (modVersion.isNotBlank()) return modVersion
    throw GradleException("Missing required property 'modVersion' for mc-forge-1-7-10-demo module.")
}

fun currentModMetadataTokens(): Map<String, String> = baseModMetadataTokens + ("modVersion" to currentModVersion())

fun hotReloadAgentLibraryFile(): File {
    val explicitLibraryName = hotReloadAgentLibraryName?.trim()?.takeIf { it.isNotEmpty() }
    val osName = System.getProperty("os.name")?.lowercase()
    val libraryName =
        explicitLibraryName ?: when {
            osName == null -> throw GradleException(
                "Unable to determine current operating system for DSGL hot-reload agent, and 'hotReloadAgentLibraryName' is not set.",
            )

            osName.startsWith("windows") -> "dsgl_hot_reload_agent.dll"
            osName.startsWith("linux") -> "libdsgl_hot_reload_agent.so"
            osName.startsWith("mac") || osName.startsWith("darwin") -> "libdsgl_hot_reload_agent.dylib"
            else -> throw GradleException(
                "Unsupported operating system for DSGL hot-reload agent: $osName, and 'hotReloadAgentLibraryName' is not set.",
            )
        }

    return project.rootDir.resolve("dsgl-hot-reload-agent/target/release/$libraryName")
}

val generatedModMetadataDir: Provider<Directory> = layout.buildDirectory.dir("generated/sources/modMetadata/kotlin")

val generateModMetadata by tasks.registering {
    outputs.dir(generatedModMetadataDir)

    doLast {
        val tokens = currentModMetadataTokens()
        val outputDir = generatedModMetadataDir.get().asFile
        val packagePath = File(outputDir, "org/dreamfinity/dsgl/mcForge1710")
        packagePath.mkdirs()
        val outputFile = File(packagePath, "DsglMc1710DemoGeneratedMetadata.kt")

        outputFile.writeText(
            """
            package org.dreamfinity.dsgl.mcForge1710

            /**
             * Generated from Gradle properties to keep @Mod metadata consistent.
             */
            object DsglMc1710DemoGeneratedMetadata {
                const val MOD_ID: String = "${tokens["modId"]}"
                const val MOD_NAME: String = "${tokens["modName"]}"
                const val MOD_VERSION: String = "${tokens["modVersion"]}"
                const val MC_VERSION_RANGE: String = "[${tokens["gameVersion"]}]"
                const val MOD_AUTHOR: String = "${tokens["modAuthor"]}"
                const val MOD_DESCRIPTION: String = "${tokens["modDescription"]}"
                const val MOD_CREDITS: String = "${tokens["modCredits"]}"
                const val MOD_ICON: String = "${tokens["modIcon"]}"
            }
            """.trimIndent() + System.lineSeparator(),
        )
    }
}

tasks {
    runClient {
        var jvmArgs =
            listOf(
                "-Ddsgl.msdf.debug=$msdfDebug",
                "-Ddsgl.msdf.debug.decorations=$msdfDebugDecorations",
                "-Ddsgl.msdf.debug.performance=$msdfDebugPerformance",
                "-Ddsgl.rebuild.trace=$rebuildTrace",
                "-Ddsgl.perf.debug=$perfDebug",
                "-Ddsgl.overlay.debug=$dsglOverlayDebug",
                "-Ddsgl.overlay.controls=$dsglOverlayControls",
                "-Ddsgl.colorPicker.debugCounters=$dsglColorPickerDebugCounters",
            )

        if (hotReload.toBoolean()) {
            jvmArgs = jvmArgs + listOf("-agentpath:${hotReloadAgentLibraryFile().absolutePath}")
        }

        jvmArgs(jvmArgs)

        if (project.hasProperty("clientRunArgs")) {
            println("clientRunArgs: ${project.property("clientRunArgs")}")
            args(project.property("clientRunArgs"))
        }
    }

    runServer {
        if (project.hasProperty("serverRunArgs")) {
            println("serverRunArgs: ${project.property("serverRunArgs")}")
            args(project.property("serverRunArgs"))
        }
    }
}

kotlin {
    sourceSets
        .getByName("main")
        .kotlin
        .srcDir(generatedModMetadataDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateModMetadata)
}

tasks.named("sourcesJar") {
    dependsOn(generateModMetadata)
}

tasks.named("devSourcesJar") {
    dependsOn(generateModMetadata)
}

tasks.named("dokkaGeneratePublicationHtml") {
    dependsOn(generateModMetadata)
}

tasks.matching { it.name.startsWith("runKtlintCheckOver") || it.name.startsWith("runKtlintFormatOver") }.configureEach {
    dependsOn(generateModMetadata)
}

tasks.named<ProcessResources>("processResources") {
    inputs.properties(baseModMetadataTokens)
    inputs.property("modVersion", providers.provider { currentModVersion() })

    filesMatching(listOf("mcmod.info", "META-INF/MANIFEST.MF")) {
        expand(currentModMetadataTokens())
    }
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.named("processResources"))
    val processedManifest = layout.buildDirectory.file("resources/main/META-INF/MANIFEST.MF")
    manifest.from(processedManifest)
    exclude("META-INF/MANIFEST.MF")
}

tasks.named("reobf") {
    dependsOn(":adapters:mc-forge-1-7-10:reobf")
}

listOf(
    "extractMcpData",
    "getVersionJsonIndex",
    "getVersionJson",
    "extractUserDev",
    "genSrgs",
).forEach { taskName ->
    tasks.named(taskName) {
        mustRunAfter(":adapters:mc-forge-1-7-10:reobf")
    }
}

tasks.named<Test>("test") {
    mustRunAfter(":adapters:mc-forge-1-7-10:reobf")
}

tasks.withType<PublishToMavenLocal>().configureEach {
    enabled = false
}
tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
tasks.named("publish") {
    enabled = false
}
tasks.named("publishToMavenLocal") {
    enabled = false
}

repositories {
    maven {
        name = "GitHubPackages"
        url = uri("https://maven.pkg.github.com/Dreamfinity/DSGL")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":adapters:mc-forge-1-7-10"))
//    implementation("org.dreamfinity:dsgl-core:0.0.1")
//    implementation("org.dreamfinity:dsgl-mc-forge-1-7-10:0.0.1:dev")
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}
