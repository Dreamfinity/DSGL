plugins {
    id("dsgl-mc1710.conventions")
}

dependencies {
    val coreProject = findProject(":core")
        ?: findProject(":dsgl:core")
        ?: error("DSGL core project not found (expected :core or :dsgl:core).")
    api(coreProject)
}
