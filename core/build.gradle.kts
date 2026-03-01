plugins {
    id("dsgl-core.conventions")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.10"
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}
