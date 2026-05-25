pluginManagement {
    // allows storing plugin versions in gradle.properties
    val kotlin: String by settings
    val ksp: String by settings
    val stonecutter: String by settings
    val fletching: String by settings
    val modstitch: String by settings
    val loom: String by settings
    val mpp: String by settings

    plugins {
        kotlin("jvm") version kotlin
        id("com.google.devtools.ksp") version ksp
        id("dev.isxander.modstitch.base") version modstitch
        id("dev.kikugie.stonecutter") version stonecutter
        id("dev.kikugie.fletching-table.fabric") version fletching
        id("net.fabricmc.fabric-loom") version loom apply false
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
        //       modstitch compat key:
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
                version("$v-$l", v)
            }
        }

        vcsVersion = "${targets.substringAfterLast(',')}-fabric" // most recent version on fabric
    }
}

rootProject.name = "Chat Patches"