import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    application
    java
}

group = "com.bluesmods.bluecordpatcher"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.beust:jcommander:1.82")
    implementation("ch.qos.logback:logback-classic:1.5.13")
    implementation("commons-io:commons-io:2.15.1")
    implementation("org.ini4j:ini4j:0.5.4")

    testImplementation(kotlin("test"))
}

tasks.jar {
    archiveFileName.set("BluecordPatcher.jar")

    manifest.attributes["Main-Class"] = "com.bluesmods.bluecordpatcher.Main"
    dependsOn(configurations.runtimeClasspath)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    doFirst {
        from(configurations.runtimeClasspath.get().map(::zipTree))
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "17"
    }
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.bluesmods.bluecordpatcher.Main")
}