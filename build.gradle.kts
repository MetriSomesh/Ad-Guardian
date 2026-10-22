// Root build file.
// Classpath-style plugin declaration (instead of the plugins DSL at root) so
// the exact same artifacts can be resolved from a plain Maven repository.
// Versions verified for this project: AGP 8.5.2, Kotlin 1.9.24,
// google-services 4.4.2, Gradle 8.7, JDK 17.
buildscript {
    repositories {
        // Local mirror used by the build machine that produced the first APKs
        // (its network cannot serve the Gradle daemon directly). It is only
        // consulted when the directory exists; on any normal machine the
        // google()/mavenCentral() repositories below are used.
        val localMirror = file("/home/hatch/workspace/.m2-offline")
        if (localMirror.isDirectory) {
            maven { url = localMirror.toURI() }
        }
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.5.2")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.24")
        classpath("com.google.gms:google-services:4.4.2")
    }
}

allprojects {
    repositories {
        val localMirror = file("/home/hatch/workspace/.m2-offline")
        if (localMirror.isDirectory) {
            maven { url = localMirror.toURI() }
        }
        google()
        mavenCentral()
    }
}
