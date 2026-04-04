import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

enum class DsglReleaseVersionPart {
    MAJOR,
    MINOR,
    PATCH
}

private val releaseSemVerRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")

abstract class DsglReleaseTask : DefaultTask() {
    @get:Input
    abstract val releaseEnabled: Property<Boolean>

    @get:InputFile
    abstract val versionFile: RegularFileProperty

    @get:Input
    abstract val versionKey: Property<String>

    @get:Input
    abstract val buildVersionKey: Property<String>

    @get:Input
    abstract val syncKeys: ListProperty<String>

    protected fun readVersionFileLines(): List<String> {
        return versionFile.get().asFile.readText().lines()
    }

    protected fun requireExistingWritableFile() {
        val file = versionFile.get().asFile
        if (!file.exists()) {
            throw GradleException("Version source file not found: ${file.path}.")
        }
        if (!file.canWrite()) {
            throw GradleException("Version source file is read-only: ${file.path}.")
        }
    }

    protected fun readPropertyLine(lines: List<String>, key: String): Pair<Int, String> {
        val regex = Regex("""^\s*${Regex.escape(key)}\s*=\s*(.+?)\s*$""")
        val matches = lines.mapIndexedNotNull { index, line ->
            if (regex.matches(line)) index else null
        }
        if (matches.isEmpty()) {
            throw GradleException("Missing '$key=' property in ${versionFile.get().asFile.path}.")
        }
        if (matches.size > 1) {
            throw GradleException(
                "Multiple '$key=' entries found in ${versionFile.get().asFile.path}. Keep a single value."
            )
        }
        val lineIndex = matches.first()
        val value = regex.matchEntire(lines[lineIndex])!!.groupValues[1].trim()
        return lineIndex to value
    }

    protected fun parseSemanticVersion(value: String, key: String): Triple<Int, Int, Int> {
        val match = releaseSemVerRegex.matchEntire(value)
            ?: throw GradleException(
                "Property '$key' in ${versionFile.get().asFile.path} must be strict MAJOR.MINOR.PATCH, but was '$value'."
            )
        return Triple(
            match.groupValues[1].toInt(),
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt()
        )
    }

    protected fun parseNonNegativeInt(value: String, key: String): Int {
        val parsed = value.toIntOrNull()
            ?: throw GradleException(
                "Property '$key' in ${versionFile.get().asFile.path} must be a non-negative integer, but was '$value'."
            )
        if (parsed < 0) {
            throw GradleException(
                "Property '$key' in ${versionFile.get().asFile.path} must be a non-negative integer, but was '$value'."
            )
        }
        return parsed
    }

    protected fun computeEffectiveReleaseVersion(): String {
        val semanticVersionKey = versionKey.get()
        val buildKey = buildVersionKey.get()
        val lines = readVersionFileLines()
        val semanticVersion = readPropertyLine(lines, semanticVersionKey).second
        parseSemanticVersion(semanticVersion, semanticVersionKey)

        val buildVersionRaw = readPropertyLine(lines, buildKey).second
        val buildVersion = parseNonNegativeInt(buildVersionRaw, buildKey)

        return if (buildVersion == 0) {
            semanticVersion
        } else {
            "$semanticVersion-build-$buildVersion"
        }
    }

    protected fun bumpSemanticVersion(current: Triple<Int, Int, Int>, part: DsglReleaseVersionPart): String {
        val (major, minor, patch) = current
        return when (part) {
            DsglReleaseVersionPart.MAJOR -> "${major + 1}.0.0"
            DsglReleaseVersionPart.MINOR -> "$major.${minor + 1}.0"
            DsglReleaseVersionPart.PATCH -> "$major.$minor.${patch + 1}"
        }
    }

    protected fun replacePropertyValue(line: String, key: String, newValue: String): String {
        val replaceRegex = Regex("""^(\s*${Regex.escape(key)}\s*=\s*).*$""")
        return replaceRegex.replace(line) { matchResult ->
            matchResult.groupValues[1] + newValue
        }
    }
}

abstract class PrintReleaseVersionTask : DsglReleaseTask() {
    init {
        onlyIf { releaseEnabled.get() }
    }

    @TaskAction
    fun printVersion() {
        logger.lifecycle("Release version for ${project.path}: ${computeEffectiveReleaseVersion()}")
    }
}

abstract class BumpModuleVersionTask : DsglReleaseTask() {
    @get:Input
    abstract val part: Property<DsglReleaseVersionPart>

    init {
        onlyIf { releaseEnabled.get() }
    }

    @TaskAction
    fun bumpVersion() {
        requireExistingWritableFile()
        val file = versionFile.get().asFile
        val originalText = file.readText()
        val lines = originalText.lines().toMutableList()
        val semanticVersionKey = versionKey.get()
        val buildKey = buildVersionKey.get()
        val syncKeysToUpdate = syncKeys.get()

        val (versionLineIndex, currentSemanticVersion) = readPropertyLine(lines, semanticVersionKey)
        val currentParsedVersion = parseSemanticVersion(currentSemanticVersion, semanticVersionKey)
        val (_, buildVersionRaw) = readPropertyLine(lines, buildKey)
        parseNonNegativeInt(buildVersionRaw, buildKey)

        val newSemanticVersion = bumpSemanticVersion(currentParsedVersion, part.get())
        val versionKeysToUpdate = linkedSetOf(semanticVersionKey).apply {
            addAll(syncKeysToUpdate)
        }
        val lineIndexes = linkedMapOf<String, Int>()
        versionKeysToUpdate.forEach { key ->
            lineIndexes[key] = readPropertyLine(lines, key).first
        }
        val buildLineIndex = readPropertyLine(lines, buildKey).first

        versionKeysToUpdate.forEach { key ->
            val lineIndex = lineIndexes[key] ?: versionLineIndex
            lines[lineIndex] = replacePropertyValue(lines[lineIndex], key, newSemanticVersion)
        }
        lines[buildLineIndex] = replacePropertyValue(lines[buildLineIndex], buildKey, "0")

        while (lines.isNotEmpty() && lines.last().isBlank()) {
            lines.removeAt(lines.lastIndex)
        }
        val updatedText = lines.joinToString(System.lineSeparator()) +
                System.lineSeparator() + System.lineSeparator()
        file.writeText(updatedText)

        logger.lifecycle(
            "Updated ${project.path}: $semanticVersionKey $currentSemanticVersion -> $newSemanticVersion; " +
                    "$buildKey reset to 0"
        )
        if (syncKeysToUpdate.isNotEmpty()) {
            logger.lifecycle("Updated sync keys: ${syncKeysToUpdate.joinToString(", ")}")
        }
    }
}
