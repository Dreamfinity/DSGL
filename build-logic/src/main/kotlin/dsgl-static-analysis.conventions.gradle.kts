import org.gradle.api.JavaVersion

plugins {
    id("io.gitlab.arturbosch.detekt")
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    baseline = rootProject.file("config/detekt/baseline-${project.name}.xml")
    parallel = true
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "1.8"
    reports {
        html.required.set(true)
        sarif.required.set(true)
        xml.required.set(false)
        txt.required.set(false)
    }
    exclude("**/generated/**", "**/build/**")
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "1.8"
}
