repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://cloudrep.veritaris.me/repos/")
    maven("https://maven.minecraftforge.net")
}

plugins {
    `kotlin-dsl`
    `maven-publish`
}

dependencies {
    implementation("com.anatawa12.forge:ForgeGradle:1.2-1.1.+")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.20")
}
