pluginManagement {
    // allows storing plugin versions in gradle.properties
    fun prop(prop: String): String = providers.gradleProperty(prop).get()
    fun plugin(prop: String): String = prop("plugin.$prop")
    fun mtk(prop: String): String = plugin("mtk.$prop")

    plugins {
        kotlin("jvm") version plugin("kotlin")
        id("com.google.devtools.ksp") version plugin("ksp")
        id("dev.kikugie.fletching-table.fabric") version plugin("fletching-table")
        id("org.gradle.crypto.checksum") version "1.4.0" // hasn't updated in 4+ years
        id("dev.isxander.mtk.manifests") version mtk("manifests")
        id("me.modmuss50.mod-publish-plugin") version plugin("mod-publish-plugin")
        id("dev.isxander.modstitch.base") version plugin("modstitch")
        id("dev.kikugie.stonecutter") version plugin("stonecutter")
        id("net.fabricmc.fabric-loom") version prop("fabric.loom") apply false
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

        val loaders = providers.gradleProperty("loaders").get()
        val loadList = loaders.replace("neoforge", "neo").split(",")
        val targets = providers.gradleProperty("targets").get()
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