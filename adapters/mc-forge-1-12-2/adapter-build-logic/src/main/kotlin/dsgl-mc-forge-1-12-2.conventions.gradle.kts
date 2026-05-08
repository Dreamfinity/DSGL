plugins {
    id("com.gtnewhorizons.retrofuturagradle")
}

val gameVersion: String by project
val forgeVersion: String by project

repositories {
    gradlePluginPortal()
    mavenCentral()
}

minecraft {
    mcVersion.set("1.12.2")
    username.set("Developer")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

