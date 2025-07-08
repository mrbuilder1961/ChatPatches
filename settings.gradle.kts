pluginManagement {
    // allow storing plugin versions in gradle.properties
    val kotlin: String by settings
    val stonecutter: String by settings
    val modstitch: String by settings
    val mpp: String by settings

    plugins {
        kotlin("jvm") version kotlin
        id("dev.kikugie.stonecutter") version stonecutter
        id("dev.isxander.modstitch.base") version modstitch
        id("me.modmuss50.mod-publish-plugin") version mpp
    }

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.kikugie.dev/snapshots")
        maven("https://maven.parchmentmc.org")
        maven("https://maven.isxander.dev/releases/")
    }
}

plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        // forge/neoforge modstitch compat key:
        //           /-----------|------|.... // neoforge
        // ----|----/                         // forge
        // 1.20   1.20.1      1.20.2  1.20.3

        val loaders: String by settings
        val loadList = loaders.replace("neoforge", "neo").split(",")
        val targets: String by settings
        targets.split(",").forEach {
            v: String ->
            loadList.forEach {
                l: String ->
                vers("$v-$l", v) // creates a version named "version-loader"
            }
        }

        val vcs: String by settings
        vcsVersion = vcs // when not explicitly specified defaults to first version
    }
}

rootProject.name = "Chat Patches" // mod.name