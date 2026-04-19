plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.minecraftforge.net")
    maven("https://cloudrep.veritaris.me/repos/")
}

dependencies {
    implementation("com.anatawa12.forge:ForgeGradle:1.2-1.1.+")
}
