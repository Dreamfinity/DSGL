plugins {
    id("dsgl-core.conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
    jacoco
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}

val minLineCoverageRatio = providers
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
    eachFile {
        if (!path.startsWith("fonts/")) {
            return@eachFile
        }
        val allowedFontArtifact =
            path.endsWith(".ttf") ||
            path.endsWith(".json") ||
            path.endsWith(".rgba.deflate")
        if (!allowedFontArtifact) {
            exclude()
        }
    }
}
