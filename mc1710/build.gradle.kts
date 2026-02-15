import java.io.File

plugins {
    id("dsgl-mc1710.conventions")
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

val modMetadataTokens = mapOf(
    "modId" to modId,
    "modGroup" to modGroup,
    "modName" to modName,
    "modVersion" to modVersion,
    "modAuthor" to modAuthor,
    "modDescription" to modDescription,
    "modCredits" to modCredits,
    "modIcon" to modIcon,
    "gameVersion" to gameVersion
)

val generatedModMetadataDir = layout.buildDirectory.dir("generated/sources/modMetadata/kotlin")

val generateModMetadata by tasks.registering {
    outputs.dir(generatedModMetadataDir)

    doLast {
        val outputDir = generatedModMetadataDir.get().asFile
        val packagePath = File(outputDir, "org/dreamfinity/dsgl/mc1710")
        packagePath.mkdirs()
        val outputFile = File(packagePath, "DsglMc1710GeneratedMetadata.kt")

        outputFile.writeText(
            """
            package org.dreamfinity.dsgl.mc1710

            /**
             * Generated from Gradle properties to keep @Mod metadata consistent.
             */
            object DsglMc1710GeneratedMetadata {
                const val MOD_ID: String = "$modId"
                const val MOD_NAME: String = "$modName"
                const val MOD_VERSION: String = "$modVersion"
                const val MC_VERSION_RANGE: String = "[$gameVersion]"
                const val MOD_AUTHOR: String = "$modAuthor"
                const val MOD_DESCRIPTION: String = "$modDescription"
                const val MOD_CREDITS: String = "$modCredits"
                const val MOD_ICON: String = "$modIcon"
            }
            """.trimIndent()
        )
    }
}

tasks {
    runClient {
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
    sourceSets.getByName("main").kotlin.srcDir(generatedModMetadataDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateModMetadata)
}

tasks.named("sourcesJar") {
    dependsOn(generateModMetadata)
}

tasks.named<ProcessResources>("processResources") {
    inputs.properties(modMetadataTokens)

    filesMatching(listOf("mcmod.info", "META-INF/MANIFEST.MF")) {
        expand(modMetadataTokens)
    }
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.named("processResources"))
    val processedManifest = layout.buildDirectory.file("resources/main/META-INF/MANIFEST.MF")
    manifest.from(processedManifest)
    exclude("META-INF/MANIFEST.MF")
}

dependencies {
    val coreProject = findProject(":core")
        ?: findProject(":dsgl:core")
        ?: error("DSGL core project not found (expected :core or :dsgl:core).")
    api(coreProject)
}
