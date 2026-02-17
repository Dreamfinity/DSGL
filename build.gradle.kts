import org.gradle.api.GradleException
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import java.io.File

plugins {
    `java-library`
}

group = property("group") as String
version = property("version") as String

repositories {
    maven(url = "https://cloudrep.veritaris.me/repos/")
    mavenCentral()
}

enum class VersionPart {
    MAJOR,
    MINOR,
    PATCH
}

enum class BumpTarget {
    CORE,
    MC1710
}

data class BumpConfig(
    val target: BumpTarget,
    val projectPath: String,
    val versionFile: File,
    val versionKey: String = "version",
    val syncedKeys: List<String> = emptyList(),
    val publishTaskPath: String
)

data class BumpSummary(
    val target: BumpTarget,
    val projectPath: String,
    val oldVersion: String,
    val newVersion: String,
    val updatedKeys: List<String>,
    val publishedModules: List<String>,
    val coordinates: List<String>
)

val semVerRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
val coreBumpConfig = BumpConfig(
    target = BumpTarget.CORE,
    projectPath = ":core",
    versionFile = rootProject.file("gradle.properties"),
    versionKey = "version",
    publishTaskPath = ":core:publishToMavenLocal"
)
val mc1710BumpConfig = BumpConfig(
    target = BumpTarget.MC1710,
    projectPath = ":mc1710",
    versionFile = rootProject.file("mc1710/gradle.properties"),
    versionKey = "version",
    syncedKeys = listOf("modVersion"),
    publishTaskPath = ":mc1710:publishToMavenLocal"
)

fun parseVersion(version: String): Triple<Int, Int, Int> {
    val match = semVerRegex.matchEntire(version)
        ?: throw GradleException(
            "Version '$version' is not strict SemVer (MAJOR.MINOR.PATCH integers only)."
        )
    val major = match.groupValues[1].toInt()
    val minor = match.groupValues[2].toInt()
    val patch = match.groupValues[3].toInt()
    return Triple(major, minor, patch)
}

fun bumpVersion(current: Triple<Int, Int, Int>, part: VersionPart): String {
    val (major, minor, patch) = current
    return when (part) {
        VersionPart.MAJOR -> "${major + 1}.0.0"
        VersionPart.MINOR -> "$major.${minor + 1}.0"
        VersionPart.PATCH -> "$major.$minor.${patch + 1}"
    }
}

fun parseTaskPath(path: String): Pair<String, String> {
    val idx = path.lastIndexOf(':')
    if (idx <= 0 || idx >= path.length - 1) {
        throw GradleException("Invalid task path '$path'. Expected ':project:taskName'.")
    }
    val projectPath = path.substring(0, idx)
    val taskName = path.substring(idx + 1)
    return projectPath to taskName
}

fun versionRegexForKey(key: String): Regex {
    return Regex("""^\s*${Regex.escape(key)}\s*=\s*(.+?)\s*$""")
}

fun versionReplaceRegexForKey(key: String): Regex {
    return Regex("""^(\s*${Regex.escape(key)}\s*=\s*).*$""")
}

fun readPropertyLine(lines: List<String>, key: String, file: File): Pair<Int, String> {
    val regex = versionRegexForKey(key)
    val matches = lines.mapIndexedNotNull { index, line ->
        if (regex.matches(line)) index else null
    }
    if (matches.isEmpty()) {
        throw GradleException("Missing '$key=' property in ${file.path}.")
    }
    if (matches.size > 1) {
        throw GradleException("Multiple '$key=' entries found in ${file.path}. Keep a single value.")
    }
    val lineIndex = matches.first()
    val value = regex.matchEntire(lines[lineIndex])!!.groupValues[1].trim()
    return lineIndex to value
}

fun ensurePublishTaskConfigured(config: BumpConfig) {
    val (projectPath, taskName) = parseTaskPath(config.publishTaskPath)
    val project = rootProject.findProject(projectPath)
    if (project == null || project.tasks.findByName(taskName) == null) {
        throw GradleException(
            "Publishing is not configured for '${config.publishTaskPath}'. " +
                "Ensure maven-publish and publishToMavenLocal are configured for ${config.projectPath}."
        )
    }
}

fun applyRuntimeVersion(config: BumpConfig, newVersion: String): Pair<List<String>, List<String>> {
    val project = rootProject.findProject(config.projectPath)
        ?: throw GradleException("Project '${config.projectPath}' not found.")
    project.version = newVersion
    if (config.syncedKeys.contains("modVersion")) {
        project.extensions.extraProperties["modVersion"] = newVersion
    }

    val publishing = project.extensions.findByType(PublishingExtension::class.java)
        ?: throw GradleException(
            "Publishing is not configured for '${config.projectPath}'. Missing PublishingExtension."
        )

    val coordinates = linkedSetOf<String>()
    val modules = linkedSetOf<String>()
    publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
        publication.version = newVersion
        modules += project.path
        coordinates += "${publication.groupId}:${publication.artifactId}:${publication.version}"
    }
    if (coordinates.isEmpty()) {
        throw GradleException(
            "Publishing is not configured for '${config.projectPath}'. No MavenPublication found."
        )
    }
    return modules.toList() to coordinates.toList()
}

fun registerBumpTask(taskName: String, part: VersionPart, config: BumpConfig) {
    val prepareTask = tasks.register("${taskName}Prepare") {
        group = "release"
        description = "Prepare ${config.target.name.lowercase()} ${part.name.lowercase()} bump and publishing metadata."
        doLast {
            ensurePublishTaskConfigured(config)
            if (!config.versionFile.exists()) {
                throw GradleException("Version source file not found: ${config.versionFile.path}.")
            }
            if (!config.versionFile.canWrite()) {
                throw GradleException("Version source file is read-only: ${config.versionFile.path}.")
            }

            val originalText = config.versionFile.readText()
            val lines = originalText.lines().toMutableList()
            val (versionLineIndex, oldVersion) = readPropertyLine(lines, config.versionKey, config.versionFile)
            val parsed = parseVersion(oldVersion)
            val newVersion = bumpVersion(parsed, part)
            val keysToUpdate = listOf(config.versionKey) + config.syncedKeys
            val lineIndexes = keysToUpdate.associateWith { key ->
                readPropertyLine(lines, key, config.versionFile).first
            }
            keysToUpdate.forEach { key ->
                val lineIndex = lineIndexes[key] ?: versionLineIndex
                val replaceRegex = versionReplaceRegexForKey(key)
                lines[lineIndex] = lines[lineIndex].replace(replaceRegex, "$1$newVersion")
            }
            val hadTrailingNewline = originalText.endsWith("\n") || originalText.endsWith("\r\n")
            val updatedText = lines.joinToString(System.lineSeparator()) +
                if (hadTrailingNewline) System.lineSeparator() else ""
            config.versionFile.writeText(updatedText)

            val (modules, coords) = applyRuntimeVersion(config, newVersion)
            rootProject.extensions.extraProperties["lastBumpSummary"] = BumpSummary(
                target = config.target,
                projectPath = config.projectPath,
                oldVersion = oldVersion,
                newVersion = newVersion,
                updatedKeys = keysToUpdate,
                publishedModules = modules,
                coordinates = coords
            )

            logger.lifecycle("${config.target.name.lowercase()} version updated: $oldVersion -> $newVersion")
        }
    }

    tasks.register(taskName) {
        group = "release"
        description = "Bump ${config.target.name.lowercase()} ${part.name.lowercase()} version and publish to Maven Local."
        dependsOn(prepareTask)
        dependsOn(config.publishTaskPath)
        doLast {
            val summary = rootProject.extensions.extraProperties["lastBumpSummary"] as? BumpSummary
                ?: throw GradleException("Missing bump summary. Preparation task did not run correctly.")
            logger.lifecycle("Version bump summary (${summary.target.name.lowercase()}): ${summary.oldVersion} -> ${summary.newVersion}")
            logger.lifecycle("Updated keys in properties: ${summary.updatedKeys.joinToString(", ")}")
            logger.lifecycle("Published modules: ${summary.publishedModules.joinToString(", ")}")
            logger.lifecycle("Published coordinates:")
            summary.coordinates.forEach { coordinate ->
                logger.lifecycle("  - $coordinate")
            }
        }
    }

    gradle.projectsEvaluated {
        val publishTask = rootProject.tasks.findByPath(config.publishTaskPath)
        publishTask?.mustRunAfter(prepareTask.get())
    }
}

registerBumpTask("bumpCoreMajor", VersionPart.MAJOR, coreBumpConfig)
registerBumpTask("bumpCoreMinor", VersionPart.MINOR, coreBumpConfig)
registerBumpTask("bumpCorePatch", VersionPart.PATCH, coreBumpConfig)

registerBumpTask("bumpMc1710Major", VersionPart.MAJOR, mc1710BumpConfig)
registerBumpTask("bumpMc1710Minor", VersionPart.MINOR, mc1710BumpConfig)
registerBumpTask("bumpMc1710Patch", VersionPart.PATCH, mc1710BumpConfig)

tasks.register("bumpMajor") {
    group = "release"
    description = "Alias for bumpCoreMajor."
    dependsOn("bumpCoreMajor")
}
tasks.register("bumpMinor") {
    group = "release"
    description = "Alias for bumpCoreMinor."
    dependsOn("bumpCoreMinor")
}
tasks.register("bumpPatch") {
    group = "release"
    description = "Alias for bumpCorePatch."
    dependsOn("bumpCorePatch")
}
