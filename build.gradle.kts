plugins {
    kotlin("jvm") version "2.4.0"
    id("com.gradleup.shadow") version "9.4.2"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "net.malusic"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("26.2.build.+")
    implementation("com.google.code.gson:gson:2.13.2")
}

tasks {
    build {
        dependsOn(shadowJar)
    }

    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(25)
    }

    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }

    processResources {
        filteringCharset = Charsets.UTF_8.name()
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
    jvmToolchain(25)
}