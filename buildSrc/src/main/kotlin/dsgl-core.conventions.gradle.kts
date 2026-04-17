import java.util.Properties
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.dreamfinity.buildlogic.toKotlinPackageSegmentFromProjectName

plugins {
    id("dsgl-jvm-publish.conventions")
}

val metadataGeneratedSourcesDir = layout.buildDirectory.dir("generated/sources/dsglMetadata/main/kotlin")
val modulePropertiesFile = layout.projectDirectory.file("gradle.properties").asFile

fun readRequiredModuleVersion(file: File): String {
    if (!file.exists()) {
        throw GradleException("Missing module gradle.properties at ${file.path}.")
    }
    val properties = Properties().apply {
        file.inputStream().use(::load)
    }
    return properties.getProperty("moduleVersion")?.trim().takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("Missing required property 'moduleVersion' in ${file.path}.")
}

val generateDsglCoreMetadata = tasks.register("generateDsglCoreMetadata") {
    group = "build setup"
    description = "Generate DsglCoreMetadata.kt from module gradle.properties."
    inputs.file(modulePropertiesFile)
    outputs.dir(metadataGeneratedSourcesDir)

    doLast {
        val moduleVersion = readRequiredModuleVersion(modulePropertiesFile)
        val packageName = buildString {
            append(project.group.toString())
            append(".dsgl.")
            append(toKotlinPackageSegmentFromProjectName(project.name))
        }
        val outputRoot = metadataGeneratedSourcesDir.get().asFile
        val outputFile = outputRoot.resolve(
            packageName.replace('.', '/') + "/DsglCoreMetadata.kt"
        )
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package $packageName

            object DsglCoreMetadata {
                const val VERSION: String = "$moduleVersion"
            }
            """.trimIndent() + System.lineSeparator()
        )
    }
}

extensions.configure<KotlinJvmProjectExtension> {
    sourceSets.named("main") {
        kotlin.srcDir(metadataGeneratedSourcesDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateDsglCoreMetadata)
}

tasks.named("sourcesJar") {
    dependsOn(generateDsglCoreMetadata)
}

tasks.named("dokkaGeneratePublicationHtml") {
    dependsOn(generateDsglCoreMetadata)
}
