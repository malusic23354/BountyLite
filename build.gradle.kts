import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("com.modrinth.minotaur") version "2.10.0"
}

group = "net.malusic"
version = file("src/main/resources/paper-plugin.yml")
    .readLines()
    .first { it.startsWith("version:") }
    .substringAfter(":")
    .trim()
    .trim('"', '\'')

modrinth {
    token.set(providers.gradleProperty("modrinthToken"))
    projectId.set("7k37mlVm")
    versionNumber.set(project.version.toString())
    versionType.set("release")
    uploadFile.set(tasks.shadowJar)
    gameVersions.addAll(listOf("1.21.11", "26.1", "26.2", "26.3"))
    loaders.add("paper")
    loaders.add("purpur")
    dependencies {
        required.project("ayRaM8J7")
        optional.project("lKEzGugV")
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.extendedclip.com/releases/")
    maven("https://jitpack.io")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.12.3")

    implementation("org.xerial:sqlite-jdbc:3.49.1.0") {
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
    implementation("com.mysql:mysql-connector-j:26.7.0") {
        exclude(group = "com.google.protobuf", module = "protobuf-java")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    jar {
        archiveClassifier.set("plain")
    }

    shadowJar {
        archiveClassifier.set("")
        mergeServiceFiles()
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(21)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    val copyPlugin = register<Copy>("copyPlugin") {
        from(shadowJar.flatMap { it.archiveFile })
        into(layout.projectDirectory.dir("run/plugins"))
    }

    register<Exec>("runServer") {
        dependsOn(copyPlugin)
        workingDir = layout.projectDirectory.dir("run").asFile
        commandLine("java", "-Xmx4G", "-jar", "paper.jar", "--nogui")
        standardInput = System.`in`
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}