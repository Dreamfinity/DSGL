import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

repositories {
    maven(url = "https://cloudrep.veritaris.me/repos/")
    mavenCentral()
}

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
    id("org.jetbrains.dokka")
}

group = property("group") as String
version = property("version") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
}

tasks.withType<Jar> {
    archiveBaseName.set("dsgl-${project.name}")
}

val dokkaHtml = tasks.named("dokkaGeneratePublicationHtml")
val dokkaJavadocJar = tasks.register<Jar>("dokkaJavadocJar") {
    dependsOn(dokkaHtml)
    from(dokkaHtml.map { it.outputs.files })
    archiveClassifier.set("javadoc")
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = property("group") as String
            artifactId = "dsgl-${project.name}"
            version = property("version") as String
            artifact(dokkaJavadocJar)
        }
    }
    repositories {
        mavenLocal()
    }
}
