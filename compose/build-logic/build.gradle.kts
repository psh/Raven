plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

// The plugins' functional tests run small builds of their own with Gradle TestKit.
tasks.test { useJUnitPlatform() }
