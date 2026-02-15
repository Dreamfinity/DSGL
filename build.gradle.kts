import org.gradle.api.GradleException
import org.gradle.api.Task
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

data class BumpSummary(
    val oldVersion: String,
    val newVersion: String,
    val publishedModules: List<String>,
    val coordinates: List<String>
)

val semVerRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
val versionPropertyRegex = Regex("""^\s*version\s*=\s*(.+?)\s*$""")
val versionLineReplaceRegex = Regex("""^(\s*version\s*=\s*).*$""")
val versionFile: File = rootProject.file("gradle.properties")
val publishTaskPaths = listOf(
    ":core:publishToMavenLocal",
    ":mc1710:publishToMavenLocal"
)

fun parseVersion(version: String): Triple<Int, Int, Int> {
    val match = semVerRegex.matchEntire(version)
        ?: throw GradleException(
            "Version '$version' is not strict SemVer (MAJOR.MINOR.PATCH integers only). " +
                "Update ${versionFile.name}."
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

fun ensurePublishTasksConfigured() {
    val missing = publishTaskPaths.filter { path ->
        val (projectPath, taskName) = parseTaskPath(path)
        val project = rootProject.findProject(projectPath)
        project == null || project.tasks.findByName(taskName) == null
    }
    if (missing.isNotEmpty()) {
        throw GradleException(
            "Publishing is not configured for task(s): ${missing.joinToString(", ")}. " +
                "Ensure maven-publish and publishToMavenLocal are configured."
        )
    }
}

fun readCurrentVersionLine(lines: List<String>): Pair<Int, String> {
    val versionLines = lines.mapIndexedNotNull { index, line ->
        if (versionPropertyRegex.matches(line)) index else null
    }
    if (versionLines.isEmpty()) {
        throw GradleException("Missing 'version=' property in ${versionFile.path}.")
    }
    if (versionLines.size > 1) {
        throw GradleException(
            "Multiple 'version=' entries found in ${versionFile.path}. Keep a single source of truth."
        )
    }
    val lineIndex = versionLines.first()
    val value = versionPropertyRegex.matchEntire(lines[lineIndex])!!.groupValues[1].trim()
    return lineIndex to value
}

fun collectCoordinates(newVersion: String): Pair<List<String>, List<String>> {
    val modules = linkedSetOf<String>()
    val coordinates = linkedSetOf<String>()

    rootProject.allprojects.forEach { project ->
        project.version = newVersion
        val publishing = project.extensions.findByType(PublishingExtension::class.java) ?: return@forEach
        publishing.publications.withType(MavenPublication::class.java).forEach { publication ->
            publication.version = newVersion
            modules += project.path
            coordinates += "${publication.groupId}:${publication.artifactId}:${publication.version}"
        }
    }

    if (coordinates.isEmpty()) {
        throw GradleException(
            "Publishing is not configured. No MavenPublication found in root/subprojects."
        )
    }

    return modules.toList() to coordinates.toList()
}

fun registerBumpTask(taskName: String, part: VersionPart) {
    val prepareTask = tasks.register("${taskName}Prepare") {
        group = "release"
        description = "Prepare version bump (${part.name.lowercase()}) and publishing metadata."
        doLast {
            ensurePublishTasksConfigured()
            if (!versionFile.exists()) {
                throw GradleException("Version source file not found: ${versionFile.path}.")
            }
            if (!versionFile.canWrite()) {
                throw GradleException("Version source file is read-only: ${versionFile.path}.")
            }

            val originalText = versionFile.readText()
            val lines = originalText.lines().toMutableList()
            val (lineIndex, oldVersion) = readCurrentVersionLine(lines)
            val parsed = parseVersion(oldVersion)
            val newVersion = bumpVersion(parsed, part)
            lines[lineIndex] = lines[lineIndex].replace(versionLineReplaceRegex, "$1$newVersion")
            val hadTrailingNewline = originalText.endsWith("\n") || originalText.endsWith("\r\n")
            val updatedText = lines.joinToString(System.lineSeparator()) +
                if (hadTrailingNewline) System.lineSeparator() else ""
            versionFile.writeText(updatedText)

            val (modules, coords) = collectCoordinates(newVersion)
            rootProject.extensions.extraProperties["lastBumpSummary"] = BumpSummary(
                oldVersion = oldVersion,
                newVersion = newVersion,
                publishedModules = modules,
                coordinates = coords
            )

            logger.lifecycle("Version updated: $oldVersion -> $newVersion")
        }
    }

    tasks.register(taskName) {
        group = "release"
        description = "Bump ${part.name.lowercase()} version and publish all modules to Maven Local."
        dependsOn(prepareTask)
        dependsOn(publishTaskPaths)
        doLast {
            val summary = rootProject.extensions.extraProperties["lastBumpSummary"] as? BumpSummary
                ?: throw GradleException("Missing bump summary. Preparation task did not run correctly.")
            logger.lifecycle("Version bump summary: ${summary.oldVersion} -> ${summary.newVersion}")
            logger.lifecycle("Published modules: ${summary.publishedModules.joinToString(", ")}")
            logger.lifecycle("Published coordinates:")
            summary.coordinates.forEach { coordinate ->
                logger.lifecycle("  - $coordinate")
            }
        }
    }

    gradle.projectsEvaluated {
        publishTaskPaths.forEach { publishTaskPath ->
            val publishTask = rootProject.tasks.findByPath(publishTaskPath)
            publishTask?.mustRunAfter(prepareTask.get())
        }
    }
}

registerBumpTask("bumpMajor", VersionPart.MAJOR)
registerBumpTask("bumpMinor", VersionPart.MINOR)
registerBumpTask("bumpPatch", VersionPart.PATCH)
