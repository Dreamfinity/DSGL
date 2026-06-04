fun isAdapterEnabled(name: String) = providers.gradleProperty("enable$name").orNull?.toBoolean() ?: false

pluginManagement {
    includeBuild("build-logic")

    fun isAdapterEnabled(name: String) = providers.gradleProperty("enable$name").orNull?.toBoolean() ?: false

    repositories {
        maven(url = "https://maven.minecraftforge.net")
        gradlePluginPortal()
        mavenCentral()
    }

    if (isAdapterEnabled("MinecraftForge1710")) {
        includeBuild("adapters/mc-forge-1-7-10/adapter-build-logic")
    }
    if (isAdapterEnabled("MinecraftNeoforge1211")) {
        includeBuild("adapters/mc-neoforge-1-21-1/adapter-build-logic")
    }
}

rootProject.name = "dsgl"

include(":core")

if (isAdapterEnabled("MinecraftForge1710")) {
    include(":adapters:mc-forge-1-7-10")
    include(":adapters:mc-forge-1-7-10:demo")
}
if (isAdapterEnabled("MinecraftNeoforge1211")) {
    include(":adapters:mc-neoforge-1-21-1")
    include(":adapters:mc-neoforge-1-21-1:demo")
}
