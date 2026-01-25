plugins {
    `java-library`
}

group = property("group") as String
version = property("version") as String

repositories {
    maven(url = "https://cloudrep.veritaris.me/repos/")
    mavenCentral()
}

dependencies {
    api(":mc1710")
}
