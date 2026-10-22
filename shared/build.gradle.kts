// Pure Kotlin/JVM module: no Android dependencies, so every rule in here is
// unit-testable on a desktop JVM. Both Android apps depend on this module.
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // Required on the test runtime classpath since JUnit 5.10 / Gradle 8:
    // without it the test worker starts with no launcher and every suite dies.
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
