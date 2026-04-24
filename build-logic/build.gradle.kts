plugins {
    `kotlin-dsl`
}

val kotlinVersion: String = rootProject.projectDir.resolveSibling("gradle.properties")
    .readLines()
    .firstOrNull { it.startsWith("kotlinVersion=") }
    ?.substringAfter("=")
    ?.trim()
    ?: error("kotlinVersion not found in root gradle.properties")

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    implementation("org.jetbrains.dokka:org.jetbrains.dokka.gradle.plugin:2.1.0")
    implementation(libs.plugins.ktlint.toDep())
    implementation(libs.plugins.detekt.toDep())
}

fun Provider<PluginDependency>.toDep() =
    map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
