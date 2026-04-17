import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.GradleException
import org.gradle.kotlin.dsl.the
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.dreamfinity.buildlogic.toKotlinPackageSegmentFromProjectName
import java.io.File
import java.util.Properties

plugins {
    id("dsgl-jvm-publish.conventions")
}

val sourceSets = the<SourceSetContainer>()
val metadataGeneratedSourcesDir = layout.buildDirectory.dir("generated/sources/dsglMetadata/main/kotlin")
val modulePropertiesFile = layout.projectDirectory.file("gradle.properties").asFile

fun readRequiredModuleVersion(file: File): String {
    if (!file.exists()) {
        throw GradleException("Missing module gradle.properties at ${file.path}.")
    }
    val properties = Properties().apply {
        file.inputStream().use(::load)
    }
    val moduleVersion = properties.getProperty("moduleVersion")?.trim()
    if (!moduleVersion.isNullOrEmpty()) {
        return moduleVersion
    }
    val publishEnabled = properties.getProperty("publishEnabled")?.trim()?.let { raw ->
        raw.toBooleanStrictOrNull()
            ?: throw GradleException(
                "Property 'publishEnabled' in ${file.path} must be true or false."
            )
    } ?: true
    if (!publishEnabled) {
        logger.lifecycle(
            "Skipping DsglAdapterMetadata generation for ${project.path}: " +
                "publishEnabled=false and moduleVersion is not set."
        )
        return ""
    }
    throw GradleException("Missing required property 'moduleVersion' in ${file.path}.")
}

val generateDsglAdapterMetadata = tasks.register("generateDsglAdapterMetadata") {
    group = "build setup"
    description = "Generate DsglAdapterMetadata.kt from module gradle.properties."
    inputs.file(modulePropertiesFile)
    outputs.dir(metadataGeneratedSourcesDir)

    doLast {
        val moduleVersion = readRequiredModuleVersion(modulePropertiesFile)
        if (moduleVersion.isEmpty()) {
            metadataGeneratedSourcesDir.get().asFile.deleteRecursively()
            return@doLast
        }
        val packageName = buildString {
            append(project.group.toString())
            append(".dsgl.")
            append(toKotlinPackageSegmentFromProjectName(project.name))
        }
        val outputRoot = metadataGeneratedSourcesDir.get().asFile
        val outputFile = outputRoot.resolve(
            packageName.replace('.', '/') + "/DsglAdapterMetadata.kt"
        )
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            package $packageName

            object DsglAdapterMetadata {
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
    dependsOn(generateDsglAdapterMetadata)
}

tasks.named("sourcesJar") {
    dependsOn(generateDsglAdapterMetadata)
}

tasks.named("dokkaGeneratePublicationHtml") {
    dependsOn(generateDsglAdapterMetadata)
}

val devJar = tasks.register<Jar>("devJar") {
    from(sourceSets["main"].output)
    archiveClassifier.set("dev")
}

val devSourcesJar = tasks.register<Jar>("devSourcesJar") {
    dependsOn(generateDsglAdapterMetadata)
    from(sourceSets["main"].allSource)
    archiveClassifier.set("dev-sources")
}

publishing {
    publications {
        withType(MavenPublication::class.java).configureEach {
            if (name == "mavenJava") {
                artifact(devJar)
                artifact(devSourcesJar)
            }
        }
    }
}

if (project.name == "mc-forge-1-7-10") {
    tasks.matching { it.name == "reobf" }.all {
        val reobfTask = this
        tasks.matching { it.name == "generateMetadataFileForMavenJavaPublication" }.configureEach {
            dependsOn(reobfTask)
        }
        tasks.withType<PublishToMavenRepository>().configureEach {
            dependsOn(reobfTask)
        }
        tasks.withType<PublishToMavenLocal>().configureEach {
            dependsOn(reobfTask)
        }
    }
}
