plugins {
    id("dsgl-mc-adapter.conventions")
    id("dsgl-mc-neoforge-1-21-1.conventions")
    id("dsgl-releaseable-module.conventions")
}

dsglRelease {
    syncKeys.add("modVersion")
}

dependencies {
    val coreProject = findProject(":core")
        ?: findProject(":dsgl:core")
        ?: error("DSGL core project not found (expected :core or :dsgl:core).")
    implementation(coreProject)
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}
