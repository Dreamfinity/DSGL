import java.io.BufferedOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

plugins {
    `java-library`
}


val fontsRootDir: File = rootProject.file("fonts")
val msdfGeneratorBinary: File = rootProject.file("msdf-atlas-gen/build/bin/msdf-atlas-gen")
val generatedPrecompiledFontsDir: File = project.file("precompiled_fonts")

tasks.register("generateMsdfAtlases") {
    group = "assets"
    description = "Generate MTSDF atlases and metadata for fonts/**/*.ttf using msdf-atlas-gen/build/bin/msdf-atlas-gen"

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
        if (!msdfGeneratorBinary.exists()) {
            throw GradleException("MSDF generator not found: ${msdfGeneratorBinary.path}")
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
            val pxrange = 4
            val size = 32
            val commonArgs = listOf(
                msdfGeneratorBinary.absolutePath,
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
                "-json", jsonOutArg
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
                            "Expected outputs: '$pngAtlasOutArg', '$jsonOutArg'"
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
                            "Expected outputs: '$pngAtlasOutArg', '$jsonOutArg'"
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
                StandardCopyOption.ATOMIC_MOVE
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

tasks.register("runDemoClient") {
    group = "application"
    description = "Run Minecraft client with DSGL showcase demo module."
    dependsOn(":mc1710-demo:runClient")
}
