import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.the

plugins {
    id("dsgl-jvm-publish.conventions")
}

val sourceSets = the<SourceSetContainer>()

val devJar = tasks.register<Jar>("devJar") {
    from(sourceSets["main"].output)
    archiveClassifier.set("dev")
}

val devSourcesJar = tasks.register<Jar>("devSourcesJar") {
    from(sourceSets["main"].allSource)
    archiveClassifier.set("dev-sources")
}

publishing {
    publications {
        withType(MavenPublication::class.java).configureEach {
            if (name == "mavenJava") {
                artifact(devJar)
                artifact(devSourcesJar)
            }
        }
    }
}
