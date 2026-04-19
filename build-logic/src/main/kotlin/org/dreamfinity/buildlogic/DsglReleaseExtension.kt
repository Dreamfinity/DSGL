package org.dreamfinity.buildlogic

import javax.inject.Inject
import org.gradle.api.file.ProjectLayout
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

abstract class DsglReleaseExtension @Inject constructor(
    objects: ObjectFactory,
    layout: ProjectLayout
) {
    val enabled: Property<Boolean> = objects.property(Boolean::class.java).convention(true)
    val versionFile: RegularFileProperty = objects.fileProperty().convention(
        layout.projectDirectory.file("gradle.properties")
    )
    val versionKey: Property<String> = objects.property(String::class.java).convention("moduleVersion")
    val buildVersionKey: Property<String> = objects.property(String::class.java).convention("buildVersion")
    val syncKeys: ListProperty<String> = objects.listProperty(String::class.java).convention(emptyList())
}
