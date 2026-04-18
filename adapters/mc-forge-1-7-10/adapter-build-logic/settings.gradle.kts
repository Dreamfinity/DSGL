import org.gradle.kotlin.dsl.maven

pluginManagement {
    includeBuild("../../../build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.minecraftforge.net")
    }
}
