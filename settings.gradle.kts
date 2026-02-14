pluginManagement {
    repositories {
        maven(url = "https://maven.minecraftforge.net")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "forge") {
                useModule("com.anatawa12.forge:ForgeGradle:1.2-1.1.+")
            }
        }
    }
}

rootProject.name = "dsgl"

include(
    ":core",
    ":mc1710",
)
