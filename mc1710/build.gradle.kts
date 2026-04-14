plugins {
    id("dsgl-mc1710.conventions")
    id("dsgl-releaseable-module.conventions")
}

dsglRelease {
    syncKeys.add("modVersion")
}

dependencies {
    val coreProject = findProject(":core")
        ?: findProject(":dsgl:core")
        ?: error("DSGL core project not found (expected :core or :dsgl:core).")
    api(coreProject)
    testImplementation(kotlin("test-junit"))
    testImplementation(kotlin("test"))
}
