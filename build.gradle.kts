plugins {
    kotlin("jvm") version "1.9.0"
    id("maven-publish")
    id("edu.wpi.first.GradleRIO") version "2025.3.2"
    id("com.github.breadmoirai.github-release") version "2.5.2"
}

project.group = "io.github.dkoontz"

project.version = "0.1.4"

repositories {
    mavenCentral()
    flatDir { dirs("libs") }

    maven { url = uri("https://maven.ctr-electronics.com/release/") }
    maven { url = uri("https://maven.revrobotics.com/") }
}

dependencies {
    implementation(kotlin("stdlib"))

    api(files("libs/teaforge-0.1.3.jar"))

    implementation("edu.wpi.first.wpilibj:wpilibj-java:2025.3.2")
    implementation("edu.wpi.first.wpiutil:wpiutil-java:2025.3.2")
    implementation("edu.wpi.first.hal:hal-java:2025.3.2")
    implementation("edu.wpi.first.wpimath:wpimath-java:2025.3.2")

    implementation("com.ctre.phoenix6:wpiapi-java:25.3.2")
    implementation("com.ctre.phoenix:api-java:5.35.0")
    implementation("com.ctre.phoenix:wpiapi-java:5.35.0")
    implementation("com.revrobotics.frc:REVLib-java:2025.0.3")

    implementation("io.ktor:ktor-client-core:2.3.7")
    implementation("io.ktor:ktor-client-websockets:2.3.7")
    implementation("io.ktor:ktor-client-cio:2.3.7")

    testImplementation(kotlin("test"))
}

kotlin { jvmToolchain(17) }

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
        vendor = JvmVendorSpec.ADOPTIUM
    }
}

tasks.test { useJUnitPlatform() }

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])

            // Ensure dependencies are included in the published POM
            pom {
                withXml {
                    val dependenciesNode = asNode().appendNode("dependencies")
                    configurations.api.get().dependencies.forEach { dep ->
                        val dependencyNode = dependenciesNode.appendNode("dependency")
                        dependencyNode.appendNode("groupId", dep.group)
                        dependencyNode.appendNode("artifactId", dep.name)
                        dependencyNode.appendNode("version", dep.version)
                        dependencyNode.appendNode("scope", "compile")
                    }
                }
            }
        }
    }
}

githubRelease {
    token(System.getenv("GITHUB_TOKEN") ?: "")
    owner.set("dkoontz")
    repo.set(project.name)
    tagName.set("${project.version}")
    releaseName.set("Release ${project.version}")
    targetCommitish.set("main")
    body.set("Automated release for version ${project.version}")
    releaseAssets.setFrom(file("build/libs/${project.name}-${project.version}.jar"))
    draft.set(false)
    prerelease.set(false)
}

tasks.named("githubRelease") { dependsOn("compileKotlin") }

tasks.named("publish") { dependsOn("compileKotlin") }
