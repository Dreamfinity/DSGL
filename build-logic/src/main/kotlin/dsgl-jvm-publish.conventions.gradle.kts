import groovy.util.Node
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.util.*

repositories {
    maven(url = "https://cloudrep.veritaris.me/repos/")
    mavenCentral()
}

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka")
}

group = property("group") as String

val modulePropertiesFile = layout.projectDirectory.file("gradle.properties").asFile

fun loadModulePropertiesIfPresent(file: File): Properties {
    return Properties().apply {
        if (file.exists()) {
            file.inputStream().use(::load)
        }
    }
}

fun readRequiredTrimmedProperty(properties: Properties, key: String): String {
    val value = properties.getProperty(key)?.trim()
    if (value.isNullOrEmpty()) {
        throw GradleException("Missing required property '$key' in ${modulePropertiesFile.path}.")
    }
    return value
}

fun readPublishEnabled(properties: Properties): Boolean {
    val raw = properties.getProperty("publishEnabled")?.trim() ?: return true
    return raw.toBooleanStrictOrNull()
        ?: throw GradleException("Property 'publishEnabled' in ${modulePropertiesFile.path} must be true or false.")
}

fun computeEffectivePublishedVersion(properties: Properties): String {
    if (!modulePropertiesFile.exists()) {
        throw GradleException("Missing module gradle.properties at ${modulePropertiesFile.path}.")
    }
    val moduleVersion = readRequiredTrimmedProperty(properties, "moduleVersion")
    val buildVersionRaw = readRequiredTrimmedProperty(properties, "buildVersion")
    val buildVersion = buildVersionRaw.toIntOrNull()
        ?: throw GradleException("Property 'buildVersion' in ${modulePropertiesFile.path} must be an integer.")
    if (buildVersion < 0) {
        throw GradleException("Property 'buildVersion' in ${modulePropertiesFile.path} must be >= 0.")
    }
    return if (buildVersion == 0) {
        moduleVersion
    } else {
        "$moduleVersion-build-$buildVersion-SNAPSHOT"
    }
}


fun readPublishProjectDepsOnly(properties: Properties): Boolean {
    val raw = properties.getProperty("publishProjectDepsOnly")?.trim() ?: return false
    return raw.toBooleanStrictOrNull()
        ?: throw GradleException(
            "Property 'publishProjectDepsOnly' in ${modulePropertiesFile.path} must be true or false."
        )
}

val moduleProperties = loadModulePropertiesIfPresent(modulePropertiesFile)
val publishProjectDepsOnly = readPublishProjectDepsOnly(moduleProperties)
val publishEnabled = readPublishEnabled(moduleProperties)
val effectivePublishedVersion = if (publishEnabled) {
    computeEffectivePublishedVersion(moduleProperties)
} else {
    null
}

version = effectivePublishedVersion ?: (property("version") as String)

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

tasks.withType<Jar>().configureEach {
    archiveBaseName.set("dsgl-${project.name}")
}

val dokkaHtml = tasks.named("dokkaGeneratePublicationHtml")
val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(dokkaHtml)
    from(dokkaHtml.map { it.outputs.files })
    archiveClassifier.set("javadoc")
}

fun configurePublishing(publicationVersion: String) {
    val isSnapshotPublication = publicationVersion.endsWith("-SNAPSHOT")
    val moduleGroup = project.group.toString()

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                groupId = moduleGroup
                artifactId = "dsgl-${project.name}"
                version = publicationVersion

                if (!publishProjectDepsOnly) {
                    from(components["java"])
                    artifact(dokkaJavadocJar)
                } else {
                    artifact(tasks.named("jar"))
                    artifact(tasks.named("sourcesJar"))
                    artifact(dokkaJavadocJar)

                    pom.withXml {
                        val root = asNode()

                        @Suppress("UNCHECKED_CAST")
                        val existingDeps = root.children()
                            .filterIsInstance<Node>()
                            .filter { it.name().toString() == "dependencies" }
                            .toList()
                        existingDeps.forEach { root.remove(it) }

                        val depsNode = root.appendNode("dependencies")

                        fun addDependency(
                            group: String,
                            artifact: String,
                            version: String,
                            scope: String
                        ) {
                            val dep = depsNode.appendNode("dependency")
                            dep.appendNode("groupId", group)
                            dep.appendNode("artifactId", artifact)
                            dep.appendNode("version", version)
                            dep.appendNode("scope", scope)
                        }

                        fun addProjectDependencies(
                            configurationName: String,
                            scope: String
                        ) {
                            val cfg = configurations.findByName(configurationName) ?: return
                            cfg.dependencies.withType(ProjectDependency::class.java).forEach { dep ->
                                val target = project.project(dep.path)
                                addDependency(
                                    target.group.toString(),
                                    "dsgl-${target.name}",
                                    target.version.toString(),
                                    scope
                                )
                            }
                        }

                        addProjectDependencies("api", "compile")
                        addProjectDependencies("implementation", "runtime")
                    }
                }
            }
        }

        repositories {
            mavenLocal()

            fun configureGitHubPackagesRepository() {
                maven {
                    name = "GitHubPackages"

                    val owner = providers.gradleProperty("gpr.owner").orNull
                        ?: System.getenv("GITHUB_REPOSITORY_OWNER")
                        ?: "developer"

                    val repository = providers.gradleProperty("gpr.repo").orNull
                        ?: System.getenv("GITHUB_REPOSITORY")?.substringAfter("/")
                        ?: rootProject.name

                    url = uri("https://maven.pkg.github.com/${owner.lowercase()}/$repository")

                    credentials {
                        username = providers.gradleProperty("gpr.user").orNull
                            ?: System.getenv("GITHUB_ACTOR")
                        password = providers.gradleProperty("gpr.key").orNull
                            ?: System.getenv("GITHUB_TOKEN")
                    }
                }
            }

            fun configureDreamfinityNexusRepository() {
                maven {
                    name = "DreamfinityNexus"
                    val releasesUrl = uri("https://nexus.dreamfinity.org/repository/maven-releases/")
                    val snapshotsUrl = uri("https://nexus.dreamfinity.org/repository/maven-snapshots/")

                    url = if (isSnapshotPublication) snapshotsUrl else releasesUrl

                    credentials {
                        username = providers.gradleProperty("nexus.user").orNull
                            ?: System.getenv("NEXUS_USERNAME")
                        password = providers.gradleProperty("nexus.password").orNull
                            ?: System.getenv("NEXUS_PASSWORD")
                    }
                }
            }

            if (!isSnapshotPublication) {
                configureGitHubPackagesRepository()
            }
            configureDreamfinityNexusRepository()
        }
    }
}

fun disablePublishingTasks() {
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
}

if (publishEnabled) {
    val publicationVersion = requireNotNull(effectivePublishedVersion) {
        "Expected effective published version for publish-enabled module ${project.path}."
    }
    configurePublishing(publicationVersion)
} else {
    disablePublishingTasks()
}
