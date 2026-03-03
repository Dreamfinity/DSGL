import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.publish.maven.tasks.PublishToMavenLocal
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository

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

val baseModMetadataTokens = mapOf(
    "modId" to modId,
    "modGroup" to modGroup,
    "modName" to modName,
    "modAuthor" to modAuthor,
    "modDescription" to modDescription,
    "modCredits" to modCredits,
    "modIcon" to modIcon,
    "gameVersion" to gameVersion
)

fun currentModVersion(): String {
    val dynamic = (findProperty("modVersion") as? String)?.trim()
    if (!dynamic.isNullOrEmpty()) return dynamic
    if (modVersion.isNotBlank()) return modVersion
    throw GradleException("Missing required property 'modVersion' for mc1710-demo module.")
}

fun currentModMetadataTokens(): Map<String, String> {
    return baseModMetadataTokens + ("modVersion" to currentModVersion())
}

val generatedModMetadataDir: Provider<Directory> = layout.buildDirectory.dir("generated/sources/modMetadata/kotlin")

val generateModMetadata by tasks.registering {
    outputs.dir(generatedModMetadataDir)

    doLast {
        val tokens = currentModMetadataTokens()
        val outputDir = generatedModMetadataDir.get().asFile
        val packagePath = File(outputDir, "org/dreamfinity/dsgl/mc1710")
        packagePath.mkdirs()
        val outputFile = File(packagePath, "DsglMc1710DemoGeneratedMetadata.kt")

        outputFile.writeText(
            """
            package org.dreamfinity.dsgl.mc1710

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
            """.trimIndent()
        )
    }
}

tasks {
    runClient {
        jvmArgs(
            "-Ddsgl.msdf.debug=false",
            "-Ddsgl.msdf.debug.decorations=false",
            "-Ddsgl.msdf.debug.performance=false",
            "-Ddsgl.rebuild.trace=false",
            "-Ddsgl.perf.debug=false",
        )

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

tasks.named("devSourcesJar") {
    dependsOn(generateModMetadata)
}

tasks.named("dokkaGeneratePublicationHtml") {
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

dependencies {
    implementation(project(":mc1710"))
}
