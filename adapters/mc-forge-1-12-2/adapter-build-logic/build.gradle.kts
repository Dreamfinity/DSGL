plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven {
        // RetroFuturaGradle
        name = "GTNH Maven"
        setUrl("https://nexus.gtnewhorizons.com/repository/public/")
        mavenContent {
            includeGroupByRegex("com\\.gtnewhorizons\\..+")
            includeGroup("com.gtnewhorizons")
        }
    }
}

dependencies {
    implementation("com.gtnewhorizons:retrofuturagradle:1.4.9")
}
