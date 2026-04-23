plugins {
    id("dsgl-core.conventions")
    id("dsgl-linter.conventions")
    id("dsgl-releaseable-module.conventions")
    id("org.jetbrains.kotlin.plugin.serialization")
    jacoco
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}

val minLineCoverageRatio =
    providers
        .gradleProperty("dsgl.jacoco.min.line")
        .orElse("0.35")

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = BigDecimal(minLineCoverageRatio.get())
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}

tasks.processResources {
    val allowedCoreFontBases =
        setOf(
            "fonts/minecraft/MinecraftDefault-Regular",
            "fonts/ubuntu/Ubuntu-Regular",
            "fonts/noto/Noto_Sans/NotoSans-Regular",
            "fonts/jetbrains_mono/JetBrainsMono-Regular",
            "fonts/telegrafico/telegrafico",
        )

    eachFile {
        if (isDirectory || !path.startsWith("fonts/")) {
            return@eachFile
        }
        val isAllowedFontArtifact =
            path.endsWith(".ttf") ||
                path.endsWith(".json") ||
                path.endsWith(".rgba.deflate")
        if (!isAllowedFontArtifact) {
            exclude()
            return@eachFile
        }
        val isAllowedCoreFontArtifact =
            allowedCoreFontBases.any { base ->
                path == "$base.ttf" ||
                    path == "$base-meta.json" ||
                    path == "$base-mtsdf.rgba.deflate"
            }
        if (!isAllowedCoreFontArtifact) {
            exclude()
        }
    }
}
