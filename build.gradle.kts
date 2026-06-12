import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

plugins {
    `java-library`
    kotlin("jvm") apply false
    id("org.jetbrains.kotlin.plugin.serialization") apply false
    id("org.jetbrains.dokka") version "2.1.0" apply false
}


val fontsRootDir: File = rootProject.file("fonts")
val msdfGeneratorExe: File = rootProject.file("bin/msdf-atlas-gen.exe")
val generatedPrecompiledFontsDir: File = project.file("precompiled_fonts")

tasks.register("generateMsdfAtlases") {
    group = "assets"
    description = "Generate MTSDF atlases and metadata for fonts/**/*.ttf using bin/msdf-atlas-gen.exe."

    val ttfTree = fileTree(fontsRootDir) {
        include("**/*.ttf")
    }

    inputs.files(ttfTree)
    val registryFile = File(generatedPrecompiledFontsDir, "generated-fonts.txt")
    println("Fonts registry file: ${registryFile.path}")
    outputs.file(registryFile)
    ttfTree.files.forEach { ttf ->
        val relative = ttf.relativeTo(fontsRootDir).invariantSeparatorsPath
        val base = relative.removeSuffix(".ttf")
        outputs.file(File(generatedPrecompiledFontsDir, "$base-mtsdf.png"))
        outputs.file(File(generatedPrecompiledFontsDir, "$base-meta.json"))
        outputs.file(File(generatedPrecompiledFontsDir, "$base.ttf"))
    }

    doLast {
        if (!msdfGeneratorExe.exists()) {
            throw GradleException("MSDF generator not found: ${msdfGeneratorExe.path}")
        }
        val fonts = ttfTree.files.sortedBy { it.relativeTo(fontsRootDir).invariantSeparatorsPath }
        if (fonts.isEmpty()) {
            logger.lifecycle("No .ttf fonts found in ${fontsRootDir.path}")
            return@doLast
        }

        fonts.forEach { ttf ->
            val relative = ttf.relativeTo(fontsRootDir).invariantSeparatorsPath
            val base = relative.removeSuffix(".ttf")
            val outputPng = File(generatedPrecompiledFontsDir, "$base-mtsdf.png")
            val outputJson = File(generatedPrecompiledFontsDir, "$base-meta.json")
            val outputTtf = File(generatedPrecompiledFontsDir, "$base.ttf")
            outputPng.parentFile?.mkdirs()
            outputJson.parentFile?.mkdirs()
            outputTtf.parentFile?.mkdirs()

            val pngAtlasOutArg = "precompiled_fonts/${base}-mtsdf.png"
            val rgbaAtlasOutArg = "precompiled_fonts/${base}-mtsdf.rgba"
            val jsonOutArg = "precompiled_fonts/${base}-meta.json"
            val fontArg = "./fonts/$relative"
            val charsetFile = "./fonts/charset.txt"
            val pxrange = 6
            val size = 32
            val commonArgs = listOf(
                msdfGeneratorExe.absolutePath,
                "-font", fontArg,
                "-allglyphs",
                "-type", "mtsdf",
                "-size", "$size",
                "-pxrange", "$pxrange",
            )
            val pngArgs = commonArgs + listOf(
                "-format", "png",
                "-imageout", pngAtlasOutArg,
            )
            val rgbaArgs = commonArgs + listOf(
                "-format", "rgba",
                "-imageout", rgbaAtlasOutArg,
                "-json", jsonOutArg,
            )

            println("Generating png atlas for $fontArg")
            println("Command is: '${pngArgs.joinToString(" ")}'")

            val genPNGResult = exec {
                workingDir = rootProject.projectDir
                commandLine(pngArgs)
                isIgnoreExitValue = true
            }
            if (genPNGResult.exitValue != 0) {
                throw GradleException(
                    "msdf-atlas-gen failed for '$fontArg' with exit code ${genPNGResult.exitValue}. " +
                        "Expected outputs: '$pngAtlasOutArg', '$jsonOutArg'",
                )
            }

            println("Generating rgba (binary) atlas for $fontArg")
            println("Command is: '${rgbaArgs.joinToString(" ")}'")

            val genRGBAResult = exec {
                workingDir = rootProject.projectDir
                commandLine(rgbaArgs)
                isIgnoreExitValue = true
            }
            if (genRGBAResult.exitValue != 0) {
                throw GradleException(
                    "msdf-atlas-gen failed for '$fontArg' with exit code ${genRGBAResult.exitValue}. " +
                        "Expected outputs: '$pngAtlasOutArg', '$jsonOutArg'",
                )
            }

            ttf.copyTo(outputTtf, overwrite = true)
        }

        val registryLines = fonts.map { it.relativeTo(fontsRootDir).invariantSeparatorsPath }
        registryFile.parentFile?.mkdirs()
        registryFile.writeText(registryLines.joinToString(System.lineSeparator()))
    }

    finalizedBy("compressMsdfRgbaAtlases")
}


tasks.register("compressMsdfRgbaAtlases") {
    group = "assets"
    description = "Vertically flip custom *.rgba atlases in-memory and deflate-compress to *.rgba.deflate (no deps)."
    dependsOn("generateMsdfAtlases")

    val rgbaTree = fileTree(generatedPrecompiledFontsDir) { include("**/*-mtsdf.rgba") }

    inputs.files(rgbaTree)
    rgbaTree.files.forEach { rgba ->
        outputs.file(File(rgba.parentFile, rgba.name + ".deflate"))
    }

    fun readInt32BE(bytes: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).int

    fun flipVerticallyInPlace(bytes: ByteArray) {
        val headerBytes = 12
        val width = readInt32BE(bytes, 4)
        val height = readInt32BE(bytes, 8)

        require(width > 0 && height > 0) { "Invalid atlas size: $width x $height" }

        val rowBytes = Math.multiplyExact(width, 4)
        val pixelBytes = Math.multiplyExact(rowBytes, height)
        val expectedTotal = headerBytes + pixelBytes
        require(bytes.size == expectedTotal) {
            "Size mismatch: got ${bytes.size}, expected $expectedTotal (w=$width h=$height)"
        }

        val tmp = ByteArray(rowBytes)
        var top = headerBytes
        var bottom = headerBytes + (height - 1) * rowBytes

        while (top < bottom) {
            System.arraycopy(bytes, top, tmp, 0, rowBytes)
            System.arraycopy(bytes, bottom, bytes, top, rowBytes)
            System.arraycopy(tmp, 0, bytes, bottom, rowBytes)
            top += rowBytes
            bottom -= rowBytes
        }
    }

    doLast {
        val files = rgbaTree.files.sortedBy { it.invariantSeparatorsPath }
        if (files.isEmpty()) return@doLast

        files.forEach { rgba ->
            val out = File(rgba.parentFile, rgba.name + ".deflate")
            out.parentFile?.mkdirs()
            val tmpOut = File(out.parentFile, out.name + ".tmp")

            val raw = Files.readAllBytes(rgba.toPath())

            flipVerticallyInPlace(raw)

            val deflater = Deflater(Deflater.BEST_SPEED, true)
            BufferedOutputStream(Files.newOutputStream(tmpOut.toPath()), 256 * 1024).use { fileOut ->
                DeflaterOutputStream(fileOut, deflater, 256 * 1024).use { defOut ->
                    defOut.write(raw)
                }
            }

            Files.move(
                tmpOut.toPath(),
                out.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )

            logger.lifecycle("Flip+deflate: ${rgba.name} -> ${out.name} (${rgba.length()} -> ${out.length()} bytes)")
        }
    }
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
    val publishTaskPath: String,
)

data class BumpSummary(
    val target: BumpTarget,
    val projectPath: String,
    val oldVersion: String,
    val newVersion: String,
    val updatedKeys: List<String>,
    val publishedModules: List<String>,
    val coordinates: List<String>,
)

val semVerRegex = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
val coreBumpConfig = BumpConfig(
    target = BumpTarget.CORE,
    projectPath = ":core",
    versionFile = rootProject.file("core/gradle.properties"),
    versionKey = "moduleVersion",
    publishTaskPath = ":core:publishToMavenLocal",
)
val mc1710BumpConfig = BumpConfig(
    target = BumpTarget.MC1710,
    projectPath = ":adapters:mc-forge-1-7-10",
    versionFile = rootProject.file("adapters/mc-forge-1-7-10/gradle.properties"),
    versionKey = "moduleVersion",
    syncedKeys = listOf("modVersion"),
    publishTaskPath = ":adapters:mc-forge-1-7-10:publishToMavenLocal",
)

fun parseVersion(version: String): Triple<Int, Int, Int> {
    val match = semVerRegex.matchEntire(version)
        ?: throw GradleException(
            "Version '$version' is not strict SemVer (MAJOR.MINOR.PATCH integers only).",
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
                "Ensure maven-publish and publishToMavenLocal are configured for ${config.projectPath}.",
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
            "Publishing is not configured for '${config.projectPath}'. Missing PublishingExtension.",
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
            "Publishing is not configured for '${config.projectPath}'. No MavenPublication found.",
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
                coordinates = coords,
            )

            logger.lifecycle("${config.target.name.lowercase()} version updated: $oldVersion -> $newVersion")
        }
    }

    tasks.register(taskName) {
        group = "release"
        description =
            "Bump ${config.target.name.lowercase()} ${part.name.lowercase()} version and publish to Maven Local."
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

tasks.register("runDemoClient") {
    group = "application"
    description = "Run Minecraft client with DSGL showcase demo module."
    dependsOn(":adapters:mc-forge-1-7-10:demo:runClient")
}

tasks.register("detektAll") {
    group = "verification"
    description = "Run detekt across all modules that have detekt applied"

    doLast {
        val violatingModules = project.subprojects
            .filter { it.plugins.hasPlugin("io.gitlab.arturbosch.detekt") }
            .filter { subproject ->
                val sarifFile = subproject.file("build/reports/detekt/detekt.sarif")
                sarifFile.exists() && sarifFile.readText().contains("\"ruleId\"")
            }
            .map { it.path }

        if (violatingModules.isNotEmpty()) {
            throw GradleException(
                "detekt found violations in: ${violatingModules.joinToString(", ")}. " +
                    "See each module's build/reports/detekt/ for details.",
            )
        }
    }
}

subprojects {
    plugins.withId("io.gitlab.arturbosch.detekt") {
        val detektTask = tasks.named("detekt")
        rootProject.tasks.named("detektAll").configure {
            dependsOn(detektTask)
        }
        gradle.taskGraph.whenReady {
            if (hasTask(":detektAll")) {
                detektTask.configure {
                    (this as? VerificationTask)?.ignoreFailures = true
                }
            }
        }
    }
}

tasks.register<Exec>("installGitHooks") {
    group = "setup"
    description = "Configure git to use hooks from .githooks/. Run once after cloning."
    commandLine("git", "config", "core.hooksPath", ".githooks")
    doLast {
        fileTree(".githooks").forEach { it.setExecutable(true) }
        logger.lifecycle("Git hooks installed. Hook path set to .githooks/")
    }
}
