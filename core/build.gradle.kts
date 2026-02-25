plugins {
    id("dsgl-core.conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
}

val fontsRootDir: File = rootProject.file("fonts")
val msdfGeneratorExe: File = rootProject.file("bin/msdf-atlas-gen.exe")
val generatedFontsResourcesDir: File = project.file("src/main/resources/fonts")

tasks.register("generateMsdfAtlases") {
    group = "assets"
    description = "Generate MTSDF atlases and metadata for fonts/**/*.ttf using bin/msdf-atlas-gen.exe."

    val ttfTree = fileTree(fontsRootDir) {
        include("**/*.ttf")
    }

    inputs.files(ttfTree)
    val registryFile = File(generatedFontsResourcesDir, "generated-fonts.txt")
    outputs.file(registryFile)
    ttfTree.files.forEach { ttf ->
        val relative = ttf.relativeTo(fontsRootDir).invariantSeparatorsPath
        val base = relative.removeSuffix(".ttf")
        outputs.file(File(generatedFontsResourcesDir, "$base-mtsdf.png"))
        outputs.file(File(generatedFontsResourcesDir, "$base-meta.json"))
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
            val outputPng = File(generatedFontsResourcesDir, "$base-mtsdf.png")
            val outputJson = File(generatedFontsResourcesDir, "$base-meta.json")
            outputPng.parentFile?.mkdirs()
            outputJson.parentFile?.mkdirs()

            val atlasOutArg = "core/src/main/resources/fonts/${base}-mtsdf.png"
            val jsonOutArg = "core/src/main/resources/fonts/${base}-meta.json"
            val fontArg = "./fonts/$relative"
            val charsetFile = "./fonts/charset.txt"

            val result = exec {
                workingDir = rootProject.projectDir
                commandLine(
                    msdfGeneratorExe.absolutePath,
                    "-font", fontArg,
                    "-charset", charsetFile,
                    "-type", "mtsdf",
                    "-pxrange", "8",
                    "-format", "png",
                    "-imageout", atlasOutArg,
                    "-json", jsonOutArg
                )
                isIgnoreExitValue = true
            }
            if (result.exitValue != 0) {
                throw GradleException(
                    "msdf-atlas-gen failed for '$fontArg' with exit code ${result.exitValue}. " +
                            "Expected outputs: '$atlasOutArg', '$jsonOutArg'"
                )
            }
        }

        val registryLines = fonts.map { it.relativeTo(fontsRootDir).invariantSeparatorsPath }
        registryFile.parentFile?.mkdirs()
        registryFile.writeText(registryLines.joinToString(System.lineSeparator()))
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}
