import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

val dsglRelease = extensions.create<DsglReleaseExtension>("dsglRelease")

tasks.register<PrintReleaseVersionTask>("printReleaseVersion") {
    group = "release"
    description = "Print the effective release version for this module."
    releaseEnabled.convention(dsglRelease.enabled)
    versionFile.convention(dsglRelease.versionFile)
    versionKey.convention(dsglRelease.versionKey)
    buildVersionKey.convention(dsglRelease.buildVersionKey)
    syncKeys.convention(dsglRelease.syncKeys)
}

tasks.register<BumpModuleVersionTask>("bumpMajor") {
    group = "release"
    description = "Task for bumping the module major version."
    part.convention(DsglReleaseVersionPart.MAJOR)
    releaseEnabled.convention(dsglRelease.enabled)
    versionFile.convention(dsglRelease.versionFile)
    versionKey.convention(dsglRelease.versionKey)
    buildVersionKey.convention(dsglRelease.buildVersionKey)
    syncKeys.convention(dsglRelease.syncKeys)
}

tasks.register<BumpModuleVersionTask>("bumpMinor") {
    group = "release"
    description = "Task for bumping the module minor version."
    part.convention(DsglReleaseVersionPart.MINOR)
    releaseEnabled.convention(dsglRelease.enabled)
    versionFile.convention(dsglRelease.versionFile)
    versionKey.convention(dsglRelease.versionKey)
    buildVersionKey.convention(dsglRelease.buildVersionKey)
    syncKeys.convention(dsglRelease.syncKeys)
}

tasks.register<BumpModuleVersionTask>("bumpPatch") {
    group = "release"
    description = "Task for bumping the module patch version."
    part.convention(DsglReleaseVersionPart.PATCH)
    releaseEnabled.convention(dsglRelease.enabled)
    versionFile.convention(dsglRelease.versionFile)
    versionKey.convention(dsglRelease.versionKey)
    buildVersionKey.convention(dsglRelease.buildVersionKey)
    syncKeys.convention(dsglRelease.syncKeys)
}
