plugins {
    kotlin("jvm") version "1.9.0"
    id("maven-publish")
    id("edu.wpi.first.GradleRIO") version "2025.3.2"
}

group = "io.github.teaforge-roborio"

version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation(kotlin("stdlib"))
    
    implementation("edu.wpi.first.wpilibj:wpilibj-java:2025.3.2")
    implementation("edu.wpi.first.hal:hal-java:2025.3.2")
    implementation("edu.wpi.first.wpiutil:wpiutil-java:2025.3.2")

    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(11) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

sourceSets {
    main {
        kotlin {
            srcDirs("src/main/kotlin", "checkouts/teaforge/src/main/kotlin")
        }
    }
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "io.github.teaforge-roborio"
            artifactId = "teaforge-roborio"
            version = "0.1.0"

            from(components["java"])
        }
    }
}