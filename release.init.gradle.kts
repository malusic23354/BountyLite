import com.modrinth.minotaur.ModrinthExtension
import java.util.Properties

initscript {
    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.modrinth.minotaur:Minotaur:2.10.0")
    }
}

gradle.rootProject {
    afterEvaluate {
        plugins.apply("com.modrinth.minotaur")

        val localTokenFile = file(".gradle/gradle.properties")
        val localToken = if (localTokenFile.exists()) {
            Properties()
                .apply { localTokenFile.inputStream().use { load(it) } }
                .getProperty("modrinthToken")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val changelogFile = file("changelog.md")
        val changelogText = if (changelogFile.exists()) {
            val lines = changelogFile.readLines()
            val start = lines.indexOfFirst { it.trim() == "## ${version}" }
            if (start < 0) {
                "No changelog entry found for ${version}."
            } else {
                val next = lines.drop(start + 1).indexOfFirst { it.startsWith("## ") }
                val end = if (next < 0) lines.size else start + 1 + next
                lines.subList(start + 1, end).joinToString("\n").trim()
            }
        } else {
            "No changelog was specified."
        }

        extensions.configure<ModrinthExtension> {
            token.set(localToken)
            projectId.set("7k37mlVm")
            versionNumber.set(version.toString())
            versionType.set("release")
            changelog.set(changelogText)
            uploadFile.set(tasks.named("shadowJar"))
            gameVersions.addAll(listOf("1.21.11", "26.1", "26.2", "26.3"))
            loaders.add("paper")
            loaders.add("purpur")
            dependencies {
                required.project("ayRaM8J7")
                optional.project("lKEzGugV")
            }
        }
    }
}