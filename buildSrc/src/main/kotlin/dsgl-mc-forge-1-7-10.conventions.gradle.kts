import org.gradle.kotlin.dsl.withGroovyBuilder

repositories {
    gradlePluginPortal()
    mavenCentral()

    exclusiveContent {
        forRepository {
            maven(url = "https://maven.minecraftforge.net/") {
                name = "forge"
                metadataSources {
                    mavenPom()
                    artifact()
                }
            }
        }
        filter {
            includeGroupByRegex("net.minecraftforge.*")
            includeGroupByRegex("de.oceanlabs.*")
        }
    }
    maven(url = "https://cloudrep.veritaris.me/repos/")
    maven(url = "https://maven.minecraftforge.net")
}

plugins {
    id("dsgl-mc-adapter.conventions")
    id("forge")
}

val gameVersion: String by project
val forgeVersion: String by project

extensions.getByName("minecraft").withGroovyBuilder {
    setProperty("version", "$gameVersion-$forgeVersion-$gameVersion")
    setProperty("mappings", "stable_12")
    setProperty("runDir", "run")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
